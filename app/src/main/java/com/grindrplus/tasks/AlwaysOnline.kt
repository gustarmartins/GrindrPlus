package com.grindrplus.tasks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.CoroutineHelper.callSuspendFunction
import com.grindrplus.core.Logger
import com.grindrplus.core.Utils.coordsToGeoHash
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
        initialDelayMillis = 30 * 1000,
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
        logi("AlwaysOnline run #$runNumber starting (last run: ${if (lastRunTime > 0) "${(System.currentTimeMillis() - lastRunTime) / 1000}s ago" else "never"})")

        try {
            val serverDrivenCascadeRepoInstance =
                GrindrPlus.instanceManager.getInstance<Any>(GrindrPlus.serverDrivenCascadeRepo)
            val grindrLocationProviderInstance =
                GrindrPlus.instanceManager.getInstance<Any>(GrindrPlus.grindrLocationProvider)
            if (grindrLocationProviderInstance == null) {
                lastRunTime = System.currentTimeMillis()
                lastRunSuccess = false
                lastError = "Failed to get the location provider instance. (app may be in background)"
                logw("Run #$runNumber skipped: $lastError")
                return false
            }

            val location = getObjectField(grindrLocationProviderInstance, "e")
            if (location == null) {
                lastRunTime = System.currentTimeMillis()
                lastRunSuccess = false
                lastError = "Location object is null. Check Grindr's Android permissions."
                logw("Run #$runNumber skipped: $lastError")
                return false
            }
            val latitude = callMethod(location, "getLatitude") as Double
            val longitude = callMethod(location, "getLongitude") as Double
            val geoHash = coordsToGeoHash(latitude, longitude)

            val methodName = "fetchCascadePage"
            val method =
                serverDrivenCascadeRepoInstance!!.javaClass.methods.firstOrNull {
                    it.name == methodName
                } ?: throw IllegalStateException("Unable to find $methodName method")

            val expectedParamCount = method.parameterCount - 1 // exclude continuation
            logi("Run #$runNumber: calling $methodName (expects $expectedParamCount params + continuation)")

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
                logi("Run #$runNumber completed successfully (next run in ${intervalMillis / 1000}s)")
                return true
            } else {
                lastRunSuccess = false
                lastError = "Unexpected result: $result"
                loge("Run #$runNumber failed: $lastError")
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
