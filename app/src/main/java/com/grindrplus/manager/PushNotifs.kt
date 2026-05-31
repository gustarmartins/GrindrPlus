package com.grindrplus.manager

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.NotificationCompat
import com.google.gson.JsonParser
import com.grindrplus.R
import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class GPlusMessage(
    val id: String,
    val content: String,
    val timestamp: Long
)

const val CHANNEL_PING_URL = "https://github.com/gustarmartins/GrindrPlus/raw/refs/heads/master/news.json"
const val SPOOF_PING_URL = "https://github.com/gustarmartins/GrindrPlus/raw/refs/heads/master/version_spoof.json"
val tgMessages = MutableStateFlow<List<GPlusMessage>>(listOf())
private val fetchMutex = Mutex()
private val spoofFetchMutex = Mutex()

fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

suspend fun fetchNotifs(context: Context) {
    if (fetchMutex.isLocked) return
    
    fetchMutex.withLock {
        withContext(Dispatchers.IO) {
            // avoids spam
            if (!isNetworkAvailable(context)) return@withContext

            val client = OkHttpClient.Builder()
                .callTimeout(30.seconds.toJavaDuration()).build()

            val request = okhttp3.Request.Builder()
                .url(CHANNEL_PING_URL)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                )
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    tgMessages.value =
                        JsonParser.parseString(response.body!!.string()).asJsonArray
                            .map { it.asJsonObject }
                            .map { obj ->
                                GPlusMessage(
                                    obj.get("message_id").asString,
                                    obj.get("text").asString,
                                    obj.get("date").asLong
                                )
                            }
                            .filterNot { it.content.isBlank() }
                            .sortedBy { it.id }.toList()
                    
                    val msg = tgMessages.value.lastOrNull() ?: return@use

                    if (!Config.isConfigLoaded) {
                        Logger.w("fetchNotifs: config not loaded, skipping notification to avoid spam")
                        return@use
                    }

                    Config.put("last_news_fetch_ms", System.currentTimeMillis())

                    if (Config.get("last_push_id", "").toString() != msg.id) {
                        Config.put("last_push_id", msg.id)
                        if (msg.content.contains("#push"))
                            sendNotification(context, msg.content.replace("#push", "").trim())
                        else sendNotification(context)
                    }
                }
            } catch (e: Exception) {
                Logger.e("fetchNotifs: Failed to fetch notifications: ${e.message}")
            }
        }
    }
}

suspend fun fetchRemoteSpoof(context: Context, force: Boolean = false) {
    if (spoofFetchMutex.isLocked) return

    spoofFetchMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!isNetworkAvailable(context)) return@withContext
            if (!Config.isConfigLoaded) return@withContext
            if (!force && !(Config.get("auto_update_spoof", true) as Boolean)) return@withContext

            val client = OkHttpClient.Builder()
                .callTimeout(30.seconds.toJavaDuration()).build()

            val request = okhttp3.Request.Builder()
                .url(SPOOF_PING_URL)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                )
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    val obj = JsonParser.parseString(response.body!!.string()).asJsonObject
                    val name = obj.get("spoofed_version_name")?.asString?.trim()
                    val code = obj.get("spoofed_version_code")?.asString?.trim()

                    Config.put("last_spoof_fetch_ms", System.currentTimeMillis())

                    if (!name.isNullOrEmpty() && !code.isNullOrEmpty() && code.toIntOrNull() != null) {
                        Config.put("spoofed_version_name", name)
                        Config.put("spoofed_version_code", code)
                        Logger.i("Applied remote spoofed version: $name ($code)")
                    } else {
                        Logger.w("fetchRemoteSpoof: remote payload missing/invalid name or code")
                    }
                }
            } catch (e: Exception) {
                Logger.e("fetchRemoteSpoof: Failed to fetch spoofed version: ${e.message}")
            }
        }
    }
}

fun sendNotification(
    context: Context,
    msg: String = "New message from GrindrPlus! Open News tab to read."
) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel = android.app.NotificationChannel(
        "update_gplus",
        "GPlus Updates",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Notifications for GPlus communications"
    }

    nm.createNotificationChannel(channel)

    NotificationCompat.Builder(context, "update_gplus").apply {
        setSmallIcon(R.drawable.ic_launcher_foreground)
        setContentTitle("GrindrPlus News")
        setContentText(msg)
        setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        setAutoCancel(true)
        setPriority(NotificationCompat.PRIORITY_MAX)
    }.also { nm.notify(1, it.build()) }
}
