package com.grindrplus.core.http

import android.content.ContentValues
import android.widget.Toast
import com.grindrplus.BuildConfig
import com.grindrplus.GrindrPlus
import com.grindrplus.GrindrPlus.showToast
import com.grindrplus.core.DatabaseHelper
import com.grindrplus.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

import com.grindrplus.hooks.unsafeTrustManager
import com.grindrplus.hooks.unsafeSslContext

class Client(interceptor: Interceptor) {
    // although we have SSLUnpinning which hooks the OkHttpClient.Builder(),
    // the hook is set up later than this constructor is called,
    // so we need to set the "unpinning" manually
    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .apply {
                if (BuildConfig.DEBUG)
                    this.sslSocketFactory(unsafeSslContext.socketFactory, unsafeTrustManager)
            }
            .build()


    fun sendRequest(
        url: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        body: RequestBody? = null
    ): Response {
        val requestBuilder = Request.Builder().url(url)
        headers?.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        when (method.uppercase()) {
            "POST" -> requestBuilder.post(body ?: RequestBody.createEmpty())
            "PUT" -> requestBuilder.put(body ?: throw IllegalArgumentException("PUT requires a body"))
            "DELETE" -> {
                if (body != null) requestBuilder.delete(body) else requestBuilder.delete()
            }
            "PATCH" -> requestBuilder.patch(body ?: throw IllegalArgumentException("PATCH requires a body"))
            "GET" -> requestBuilder.get()
            else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
        }

        return httpClient.newCall(requestBuilder.build()).execute()
    }

    fun blockUser(profileId: String, silent: Boolean = false, reflectInDb: Boolean = true) {
        GrindrPlus.shouldTriggerAntiblock = false
        GrindrPlus.executeAsync {
            val response = sendRequest(
                "https://grindr.mobi/v3/me/blocks/$profileId",
                "POST"
            )
            if (response.isSuccessful) {
                response.close()
                if (!silent) showToast(Toast.LENGTH_LONG, "User blocked successfully")
                if (reflectInDb) {
                    val order = DatabaseHelper.query(
                        "SELECT MAX(order_) AS order_ FROM blocks"
                    ).firstOrNull()?.get("order_") as? Int ?: 0
                    DatabaseHelper.insert(
                        "blocks",
                        ContentValues().apply {
                            put("profileId", profileId)
                            put("order_", order + 1)
                        }
                    )
                }
            } else {
                if (!silent) {
                    response.useBody { errorBody ->
                        showToast(Toast.LENGTH_LONG, "Failed to block user: $errorBody")
                    }
                }
            }
        }
        GrindrPlus.executeAsync {
            Thread.sleep(500) // Wait for WS to reply
            GrindrPlus.shouldTriggerAntiblock = true
        }
    }

    fun unblockUser(profileId: String, silent: Boolean = false, reflectInDb: Boolean = true) {
        GrindrPlus.shouldTriggerAntiblock = false
        GrindrPlus.executeAsync {
            val response = sendRequest(
                "https://grindr.mobi/v3/me/blocks/$profileId",
                "DELETE"
            )
            if (response.isSuccessful) {
                response.close()
                if (!silent) showToast(Toast.LENGTH_LONG, "User unblocked successfully")
                try {
                    if (reflectInDb) {
                        DatabaseHelper.delete(
                            "blocks",
                            "profileId = ?",
                            arrayOf(profileId)
                        )
                    }
                } catch (e: Exception) {
                    Logger.apply {
                        e("Error removing user from blocks list: ${e.message}")
                        writeRaw(e.stackTraceToString())
                    }
                }
            } else {
                if (!silent) {
                    response.useBody { errorBody ->
                        showToast(Toast.LENGTH_LONG, "Failed to unblock user: $errorBody")
                    }
                }
            }
        }
        GrindrPlus.executeAsync {
            Thread.sleep(500) // Wait for WS to reply
            GrindrPlus.shouldTriggerAntiblock = true
        }
    }

