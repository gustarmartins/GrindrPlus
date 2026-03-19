package com.grindrplus.hooks

import android.widget.Toast
import androidx.room.withTransaction
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.core.logd
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.core.logw
import com.grindrplus.persistence.mappers.asAlbumBriefToAlbumEntity
import com.grindrplus.persistence.mappers.asAlbumToAlbumEntity
import com.grindrplus.persistence.mappers.toAlbumContentEntity
import com.grindrplus.persistence.mappers.toGrindrAlbum
import com.grindrplus.persistence.mappers.toGrindrAlbumBrief
import com.grindrplus.persistence.mappers.toGrindrAlbumContent
import com.grindrplus.persistence.mappers.toGrindrAlbumWithoutContent
import com.grindrplus.persistence.model.AlbumContentEntity
import com.grindrplus.persistence.model.AlbumEntity
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.RetrofitUtils
import com.grindrplus.utils.RetrofitUtils.createSuccess
import com.grindrplus.utils.RetrofitUtils.getSuccessValue
import com.grindrplus.utils.RetrofitUtils.isFail
import com.grindrplus.utils.RetrofitUtils.isGET
import com.grindrplus.utils.RetrofitUtils.isPUT
import com.grindrplus.utils.RetrofitUtils.isSuccess
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import com.grindrplus.utils.withSuspendResult
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class UnlimitedAlbums : Hook("Unlimited albums", "Allow to be able to view unlimited albums") {
    private val albumsService = "tk.a" // search for 'v1/albums/red-dot'
    private val albumModel = "com.grindrapp.android.chat.domain.model.Album"
    private val filteredSpankBankAlbumContent =
        "com.grindrapp.android.albums.spankbank.domain.model.FilteredSpankBankAlbumContent"
    private val spankBankAlbumModel =
        "com.grindrapp.android.albums.spankbank.domain.model.SpankBankAlbum"
    private val spankBankAlbumContentModel =
        "com.grindrapp.android.albums.spankbank.domain.model.SpankBankAlbumContent"
    private val httpExceptionResponse = "com.grindrapp.android.network.http.HttpExceptionResponse"
    private val sharedAlbumsBrief = "com.grindrapp.android.model.albums.SharedAlbumsBrief"
    private val albumsList = "com.grindrapp.android.model.AlbumsList"

    override fun init() {
        val albumsServiceClass = findClass(albumsService)

        RetrofitUtils.hookService(
            albumsServiceClass,
        ) { originalHandler, proxy, method, args ->
            val result = originalHandler.invoke(proxy, method, args)
            try {
                when {
                    method.isGET("v2/albums/{albumId}") -> handleGetAlbum(args, result)
                    method.isGET("v1/albums") -> handleGetAlbums(args, result)
                    method.isGET("v2/albums/shares") -> handleGetAlbumsShares(args, result)
                    method.isGET("v2/albums/shares/{profileId}") ->
                        handleGetAlbumsSharesProfileId(args, result)
                    method.isGET("v3/albums/{albumId}/view") ->
                        handleGetAlbumsViewAlbumId(args, result)
                    method.isPUT("v1/albums/{albumId}/shares/remove") ->
                        handleRemoveAlbumShares(args, result)
                    else -> result
                }
            } catch (e: Exception) {
                loge("Error handling album request: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
                result
            }
        }

        findClass(albumModel).hookConstructor(HookStage.AFTER) { param ->
            try {
                setObjectField(param.thisObject(), "albumViewable", true)
                setObjectField(param.thisObject(), "viewableUntil", Long.MAX_VALUE)
            } catch (e: Exception) {
                loge("Error making album viewable: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }

        findClass(albumModel).hook("getAlbumViewable", HookStage.BEFORE) { param ->
            param.setResult(true)
        }

        val albumBodyUiData = "com.grindrapp.android.chat.presentation.model.BodyUiData\$AlbumBodyUiData"
        findClass(albumBodyUiData).hook("isViewable", HookStage.BEFORE) { param ->
            param.setResult(true)
        }

        findClass("com.grindrapp.android.chat.ui.model.AlbumClickEvent").hook("isViewable", HookStage.BEFORE) { param ->
            param.setResult(true)
        }

        val albumBodyModel = "com.grindrapp.android.chat.data.model.messagebody.AlbumBody"
        findClass(albumBodyModel).hookConstructor(HookStage.AFTER) { param ->
            try {
                setObjectField(param.thisObject(), "isViewable", true)
            } catch (e: Exception) {
                loge("Error making AlbumBody viewable: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }

        findClass(albumBodyModel).hook("isViewable", HookStage.BEFORE) { param ->
            param.setResult(true)
        }

        findClass(albumBodyModel).hook("getIsViewable", HookStage.BEFORE) { param ->
            param.setResult(true)
        }

        findClass(spankBankAlbumModel).hookConstructor(HookStage.AFTER) { param ->
            try {
                setObjectField(param.thisObject(), "albumViewable", true)
                setObjectField(param.thisObject(), "expiresAt", Long.MAX_VALUE)
            } catch (e: Exception) {
                loge("Error making album viewable: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }

        listOf(spankBankAlbumContentModel, filteredSpankBankAlbumContent).forEach { clazz ->
            findClass(clazz).hookConstructor(HookStage.AFTER) { param ->
                try {
                    setObjectField(param.thisObject(), "albumViewable", true)
                } catch (e: Exception) {
                    loge("Error making album viewable: ${e.message}")
                    Logger.writeRaw(e.stackTraceToString())
                }
            }
        }

        findClass("com.grindrapp.android.albums.data.model.Album").hookConstructor(HookStage.AFTER) { param ->
            try {
                setObjectField(param.thisObject(), "albumViewable", true)
            } catch (e: Exception) {
                loge("Error making fresh album viewable: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }
        
        // v3/pressie-albums/feed responds with server paywalls, but the client can be tricked
        // and avoid it by constructing a fake spankbank model from `paywalledAlbums` URLs
        val spankBankResponseModel = "com.grindrapp.android.albums.spankbank.domain.model.SpankBankAlbumForProfileResponse"
        findClass(spankBankResponseModel).hookConstructor(HookStage.AFTER) { param ->
            try {
                val contentList = getObjectField(param.thisObject(), "content") as? List<*> ?: return@hookConstructor
                val paywalledAlbums = getObjectField(param.thisObject(), "paywalledAlbums") as? Map<*, *>

                if (contentList.isEmpty() && !paywalledAlbums.isNullOrEmpty()) {
                    val albumContentClass = findClass(spankBankAlbumContentModel)
                    val contentTypeClass = findClass("com.grindrapp.android.albums.spankbank.domain.model.SpankBankAlbumContentType")
                    val imageType = de.robv.android.xposed.XposedHelpers.getStaticObjectField(contentTypeClass, "AlbumImage")
                    val videoType = de.robv.android.xposed.XposedHelpers.getStaticObjectField(contentTypeClass, "AlbumMp4Video")

                    val newContent = mutableListOf<Any>()

                    paywalledAlbums.values.forEach { paywallContent ->
                        if (paywallContent == null) return@forEach
                        val albumId = callMethod(paywallContent, "getAlbumId") as Long
                        val urls = callMethod(paywallContent, "getPaywallUrls") as List<*>

                        urls.forEachIndexed { index, urlObj ->
                            val url = urlObj?.toString() ?: return@forEachIndexed
                            val type = if (url.contains(".mp4") || url.contains(".mov")) videoType else imageType
                            
                            // constructor: (long albumId, long contentId, ContentType, String url, boolean viewable, boolean seen, Long prev, Long next)
                            val contentInst = albumContentClass.constructors.first().newInstance(
                                albumId,
                                (albumId + index + 1), // Fake unique content ID
                                type,
                                url,
                                true, // albumViewable
                                false, // seen
                                null, // prev
                                null  // next
                            )
                            newContent.add(contentInst)
                        }
                    }

                    if (newContent.isNotEmpty()) {
                        setObjectField(param.thisObject(), "content", newContent)
                    }
                }
            } catch (e: Throwable) {
                loge("Error fixing SpankBankAlbumForProfileResponse: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }

        findClass("com.grindrapp.android.albums.data.model.fresh.FreshContent").hookConstructor(HookStage.AFTER) { param ->
            try {
                setObjectField(param.thisObject(), "albumViewable", true)
            } catch (e: Exception) {
                loge("Error making fresh album content viewable: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }

        findClass("com.grindrapp.android.albums.data.model.fresh.FreshFeedContent").hookConstructor(HookStage.AFTER) { param ->
            try {
                val allowStatus = findClass("com.grindrapp.android.albums.data.model.fresh.FreshPaywallStatus").getField("Allow").get(null)
                setObjectField(param.thisObject(), "paywallStatus", allowStatus)
            } catch (e: Exception) {
                loge("Error marking fresh feed content allowed: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }

        findClass("com.grindrapp.android.albums.domain.model.FreshData").hookConstructor(HookStage.AFTER) { param ->
            try {
                val unlockedStatus = findClass("com.grindrapp.android.albums.domain.model.FreshPaywallStatus").getField("Unlocked").get(null)
                setObjectField(param.thisObject(), "paywallStatus", unlockedStatus)
            } catch (e: Exception) {
                loge("Error marking fresh data unlocked: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }
        
        // suppress client's paywall to allow navigation in the albums feed
        val onFreshItemClicked = "com.grindrapp.android.albums.presentation.model.fresh.FreshCarouselEvent\$OnFreshItemClicked"
        findClass(onFreshItemClicked).hookConstructor(HookStage.AFTER) { param ->
            try {
                setObjectField(param.thisObject(), "isLocked", false)
            } catch (e: Throwable) {
                loge("Error bypassing pressie lock: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }

        // suppress "Unlock this album" banner in the client with an empty map
        // needs checking; i believe it is stable and also makes the UI cleaner...
        val fullScreenUiState = "com.grindrapp.android.albums.spankbank.domain.model.FullScreenUiState"
        findClass(fullScreenUiState).hook("getContentToPaywall", HookStage.BEFORE) { param ->
            try {
                val extensionsClass = findClass("kotlinx.collections.immutable.ExtensionsKt")
                val emptyMap = extensionsClass.getMethod("persistentMapOf").invoke(null)
                param.setResult(emptyMap)
            } catch (e: Throwable) {
                loge("Error suppressing album paywall button: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }

        findClass(albumModel).hook("isValid", HookStage.BEFORE) { param -> param.setResult(true) }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun saveAlbum(grindrAlbum: Any) {
        try {
            val dao = GrindrPlus.database.albumDao()

            val dbAlbum = grindrAlbum.asAlbumToAlbumEntity()
            dao.upsertAlbum(dbAlbum)

            val grindrAlbumContent = getObjectField(grindrAlbum, "content") as? List<Any> ?: return
            grindrAlbumContent.forEach {
                try {
                    val dbAlbumContent = it.toAlbumContentEntity(dbAlbum.id)
                    dao.upsertAlbumContent(dbAlbumContent)
                } catch (e: Exception) {
                    loge("Failed to convert album content: ${e.message}")
                    Logger.writeRaw(e.stackTraceToString())
                }
            }
        } catch (e: Exception) {
            loge("Failed to save album: ${e.message}")
            Logger.writeRaw(e.stackTraceToString())
        }
    }

    private suspend fun saveAlbumContent(albumId: Long, contentEntities: List<AlbumContentEntity>) {
        try {
            val dao = GrindrPlus.database.albumDao()

            val albumExists = dao.albumExists(albumId)

            if (albumExists) {
                contentEntities.forEach { content -> dao.upsertAlbumContent(content) }
            } else {
                logw("Album $albumId doesn't exist, creating placeholder")
                val currentTime = System.currentTimeMillis().toString()
                val placeholderAlbum =
                    AlbumEntity(
                        id = albumId,
                        albumName = "Unknown Album",
                        createdAt = currentTime,
                        profileId = 0L,
                        updatedAt = currentTime
                    )
                dao.upsertAlbum(placeholderAlbum)

                contentEntities.forEach { content -> dao.upsertAlbumContent(content) }
            }
        } catch (e: Exception) {
            loge("Failed to save album content: ${e.message}")
            Logger.writeRaw(e.stackTraceToString())
        }
    }

    private fun handleRemoveAlbumShares(args: Array<Any?>, result: Any) =
        withSuspendResult(args, result) { args, result ->
            val albumId = args[0] as? Long ?: return@withSuspendResult result
            logi("Removing album shares for ID: $albumId")

            if (result.isFail()) {
                try {
                    runBlocking {
                        GrindrPlus.database.withTransaction {
                            val dao = GrindrPlus.database.albumDao()
                            val albumToDelete = dao.getAlbum(albumId)

                            if (albumToDelete != null) {
                                dao.deleteAlbum(albumId)
                                createSuccess(albumId)
                            } else {
                                logd("Album with ID $albumId not found in the database.")
                                result
                            }
                        }
                    }
                } catch (e: Exception) {
                    loge("Failed to delete album $albumId: ${e.message}")
                    Logger.writeRaw(e.stackTraceToString())
                    result
                }
            } else {
                result
            }
        }

    private fun handleGetAlbumsViewAlbumId(args: Array<Any?>, result: Any) =
        withSuspendResult(args, result) { args, result ->
            val albumId = args[0] as? Long ?: return@withSuspendResult result
            logd("Checking if album $albumId is viewable")

            if (!result.isSuccess()) {
                logw("Album $albumId is not viewable, checking database")
                runBlocking {
                    val dao = GrindrPlus.database.albumDao()
                    val album = dao.getAlbum(albumId)
                    if (album != null) {
                        logd("Album $albumId is viewable, returning success")
                        createSuccess(true)
                    } else {
                        logd("Album $albumId is not viewable, returning failure")
                        result
                    }
                }
            }

            result
        }

    private fun handleGetAlbum(args: Array<Any?>, result: Any) =
        withSuspendResult(args, result) { args, result ->
            if (!result.isSuccess()) return@withSuspendResult result
            val albumId = args[0] as? Long ?: return@withSuspendResult result
            logd("Fetching album with ID: $albumId")

            try {
                GrindrPlus.httpClient
                    .sendRequest(url = "https://grindr.mobi/v1/albums/$albumId", method = "GET")
                    .use { response ->
                        val responseBody = response.body?.string()
                        if (!responseBody.isNullOrEmpty()) {
                            logd("Got ${responseBody.length} bytes for album $albumId")
                            val modifiedResult = parseAlbumContent(albumId, responseBody, result)
                            return@withSuspendResult modifiedResult
                        } else {
                            loge("Empty response body for album $albumId")
                            val modifiedResult = fetchAlbumFromDatabase(albumId, result)
                            return@withSuspendResult modifiedResult
                        }
                    }
            } catch (e: Exception) {
                loge("Failed to fetch album $albumId: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Failed to load album")
                val modifiedResult = fetchAlbumFromDatabase(albumId, result)
                return@withSuspendResult modifiedResult
            }
        }

    private fun fetchAlbumFromDatabase(albumId: Long, originalResult: Any): Any {
        if (!originalResult.isSuccess()) return originalResult
        try {
            loge("Fetching album with ID: $albumId from database")
            return runBlocking {
                val dao = GrindrPlus.database.albumDao()
                val album = dao.getAlbum(albumId)
                if (album != null) {
                    val content = dao.getAlbumContent(albumId)
                    var albumObject = getObjectField(originalResult, "a")

                    if (
                        albumObject != null && albumObject.javaClass.name == httpExceptionResponse
                    ) {
                        logw(
                            "Album object is HttpExceptionResponse, creating new Album from entity"
                        )
                        albumObject = album.toGrindrAlbumWithoutContent()
                    }

                    if (albumObject != null) {
                        setObjectField(albumObject, "albumId", albumId)
                        setObjectField(
                            albumObject,
                            "content",
                            content.map { it.toGrindrAlbumContent() }
                        )
                        setObjectField(albumObject, "albumViewable", true)
                        setObjectField(albumObject, "viewableUntil", Long.MAX_VALUE)
                        createSuccess(albumObject)
                    } else {
                        loge("Album is null, cannot set content")
                        originalResult
                    }
                } else {
                    logw("Album $albumId not found in database")
                    originalResult
                }
            }
        } catch (e: Exception) {
            loge("Failed to load album $albumId from database: ${e.message}")
            Logger.writeRaw(e.stackTraceToString())
            return originalResult
        }
    }

    private fun parseAlbumContent(albumId: Long, responseBody: String, originalResult: Any): Any {
        if (!originalResult.isSuccess()) return originalResult
        try {
            logd("Parsing album content for ID: $albumId")
            val jsonResponse = JSONObject(responseBody)
            jsonResponse.optJSONArray("content")?.let { contentArray ->
                logd("Content array found for album ID: $albumId")
                val albumContentEntities = mutableListOf<AlbumContentEntity>()

                for (i in 0 until contentArray.length()) {
                    try {
                        val contentJson = contentArray.getJSONObject(i)
                        val albumContentEntity =
                            AlbumContentEntity(
                                id = contentJson.optLong("contentId"),
                                albumId = albumId,
                                contentType = contentJson.optString("contentType"),
                                coverUrl = contentJson.optString("coverUrl"),
                                thumbUrl = contentJson.optString("thumbUrl"),
                                url = contentJson.optString("url")
                            )
                        albumContentEntities.add(albumContentEntity)
                    } catch (e: Exception) {
                        loge("Error parsing content item: ${e.message}")
                        Logger.writeRaw(e.stackTraceToString())
                    }
                }

                if (albumContentEntities.isNotEmpty()) {
                    logd("Saving album content for ID: $albumId")
                    try {
                        runBlocking { saveAlbumContent(albumId, albumContentEntities) }
                    } catch (e: Exception) {
                        loge("Failed to save album content: ${e.message}")
                        Logger.writeRaw(e.stackTraceToString())
                    }
                }

                try {
                    val grindrAlbumContentList =
                        albumContentEntities.map { it.toGrindrAlbumContent() }
                    val albumObject = getObjectField(originalResult, "a")
                    if (albumObject != null) {
                        logd("Setting album content for ID: $albumId")
                        setObjectField(albumObject, "content", grindrAlbumContentList)
                        return originalResult
                    } else {
                        loge("Album object not found in result for album ID: $albumId")
                    }
                } catch (e: Exception) {
                    loge("Error setting album content: ${e.message}")
                    Logger.writeRaw(e.stackTraceToString())
                }
            }
                ?: run {
                    loge("Failed to parse content array for album ID: $albumId")
                    return fetchAlbumFromDatabase(albumId, originalResult)
                }
        } catch (e: Exception) {
            loge("Error parsing album content: ${e.message}")
            Logger.writeRaw(e.stackTraceToString())
            return fetchAlbumFromDatabase(albumId, originalResult)
        }

        return originalResult
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleGetAlbums(args: Array<Any?>, result: Any) =
        withSuspendResult(args, result) { _, result ->
            if (result.isSuccess()) {
                try {
                    val albums = getObjectField(result.getSuccessValue(), "albums") as? List<Any>
                    if (albums != null) {
                        runBlocking {
                            GrindrPlus.database.withTransaction {
                                albums.forEach { album ->
                                    try {
                                        saveAlbum(album)
                                    } catch (e: Exception) {
                                        loge("Error saving album: ${e.message}")
                                        Logger.writeRaw(e.stackTraceToString())
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    loge("Error processing albums: ${e.message}")
                    Logger.writeRaw(e.stackTraceToString())
                }
            }

            try {
                val albums = runBlocking {
                    GrindrPlus.database.withTransaction {
                        val dao = GrindrPlus.database.albumDao()
                        val dbAlbums = dao.getAlbums()
                        dbAlbums.mapNotNull {
                            try {
                                val dbContent = dao.getAlbumContent(it.id)
                                it.toGrindrAlbum(dbContent)
                            } catch (e: Exception) {
                                loge("Error converting album ${it.id}: ${e.message}")
                                Logger.writeRaw(e.stackTraceToString())
                                null
                            }
                        }
                    }
                }

                val newValue =
                    findClass(albumsList)
                        .getConstructor(List::class.java)
                        .newInstance(albums)

                createSuccess(newValue)
            } catch (e: Exception) {
                loge("Error creating albums list: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
                result
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun handleGetAlbumsShares(args: Array<Any?>, result: Any) =
        withSuspendResult(args, result) { _, result ->
            logd("Fetching shared albums")
            if (result.isSuccess()) {
                try {
                    runBlocking {
                        GrindrPlus.database.withTransaction {
                            val dao = GrindrPlus.database.albumDao()
                            val albumBriefs =
                                getObjectField(result.getSuccessValue(), "albums") as? List<Any>
                            albumBriefs?.forEach { albumBrief ->
                                try {
                                    val albumEntity = albumBrief.asAlbumBriefToAlbumEntity()
                                    dao.upsertAlbum(albumEntity)

                                    val grindrAlbumContent = getObjectField(albumBrief, "content")
                                    if (grindrAlbumContent != null) {
                                        val dbAlbumContent =
                                            grindrAlbumContent.toAlbumContentEntity(albumEntity.id)
                                        dao.upsertAlbumContent(dbAlbumContent)
                                    }
                                } catch (e: Exception) {
                                    loge("Error processing album brief: ${e.message}")
                                    Logger.writeRaw(e.stackTraceToString())
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    loge("Error saving album briefs: ${e.message}")
                    Logger.writeRaw(e.stackTraceToString())
                }
            }

            try {
                val albumBriefs = runBlocking {
                    GrindrPlus.database.withTransaction {
                        val dao = GrindrPlus.database.albumDao()
                        val dbAlbums = dao.getAlbums()
                        dbAlbums.mapNotNull {
                            try {
                                val dbContent = dao.getAlbumContent(it.id)
                                if (dbContent.isNotEmpty()) {
                                    it.toGrindrAlbumBrief(dbContent.first())
                                } else {
                                    logw("Album ${it.id} has no content")
                                    null
                                }
                            } catch (e: Exception) {
                                loge("Error converting album ${it.id} to brief: ${e.message}")
                                Logger.writeRaw(e.stackTraceToString())
                                null
                            }
                        }
                    }
                }

                val newValue =
                    findClass(sharedAlbumsBrief)
                        .getConstructor(List::class.java)
                        .newInstance(albumBriefs)

                createSuccess(newValue)
            } catch (e: Exception) {
                loge("Error creating shared albums brief: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
                result
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun handleGetAlbumsSharesProfileId(args: Array<Any?>, result: Any) =
        withSuspendResult(args, result) { args, result ->
            logd("Fetching shared albums for profile ID")

            val profileId = args[0] as? Long ?: return@withSuspendResult result

            if (result.isSuccess()) {
                try {
                    runBlocking {
                        GrindrPlus.database.withTransaction {
                            val dao = GrindrPlus.database.albumDao()
                            val albumBriefs =
                                getObjectField(result.getSuccessValue(), "albums") as? List<Any>
                            albumBriefs?.forEach { albumBrief ->
                                try {
                                    val albumEntity = albumBrief.asAlbumBriefToAlbumEntity()
                                    dao.upsertAlbum(albumEntity)

                                    val grindrAlbumContent = getObjectField(albumBrief, "content")
                                    if (grindrAlbumContent != null) {
                                        val dbAlbumContent =
                                            grindrAlbumContent.toAlbumContentEntity(albumEntity.id)
                                        dao.upsertAlbumContent(dbAlbumContent)
                                    }
                                } catch (e: Exception) {
                                    loge("Error processing album brief: ${e.message}")
                                    Logger.writeRaw(e.stackTraceToString())
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    loge("Error saving album briefs: ${e.message}")
                    Logger.writeRaw(e.stackTraceToString())
                }
            }

            try {
                val albumBriefs = runBlocking {
                    GrindrPlus.database.withTransaction {
                        val dao = GrindrPlus.database.albumDao()
                        val dbAlbums = dao.getAlbums(profileId)
                        dbAlbums.mapNotNull {
                            try {
                                val dbContent = dao.getAlbumContent(it.id)
                                if (dbContent.isNotEmpty()) {
                                    it.toGrindrAlbumBrief(dbContent.first())
                                } else {
                                    logw("Album ${it.id} has no content")
                                    null
                                }
                            } catch (e: Exception) {
                                loge("Error converting album ${it.id} to brief: ${e.message}")
                                Logger.writeRaw(e.stackTraceToString())
                                null
                            }
                        }
                    }
                }

                val newValue =
                    findClass(sharedAlbumsBrief)
                        .getConstructor(List::class.java)
                        .newInstance(albumBriefs)

                createSuccess(newValue)
            } catch (e: Exception) {
                loge("Error creating shared albums brief: ${e.message}")
                result
            }
        }

    private inline fun <T : Closeable?, R> T.use(block: (T) -> R): R {
        var closed = false
        try {
            return block(this)
        } catch (e: Exception) {
            closed = true
            try {
                this?.close()
            } catch (closeException: IOException) {
                e.addSuppressed(closeException)
            }
            throw e
        } finally {
            if (!closed) {
                this?.close()
            }
        }
    }
}
