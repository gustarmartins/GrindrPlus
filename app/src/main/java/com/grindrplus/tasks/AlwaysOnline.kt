package com.grindrplus.tasks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.CoroutineHelper.callSuspendFunction
import com.grindrplus.core.Logger
import com.grindrplus.core.Utils.coordsToGeoHash
import com.grindrplus.core.logd
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.core.logw
import com.grindrplus.utils.Task
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField

class AlwaysOnline :
    Task(
        id = "Always Online",
        description = "Keeps you online by periodically fetching cascade",
        initialDelayMillis = 5 * 1000,
        intervalMillis = 5 * 60 * 1000
    ) {
    var lastRunTime: Long = 0
        private set
    var lastRunSuccess: Boolean = false
        private set
    var lastError: String? = null
        private set
    var runCount: Int = 0
        private set

    override suspend fun execute(): Boolean {
        runCount++
        val runNumber = runCount
        logd("AlwaysOnline run #$runNumber starting (last run: ${if (lastRunTime > 0) "${(System.currentTimeMillis() - lastRunTime) / 1000}s ago" else "never"})")

        try {
            val serverDrivenCascadeRepoInstance =
                GrindrPlus.instanceManager.getInstance<Any>(GrindrPlus.serverDrivenCascadeRepo)
            if (serverDrivenCascadeRepoInstance == null) {
                lastRunTime = System.currentTimeMillis()
                lastRunSuccess = false
                lastError = "Unable to access the cascade instance." +
                        " This could mean the app was killed by Android's process killer, " +
                        "or that you have not logged into your account yet."
                logw("Run #$runNumber skipped: $lastError")
                return false
            }

            val grindrLocationProviderInstance =
                GrindrPlus.instanceManager.getInstance<Any>(GrindrPlus.grindrLocationProvider)
            if (grindrLocationProviderInstance == null) {
                lastRunTime = System.currentTimeMillis()
                lastRunSuccess = false
                lastError = "Location provider not initialized yet."
                logw("Run #$runNumber skipped: $lastError")
                return false
            }

            val location = getObjectField(grindrLocationProviderInstance, "e")
            val latitude: Double
            val longitude: Double

            if (location != null) {
                latitude = callMethod(location, "getLatitude") as Double
                longitude = callMethod(location, "getLongitude") as Double
            } else {
                // app does not have Location access permissions or something else
                // for task to work anyways we can fall back to spoofed coordinates (if present) from Config.
                val coords = (Config.get("forced_coordinates",
                    Config.get("current_location", "")) as String)
                    .takeIf { it.isNotEmpty() }
                    ?.split(",")

                if (coords != null && coords.size == 2) {
                    latitude = coords[0].toDoubleOrNull() ?: 0.0
                    longitude = coords[1].toDoubleOrNull() ?: 0.0
                    val locationName = (Config.get("current_location_name", "") as String)
                        .takeIf { it.isNotEmpty() }
                    logd("Run #$runNumber: using fallback location ${locationName ?: "($latitude, $longitude)"}")
                } else {
                    lastRunTime = System.currentTimeMillis()
                    lastRunSuccess = false
                    lastError = "Location unavailable due to Android's standby or permission restrictions" +
                            " (and we found no spoofed location to fallback into)"
                    logw("Run #$runNumber skipped: $lastError")
                    return false
                }
            }
            val geoHash = coordsToGeoHash(latitude, longitude)

            val methodName = "fetchCascadePage"
            val method =
                serverDrivenCascadeRepoInstance.javaClass.methods.firstOrNull {
                    it.name == methodName
                } ?: throw IllegalStateException("Unable to find $methodName method")

            val expectedParamCount = method.parameterCount - 1 // exclude continuation
            logd("Run #$runNumber: calling $methodName (expects $expectedParamCount params + continuation)")

            val params = arrayOf<Any?>(
                geoHash,
                null,
                false, false, false, false,
                null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
                false,
                1,
                null, null,
                false, false, false,
                null,
                false,
                null
            )

            if (params.size != expectedParamCount) {
                lastRunTime = System.currentTimeMillis()
                lastRunSuccess = false
                lastError = "Wrong number of arguments in Always Online module. Expected $expectedParamCount, got ${params.size}. " +
                        "This likely means that this module is outdated and should be disabled for now."
                loge("Run #\$runNumber failed: $lastError")
                return false
            }

            val result = callSuspendFunction { continuation ->
                method.invoke(serverDrivenCascadeRepoInstance, *params, continuation)
            }

            lastRunTime = System.currentTimeMillis()
            if (result.toString().contains("Success")) {
                lastRunSuccess = true
                lastError = null
                logd("Run #$runNumber completed successfully (next run in ${intervalMillis / 1000}s)")
                return true
            } else {
                lastRunSuccess = false
                val resultStr = result.toString()
                lastError = "Unexpected result: $resultStr"
                loge("Run #$runNumber failed: $lastError")
                if (resultStr.contains("errorCode=500")) {
                    logw("Hint: HTTP 500 often occurs when the system restricts Grindr in background. " +
                        "Try setting Grindr to 'Unrestricted' in battery optimization settings.")
                }
                return false
            }
        } catch (e: Exception) {
            lastRunTime = System.currentTimeMillis()
            lastRunSuccess = false
            lastError = e.message
            loge("Run #$runNumber failed: Unknown error in Always Online task: $lastError")
            Logger.writeRaw(e.stackTraceToString())
            return false
        }
    }
}