    fun favorite(
        profileId: String,
        silent: Boolean = false,
        reflectInDb: Boolean = true
    ) {
        GrindrPlus.executeAsync {
            val response = sendRequest(
                "https://grindr.mobi/v3/me/favorites/$profileId",
                "POST"
            )
            if (response.isSuccessful) {
                response.close()
                if (!silent) showToast(Toast.LENGTH_LONG, "User favorited successfully")
                if (reflectInDb) {
                    DatabaseHelper.insert(
                        "favorite_profile",
                        ContentValues().apply {
                            put("id", profileId)
                        }
                    )
                }
            } else {
                if (!silent) {
                    response.useBody { errorBody ->
                        showToast(Toast.LENGTH_LONG, "Failed to favorite user: $errorBody")
                    }
                }
            }
        }
    }

    fun unfavorite(
        profileId: String,
        silent: Boolean = false,
        reflectInDb: Boolean = true
    ) {
        GrindrPlus.executeAsync {
            val response = sendRequest(
                "https://grindr.mobi/v3/me/favorites/$profileId",
                "DELETE"
            )
            if (response.isSuccessful) {
                response.close()
                if (!silent) showToast(Toast.LENGTH_LONG, "User unfavorited successfully")
                try {
                    if (reflectInDb) {
                        DatabaseHelper.delete(
                            "favorite_profile",
                            "id = ?",
                            arrayOf(profileId)
                        )
                    }
                } catch (e: Exception) {
                    Logger.apply {
                        e("Error removing user from favorites list: ${e.message}")
                        writeRaw(e.stackTraceToString())
                    }
                }
            } else {
                if (!silent) {
                    response.useBody { errorBody ->
                        showToast(Toast.LENGTH_LONG, "Failed to unfavorite user: $errorBody")
                    }
                }
            }
        }
    }

    fun updateLocation(geohash: String): Boolean {
        val body = """
            {
                "geohash": "$geohash"
            }
        """.trimIndent()

        val response = sendRequest(
            "https://grindr.mobi/v4/location",
            "PUT",
            body = body.toRequestBody(),
            headers = mapOf("Content-Type" to "application/json; charset=UTF-8")
        )
        if (response.isSuccessful) {
            response.close()
            return true
        } else {
            response.useBody { errorBody ->
                Logger.e("Failed to update location: $errorBody")
            }
            return false
        }
    }

    /**
     * Work-around to force Grindr's internal session refresh.
     * Grindr only fires POST /v8/sessions when the server-side incognito
     * state *actually changes*. The GET endpoint is unreliable (always
     * returns false), so we can't know the real state.
     *
     * Solution: always PUT false first (to ensure the server is in a known
     * state), wait for the websocket reconnect cycle to complete (~2s),
     * then PUT true. This guarantees a real false→true state transition,
     * which triggers POST /v8/sessions with the current geohash and
     * registers the user in the cascade grid at the new location.
     *
     * The OFF toggle may invalidate the current auth token (if it causes
     * a real state change). The ON PUT will retry with backoff to wait
     * for Grindr's internal token refresh to complete.
     */
    fun refreshSessionViaIncognito(): Boolean {
        try {
            val settingsUrl = "https://grindr.mobi/v3/me/prefs/settings"
            val jsonHeaders = mapOf("Content-Type" to "application/json; charset=UTF-8")

            // Step 1: Force incognito to false (reset to known state).
            // If it was already false this is a no-op server-side;
            // if it was true, this triggers a websocket reconnect which
            // invalidates the current auth token.
            val offBody = """{"settings":{"incognito":false}}""".toRequestBody()
            val offResponse = sendRequest(settingsUrl, "PUT", body = offBody, headers = jsonHeaders)
            if (!offResponse.isSuccessful) {
                offResponse.useBody { errorBody ->
                    Logger.w("Incognito reset to false failed (${offResponse.code}): $errorBody")
                }
                // Non-fatal: the state change may have still occurred server-side
                // before the 401 was returned. Continue to step 2.
            } else {
                offResponse.close()
            }

            // Wait for websocket reconnect cycle to complete.
            // Analysis shows the cycle takes ~1-2s, and a 500ms gap caused
            // the ON PUT to arrive with a stale token → 401 → no state change.
            Thread.sleep(2500)

            // Step 2: Set incognito to true. Server should now be in the
            // false state, so this is a real state change → Grindr fires
            // POST /v8/sessions. Retry on 401 (token may still be refreshing).
            val onBody = """{"settings":{"incognito":true}}""".toRequestBody()
            var onResponse = sendRequest(settingsUrl, "PUT", body = onBody, headers = jsonHeaders)
            var retryCount = 0
            while (onResponse.code == 401 && retryCount < 3) {
                onResponse.close()
                Logger.d("Incognito ON got 401, retrying (${retryCount + 1}/3)...")
                Thread.sleep(1500)
                onResponse = sendRequest(settingsUrl, "PUT", body = onBody, headers = jsonHeaders)
                retryCount++
            }

            if (!onResponse.isSuccessful) {
                onResponse.useBody { errorBody ->
                    Logger.w("Incognito set to true failed (${onResponse.code}): $errorBody")
                }
                // Even if this failed, the OFF toggle may have already
                // triggered a session refresh. Return true to avoid
                // unnecessary error toasts.
                return retryCount > 0
            } else {
                onResponse.close()
            }

            return true
        } catch (e: Exception) {
            Logger.w("Session refresh via incognito failed: ${e.message}")
            return false
        }
    }

