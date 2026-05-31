package com.grindrplus.tasks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import com.grindrplus.core.Utils.coordsToGeoHash
import com.grindrplus.core.logd
import com.grindrplus.core.loge

import com.grindrplus.core.logw
import com.grindrplus.utils.Task
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField

class AlwaysOnline :
    Task(
        id = "Always Online",
        description = "Keeps you online by periodically fetching cascade. May be blocked by Android doze/battery or OEM background limits, and can stop working if your Grindr session/token expires (re-open Grindr to refresh).",
        initialDelayMillis = 5 * 1000,
        intervalMillis = (Config.get("always_online_interval_mins", 15) as Number).toLong() * 60 * 1000
    ) {
    override val enabledByDefault: Boolean = false

    var lastRunTime: Long = 0
        private set
    var lastRunSuccess: Boolean = false
        private set
    override var lastError: String? = null
    var runCount: Int = 0
        private set

    override suspend fun execute(): Boolean {
        runCount++
        val runNumber = runCount
        logd("AlwaysOnline run #$runNumber starting (last run: ${if (lastRunTime > 0) "${(System.currentTimeMillis() - lastRunTime) / 1000}s ago" else "never"})")

        try {
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
                // for task to work anyway we can fall back to spoofed coordinates (if present) from Config.
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

            logd("Run #$runNumber: fetching cascade for geoHash $geoHash")
            val result = GrindrPlus.httpClient.fetchCascade(geoHash)

            lastRunTime = System.currentTimeMillis()
            if (result.has("items")) {
                lastRunSuccess = true
                lastError = null
                logd("Run #$runNumber completed successfully (next run in ${intervalMillis / 1000}s)")
                return true
            } else {
                lastRunSuccess = false
                lastError = "Cascade fetch returned no profiles (HTTP 200, empty result)."
                loge("Run #$runNumber failed: $lastError")
                return false
            }
        } catch (e: Exception) {
            lastRunTime = System.currentTimeMillis()
            lastRunSuccess = false
            lastError = "Cascade fetch failed: ${e.message}"
            loge("Run #$runNumber failed: $lastError")
            Logger.writeRaw(e.stackTraceToString())
            return false
        }
    }
}
