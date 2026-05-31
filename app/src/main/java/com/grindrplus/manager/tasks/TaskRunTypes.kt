package com.grindrplus.manager.tasks

data class TaskRunEvent(
    val taskId: String,
    val success: Boolean,
    val error: String?,
    val timestamp: Long,
    val durationMs: Long
)

object TaskErrorExplainer {
    private val explanations = listOf(
        "Location provider not initialized" to "Location services aren't ready yet",
        "Location unavailable" to "Android killed background location access; no spoofed location fallback",
        "standby" to "Android killed background location access; no spoofed location fallback",
        "HTTP 401" to "Auth token rejected — try re-logging into Grindr",
        "HTTP 403" to "Auth token rejected — try re-logging into Grindr",
        "HTTP 429" to "Rate limited by Grindr — increase the Always Online interval",
        "HTTP 500" to "Grindr server error",
        "HTTP 502" to "Grindr server temporarily unavailable",
        "HTTP 503" to "Grindr server temporarily unavailable",
        "EAI_NODATA" to "No network/DNS — background data may be restricted for Grindr",
        "Unable to resolve host" to "No network/DNS — background data may be restricted for Grindr",
        "getaddrinfo" to "No network/DNS — background data may be restricted for Grindr",
        "timeout" to "Network timeout — connection may be restricted in the background",
        "Task returned false" to "Task completed but reported failure (check debug logs for details)"
    )

    fun explain(error: String?): String? {
        if (error == null) return null
        return explanations.firstOrNull { (pattern, _) ->
            error.contains(pattern, ignoreCase = true)
        }?.second
    }
}