    fun reportUser(
        profileId: String,
        reason: String = "SPAM",
        comment: String = ""
    ) {
        GrindrPlus.executeAsync {
            val body = """
                {
                    "reason": "$reason",
                    "comment": "$comment",
                    "locations": [
                        "CHAT_MESSAGE"
                    ]
                }
            """.trimIndent()

            val response = sendRequest(
                "https://grindr.mobi/v3.1/flags/$profileId",
                "POST",
                body = body.toRequestBody(),
                headers = mapOf("Content-Type" to "application/json; charset=UTF-8")
            )

            if (response.isSuccessful) {
                response.close()
                showToast(Toast.LENGTH_LONG, "User reported successfully")
            } else {
                response.useBody { errorBody ->
                    showToast(Toast.LENGTH_LONG, "Failed to report user: $errorBody")
                }
            }
        }
    }

    suspend fun fetchCascade(
        nearbyGeoHash: String,
        onlineOnly: Boolean = false,
        photoOnly: Boolean = false,
        faceOnly: Boolean = false,
        notRecentlyChatted: Boolean = false,
        fresh: Boolean = false,
        pageNumber: Int = 1,
        favorites: Boolean = false,
        showSponsoredProfiles: Boolean = false,
        shuffle: Boolean = false,
        hot: Boolean = false
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append("https://grindr.mobi/v3/cascade?nearbyGeoHash=$nearbyGeoHash")
                append("&onlineOnly=$onlineOnly")
                append("&photoOnly=$photoOnly")
                append("&faceOnly=$faceOnly")
                append("&notRecentlyChatted=$notRecentlyChatted")
                append("&fresh=$fresh")
                append("&pageNumber=$pageNumber")
                append("&favorites=$favorites")
                append("&showSponsoredProfiles=$showSponsoredProfiles")
                append("&shuffle=$shuffle")
                append("&hot=$hot")
            }

