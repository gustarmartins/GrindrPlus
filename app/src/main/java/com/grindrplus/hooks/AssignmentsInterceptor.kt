package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.core.FeatureDefinitions
import com.grindrplus.core.Logger
import com.grindrplus.core.logd
import com.grindrplus.manager.settings.FeatureState
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers.callMethod
import org.json.JSONArray
import org.json.JSONObject

class AssignmentsInterceptor : Hook(
    "Assignments interceptor",
    "Spoof server feature flag assignments"
) {
    private val webSocketClientImpl = "com.grindrapp.android.network.websocket.WebSocketClientImpl"
    private val reconnectStrategy = "d30.b" // exponential backoff counter from c

    override fun init() {
        injectHttpInterceptor()
        hookWebSocket403()
    }

    private fun injectHttpInterceptor() {
        findClass("okhttp3.OkHttpClient\$Builder")
            .hookConstructor(HookStage.AFTER) { param ->
                try {
                    val classLoader = param.thisObject().javaClass.classLoader!!
                    val interceptorClass = classLoader.loadClass("okhttp3.Interceptor")
                    val chainClass = classLoader.loadClass("okhttp3.Interceptor\$Chain")

                    val proxy = java.lang.reflect.Proxy.newProxyInstance(
                        classLoader,
                        arrayOf(interceptorClass)
                    ) { _, method, args ->
                        if (method.name == "intercept" && args != null && args.size == 1) {
                            interceptResponse(args[0])
                        } else {
                            null
                        }
                    }

                    callMethod(param.thisObject(), "addInterceptor", proxy)
                    logd("Injected assignments response interceptor via Proxy")
                } catch (e: Exception) {
                    loge("Failed to inject assignments interceptor: ${e.message}")
                    Logger.writeRaw(e.stackTraceToString())
                }
            }
    }


    private fun hookWebSocket403() {
        findClass(webSocketClientImpl)
            .hook("onFailure", HookStage.BEFORE) { param ->
                try {
                    val response = param.args().getOrNull(2) ?: return@hook
                    val code = callMethod(response, "code") as? Int ?: return@hook

                    if (code == 403) {
                        logd("WebSocket got 403 - forcing extended backoff")

                        //  inflate  the backoff so the reconnect loop stops

                        try {
                            val backoffClass = findClass(reconnectStrategy)
                            // The counter field 'a' controls the delay calculation
                            // delay = min(2500 * 2 * count, 180000) / 2
                            val counterField = backoffClass.getDeclaredField("a")
                            counterField.isAccessible = true

                        } catch (_: Exception) {}

                        Thread.sleep(15_000)
                        logd("WebSocket 403 cooldown complete, allowing reconnect")
                    }
                } catch (e: Exception) {
                    loge("Error in WebSocket 403 handler: ${e.message}")
                }
            }
    }

    private val assignmentPaths = setOf("/v3/assignment", "/public/v1/public-features")

    private val dumpPaths = assignmentPaths + setOf(
        "/v3/me", "/v1/me", "/v4/me",
        "/v3/bootstrap",
        "/v8/sessions",
    )

    private val dumpKeywords = listOf("feature", "subscription", "entitlement", "upsell")

    private fun isDumpMode(): Boolean =
        Config.get("dump_raw_assignments", false) as Boolean

    private fun interceptResponse(chainObj: Any): Any {
        val request = callMethod(chainObj, "request")
        val response = try {
            callMethod(chainObj, "proceed", request)
        } catch (e: de.robv.android.xposed.XposedHelpers.InvocationTargetError) {
            throw e.cause ?: e
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause ?: e
        }

        val url = callMethod(request, "url")
        val path = callMethod(url, "encodedPath") as String
        val dumpMode = isDumpMode()

        val isInterestingPath = if (dumpMode) {
            path in dumpPaths || dumpKeywords.any { path.lowercase().contains(it) }
        } else {
            path in assignmentPaths
        }



        if (!isInterestingPath) {
            return response as Any
        }

        return try {
            val body = callMethod(response, "body") ?: return response as Any
            val contentType = callMethod(body, "contentType")
            val originalJson = callMethod(body, "string") as String

            if (dumpMode) {
                logi("=== RAW DUMP: $path ===")
                Logger.writeRaw("--- RAW_DUMP_START $path ---")
                Logger.writeRaw(originalJson)
                Logger.writeRaw("--- RAW_DUMP_END $path ---")

                return rebuildResponse(response, body, originalJson, contentType)
            }

            val modifiedJson = modifyAssignments(originalJson, path)
            rebuildResponse(response, body, modifiedJson, contentType)
        } catch (e: Exception) {
            loge("Error intercepting $path: ${e.message}")
            Logger.writeRaw(e.stackTraceToString())
            response as Any
        }
    }

    /**
     * Rebuilds an OkHttp Response with a new string body, preserving content type.
     */
    private fun rebuildResponse(response: Any, body: Any, json: String, contentType: Any?): Any {
        val mediaType = contentType ?: run {
            val mediaTypeClass = body.javaClass.classLoader!!.loadClass("okhttp3.MediaType")
            val companionClass = body.javaClass.classLoader!!.loadClass("okhttp3.MediaType\$Companion")
            val companion = mediaTypeClass.getDeclaredField("Companion").get(null)
            callMethod(companion, "get", "application/json; charset=utf-8")
        }

        val responseBodyClass = body.javaClass.classLoader!!.loadClass("okhttp3.ResponseBody")
        val companionField = responseBodyClass.getDeclaredField("Companion")
        val companion = companionField.get(null)

        val createMethod = companion.javaClass.methods.first { m ->
            m.name == "create" && m.parameterTypes.size == 2
                && m.parameterTypes[0] == String::class.java
        }
        val newBody = createMethod.invoke(companion, json, mediaType)

        val builder = callMethod(response, "newBuilder")
        callMethod(builder, "body", newBody)
        return callMethod(builder, "build")
    }

    private fun modifyAssignments(originalJson: String, path: String): String {
        val root = JSONObject(originalJson)
        val assignments = root.optJSONArray("assignments") ?: JSONArray()

        // Index existing assignments by key for fast lookup
        val assignmentMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until assignments.length()) {
            val assignment = assignments.getJSONObject(i)
            val key = assignment.optString("key", "")
            if (key.isNotEmpty()) {
                assignmentMap[key] = assignment
            }
        }

        var overrideCount = 0
        var injectCount = 0
        var passthroughCount = 0

        for (def in FeatureDefinitions.ALL) {
            val configKey = FeatureDefinitions.configKey(def.name)
            val rawValue = Config.get(configKey, def.defaultState.configValue)
            val state = when (rawValue) {
                is Boolean -> FeatureState.fromLegacyBoolean(rawValue)
                is String -> FeatureState.fromConfig(rawValue)
                else -> def.defaultState
            }

            // this is to let the server's own value stay
            if (state == FeatureState.DEFAULT) {
                passthroughCount++
                continue
            }

            val desiredValue = if (state == FeatureState.ON) "on" else "off"
            val existing = assignmentMap[def.assignmentKey]

            if (existing != null) {
                val currentValue = existing.optString("value", "")
                if (currentValue != desiredValue) {
                    existing.put("value", desiredValue)
                    overrideCount++
                }
            } else {

                val newAssignment = JSONObject().apply {
                    put("key", def.assignmentKey)
                    put("value", desiredValue)
                    put("type", "FEATURE_FLAG")
                    put("source", "assignments")
                }
                assignments.put(newAssignment)
                injectCount++
            }
        }

        root.put("assignments", assignments)

        logd("Assignments interceptor ($path): " +
            "${assignmentMap.size} server flags, " +
            "$overrideCount overridden, " +
            "$injectCount injected, " +
            "$passthroughCount passthrough"
        )

        return root.toString()
    }
}
