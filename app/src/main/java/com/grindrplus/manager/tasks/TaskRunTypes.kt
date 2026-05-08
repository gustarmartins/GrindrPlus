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
        "cascade instance" to "App was killed by Android or user hasn't logged in yet",
        "Location provider not initialized" to "Location services aren't ready yet",
        "Location unavailable" to "Android killed background location access; no spoofed location fallback",
        "standby" to "Android killed background location access; no spoofed location fallback",
        "errorCode=500" to "Server error — likely Android battery restrictions on Grindr",
        "HTTP 500" to "Server error — likely Android battery restrictions on Grindr",
        "fetchCascadePage" to "Grindr version incompatibility with the method signature",
        "Unable to find" to "Grindr version incompatibility with the method signature",
        "Wrong number of arguments" to "Version mismatch — cascade parameters changed",
        "Task returned false" to "Task completed but reported failure (check debug logs for details)"
    )

    fun explain(error: String?): String? {
        if (error == null) return null
        return explanations.firstOrNull { (pattern, _) ->
            error.contains(pattern, ignoreCase = true)
        }?.second
    }
}