            val response = sendRequest(url, "GET")
            if (response.isSuccessful) {
                response.useBody { responseBody ->
                    if (!responseBody.isNullOrEmpty()) {
                        return@withContext JSONObject(responseBody)
                    }
                    JSONObject()
                }
            } else {
                Logger.e("Failed to get nearby profiles: ${response.code}")
                response.useBody { errorBody ->
                    Logger.e("Error body: $errorBody")
                }
                JSONObject()
            }
        } catch (e: Exception) {
            Logger.e("Failed to get nearby profiles: ${e.message}")
            Logger.writeRaw(e.stackTraceToString())
            JSONObject()
        }
    }

    suspend fun getBlocks(): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = sendRequest("https://grindr.mobi/v3.1/me/blocks", "GET")
            if (response.isSuccessful) {
                response.useBody { responseBody ->
                    if (!responseBody.isNullOrEmpty()) {
                        val jsonResponse = JSONObject(responseBody)
                        val blockingArray = jsonResponse.optJSONArray("blocking")
                        if (blockingArray != null) {
                            val blockedProfileIds = mutableListOf<String>()
                            for (i in 0 until blockingArray.length()) {
                                val blockingJson = blockingArray.getJSONObject(i)
                                val profileId = blockingJson.optLong("profileId")
                                blockedProfileIds.add(profileId.toString())
                            }
                            return@useBody blockedProfileIds
                        }
                    }
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Logger.e("Failed to get blocks: ${e.message}")
            Logger.writeRaw(e.stackTraceToString())
            emptyList()
        }
    }

    suspend fun getFavorites(): List<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        try {
            val response = sendRequest("https://grindr.mobi/v6/favorites", "GET")
            if (response.isSuccessful) {
                response.useBody { responseBody ->
                    if (!responseBody.isNullOrEmpty()) {
                        val jsonResponse = JSONObject(responseBody)
                        val favoritesArray = jsonResponse.optJSONArray("favorites")
                        if (favoritesArray != null) {
                            val favoriteProfileIds = mutableListOf<Triple<String, String, String>>()
                            for (i in 0 until favoritesArray.length()) {
                                val favoriteJson = favoritesArray.getJSONObject(i)
                                val profileId = favoriteJson.optString("profileId")

                                val note = try {
                                    DatabaseHelper.query(
                                        "SELECT note FROM profile_note WHERE profile_id = ?",
                                        arrayOf(profileId)
                                    ).firstOrNull()?.get("note") as? String ?: ""
                                } catch (e: Exception) {
                                    Logger.apply {
                                        log("Failed to fetch note for profileId $profileId: ${e.message}")
                                        writeRaw(e.stackTraceToString())
                                    }
                                    ""
                                }

                                val phoneNumber = try {
                                    DatabaseHelper.query(
                                        "SELECT phone_number FROM profile_note WHERE profile_id = ?",
                                        arrayOf(profileId)
                                    ).firstOrNull()?.get("phone_number") as? String ?: ""
                                } catch (e: Exception) {
                                    Logger.apply {
                                        log("Failed to fetch phone number for profileId $profileId: ${e.message}")
                                        writeRaw(e.stackTraceToString())
                                    }
                                    ""
                                }

                                favoriteProfileIds.add(Triple(profileId, note, phoneNumber))
                            }
                            return@useBody favoriteProfileIds
                        }
                    }
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Logger.e("Failed to get favorites: ${e.message}")
            Logger.writeRaw(e.stackTraceToString())
            emptyList()
        }
    }

    fun addProfileNote(profileId: String, notes: String, phoneNumber: String, silent: Boolean = false) {
        if (notes.length > 250) {
            showToast(Toast.LENGTH_LONG, "Notes are too long")
            return
        }

        val body = """
            {
                "notes": "${notes.replace("\n", "\\n")}",
                "phoneNumber": "$phoneNumber"
            }
        """.trimIndent()

        GrindrPlus.executeAsync {
            val response = sendRequest(
                "https://grindr.mobi/v1/favorites/notes/$profileId",
                "PUT",
                body = body.toRequestBody(),
                headers = mapOf("Content-Type" to "application/json; charset=utf-8")
            )
            if (response.isSuccessful) {
                response.close()
                try {
                    val existingNote = DatabaseHelper.query(
                        "SELECT * FROM profile_note WHERE profile_id = ?",
                        arrayOf(profileId)
                    ).firstOrNull()
                    if (existingNote != null) {
                        DatabaseHelper.update(
                            "profile_note",
                            ContentValues().apply {
                                put("note", notes)
                                put("phone_number", phoneNumber)
                            },
                            "profile_id = ?",
                            arrayOf(profileId)
                        )
                    } else {
                        DatabaseHelper.insert(
                            "profile_note",
                            ContentValues().apply {
                                put("profile_id", profileId)
                                put("note", notes)
                                put("phone_number", phoneNumber)
                            }
                        )
                    }
                } catch (e: Exception) {
                    Logger.e("Failed to update profile note: ${e.message}")
                    Logger.writeRaw(e.stackTraceToString())
                }
                if (!silent) showToast(Toast.LENGTH_LONG, "Note added successfully")
            } else {
                if (!silent) {
                    response.useBody { errorBody ->
                        showToast(Toast.LENGTH_LONG, "Failed to add note: $errorBody")
                    }
                }
            }
        }
    }
}

fun RequestBody.Companion.createEmpty(): RequestBody {
    return "".toRequestBody()
}

private inline fun <T> Response.useBody(block: (String?) -> T): T {
    return try {
        block(body?.string())
    } finally {
        body?.close()
    }
}