package com.grindrplus.core.http

import android.content.ContentValues
import android.widget.Toast
import com.grindrplus.BuildConfig
import com.grindrplus.GrindrPlus
import com.grindrplus.GrindrPlus.showToast
import com.grindrplus.core.DatabaseHelper
import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
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
     * Sends a manual POST to /v8/sessions to refresh the user's presence
     * in the cascade grid at the new location.
     *
     * Based on mitmproxy analysis, the payload structure is:
     * {
     *   "email":     "user@example.com",          -- user's login email
     *   "authToken": "eb082524fa48628...",          -- 64-char hex session hash (NOT the JWT!)
     *   "token":     "dtDLizl...:APA91bGx...",     -- Firebase Cloud Messaging push token
     *   "geohash":   "sg4prgvx4yd0"                -- location geohash
     * }
     *
     * The JWT goes only in the Authorization header (handled by Interceptor).
     * All three body fields are extracted from the userSession instance by
     * enumerating its String-returning methods and classifying by format.
     */
    fun refreshSession(geohash: String): Boolean {
        try {
            val sessionsUrl = "https://grindr.mobi/v8/sessions"
            val jsonHeaders = mapOf("Content-Type" to "application/json; charset=UTF-8")

            val userSessionInstance = GrindrPlus.instanceManager
                .getInstance<Any>(GrindrPlus.userSession)

            if (userSessionInstance == null) {
                Logger.w("Cannot refresh session: userSession instance not available")
                return false
            }

            // Extract all string values from userSession methods
            val sessionStrings = extractAllSessionStrings(userSessionInstance)

            // Classify the extracted strings by their format
            var email = ""
            var authTokenHex = ""
            var fcmToken = ""

            for ((methodName, value) in sessionStrings) {
                when {
                    // Firebase FCM token: contains ":" and typically "APA91b" or similar
                    value.contains(":") && value.length > 50 -> {
                        fcmToken = value
                        Logger.d("Session field 'token' (FCM) from method=$methodName (len=${value.length})")
                    }
                    // Email: contains "@"
                    value.contains("@") && value.contains(".") -> {
                        email = value
                        Logger.d("Session field 'email' from method=$methodName")
                    }
                    // 64-char hex hash: exactly 64 hex chars, no dots
                    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> {
                        authTokenHex = value
                        Logger.d("Session field 'authToken' (hex) from method=$methodName")
                    }
                }
            }

            // Log what we found for debugging
            Logger.d("Session refresh payload — " +
                    "email: ${if (email.isNotEmpty()) "${email.take(3)}***" else "(empty)"}, " +
                    "authToken(hex): ${if (authTokenHex.isNotEmpty()) "${authTokenHex.take(8)}..." else "(empty)"}, " +
                    "token(fcm): ${if (fcmToken.isNotEmpty()) "${fcmToken.take(12)}..." else "(empty)"}, " +
                    "geohash: $geohash")

            if (email.isEmpty() || authTokenHex.isEmpty()) {
                Logger.w("Cannot refresh session: missing email or authToken. " +
                        "Found ${sessionStrings.size} string methods on userSession.")
                // Dump all candidates for debugging
                sessionStrings.forEach { (name, value) ->
                    Logger.d("  userSession.$name() = \"${value.take(30)}...\" (len=${value.length})")
                }
                return false
            }

            // Build the payload
            val bodyJson = JSONObject().apply {
                put("email", email)
                put("authToken", authTokenHex)
                put("token", fcmToken)
                put("geohash", geohash)
            }

            val body = bodyJson.toString().toRequestBody()
            val response = sendRequest(sessionsUrl, "POST", body = body, headers = jsonHeaders)

            if (!response.isSuccessful) {
                response.useBody { errorBody ->
                    // Truncate to avoid dumping full Cloudflare HTML pages into logcat
                    val truncated = if ((errorBody?.length ?: 0) > 200) errorBody?.take(200) + "..." else errorBody
                    Logger.w("Session refresh failed (${response.code}): $truncated")
                }
                return false
            } else {
                response.close()
                Logger.d("Session refreshed for geohash $geohash")
                return true
            }
        } catch (e: Exception) {
            Logger.w("Session refresh failed: ${e.message}")
            return false
        }
    }

    /**
     * Enumerates all no-arg String-returning methods on the userSession instance
     * and returns their name→value pairs, filtering out empty/short values and
     * known non-interesting ones (roles JSON, etc).
     */
    private fun extractAllSessionStrings(userSession: Any): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()

        for (method in userSession.javaClass.declaredMethods) {
            if (method.parameterCount != 0) continue
            if (method.returnType != String::class.java) continue
            try {
                method.isAccessible = true
                val value = method.invoke(userSession) as? String ?: continue
                if (value.isEmpty()) continue
                if (value.length < 5) continue          // skip very short values
                if (value.startsWith("[")) continue      // roles JSON array
                results.add(method.name to value)
            } catch (_: Exception) {
                // skip methods that throw
            }
        }

        // Also check StateFlow<String> fields (like the JWT authToken flow)
        // We skip these for the body payload since the JWT goes in the header only.

        return results
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