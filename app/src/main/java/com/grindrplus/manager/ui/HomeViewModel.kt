package com.grindrplus.manager.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindrplus.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.net.UnknownHostException
import java.time.Instant

data class Release(
    val name: String,
    val description: String,
    val author: String,
    val avatarUrl: String,
    val publishedAt: Instant,
)

class HomeViewModel : ViewModel() {
    val contributors = mutableStateMapOf<String, String>()
    val releases = mutableStateMapOf<String, Release>()
    val isLoading = mutableStateOf(true)
    val loadingText = mutableStateOf("Fetching latest updates...")
    val errorMessage = mutableStateOf<String?>(null)

    // Flag to avoid multiple fetches
    private var hasFetched = false

    companion object {
        private val TAG = HomeViewModel::class.simpleName

        private const val CONTRIBUTORS_URL = "https://api.github.com/repos/R0rt1z2/GrindrPlus/contributors"
        private const val RELEASES_URL = "https://api.github.com/repos/R0rt1z2/GrindrPlus/releases"
        // Reuse the HTTP client across requests
        private val client = OkHttpClient()
    }
    
    private fun fetchUrlContent(url: String): Result<String> {
        val maxRetries = 3
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                Logger.d("$TAG: Fetching content from $url (Attempt $attempt/$maxRetries)")
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Server error: ${response.code}")
                    val body = response.body?.string() ?: throw IOException("Empty response body")
                    return Result.success(body)
                }
            } catch (e: Exception) {
                lastException = e
                Logger.e("$TAG: Attempt $attempt at fetching data failed: ${e.message}")
                if (attempt < maxRetries) {
                    Thread.sleep(2000)
                }
            }
        }
        // in this case the connection was blocked by something out of our control
        return Result.failure(Exception("Connection to $url refused. Please check your VPN, Proxy or AdBlocker settings.", lastException))
    }

    private suspend fun parseContributors(jsonContent: String) = withContext(Dispatchers.Default) {
        val jsonArray = JSONArray(jsonContent)
        // Accumulate new data in a temporary map
        val newContributors = mutableMapOf<String, String>()
        for (i in 0 until jsonArray.length()) {
            val contributor = jsonArray.getJSONObject(i)
            if (contributor.getString("login").contains("bot")) continue
            newContributors[contributor.getString("login")] = contributor.getString("avatar_url")
        }
        // Update the state in bulk
        contributors.clear()
        contributors.putAll(newContributors)
    }

    private suspend fun parseReleases(jsonContent: String) = withContext(Dispatchers.Default) {
        val jsonArray = JSONArray(jsonContent)
        val newReleases = mutableMapOf<String, Release>()
        for (i in 0 until jsonArray.length()) {
            val release = jsonArray.getJSONObject(i)
            val id = release.getString("id")
            val name = if (!release.isNull("name"))
                release.getString("name") else release.getString("tag_name")
            val description = if (!release.isNull("body"))
                release.getString("body") else "No description provided"
            val author = release.getJSONObject("author").getString("login")
            val avatarUrl = release.getJSONObject("author").getString("avatar_url")
            val publishedAt = Instant.parse(release.getString("published_at"))

            newReleases[id] = Release(name, description, author, avatarUrl, publishedAt)
        }
        releases.clear()
        releases.putAll(newReleases)
    }

    fun fetchData(forceRefresh: Boolean = false) {
        if (hasFetched && !forceRefresh) return
        if (forceRefresh) {
            errorMessage.value = null
        }
        isLoading.value = true
        loadingText.value = "Fetching latest updates..."

        viewModelScope.launch {
            val textUpdateJob = launch {
                delay(10000)
                loadingText.value = "Still fetching... Check your internet connectivity."
            }
            try {
                coroutineScope {
                    val contributorsDeferred = async(Dispatchers.IO) { fetchUrlContent(CONTRIBUTORS_URL) }
                    val releasesDeferred = async(Dispatchers.IO) { fetchUrlContent(RELEASES_URL) }
                    
                    val contributorsResult = contributorsDeferred.await()
                    val releasesResult = releasesDeferred.await()
                    
                    // this blocks the "still fetching..." in case it finishes early
                    textUpdateJob.cancel() 

                    val contributorsError = contributorsResult.exceptionOrNull()
                    val releasesError = releasesResult.exceptionOrNull()

                    if (contributorsResult.isSuccess && releasesResult.isSuccess) {
                        parseContributors(contributorsResult.getOrThrow())
                        parseReleases(releasesResult.getOrThrow())
                        
                        hasFetched = true
                    } else {
                        throw contributorsError ?: releasesError ?: Exception("Unknown fetch error")
                    }
                }
            } catch (e: UnknownHostException) {
                Logger.e("$TAG: No internet connection: ${e.message}")
                errorMessage.value = "No internet connection. Please check your network and try again."
            } catch (e: Exception) {
            Logger.e("$TAG: Error fetching data: ${e.message}")
            errorMessage.value = e.message ?: "An error occurred: ${e.localizedMessage}"
        } finally { 
            textUpdateJob.cancel()
                isLoading.value = false
            }
        }
    }
}