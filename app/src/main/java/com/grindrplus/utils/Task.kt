package com.grindrplus.utils

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import com.grindrplus.core.logd
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.core.logw
import kotlinx.coroutines.Job

abstract class Task(
    val id: String,
    val description: String,
    val initialDelayMillis: Long = 30 * 1000, // 30 seconds
    val intervalMillis: Long = 10 * 60 * 1000 // 10 minutes
) {
    private var job: Job? = null

    /**
     * Check if the task is enabled in config
     */
    fun isTaskEnabled(): Boolean {
        return Config.isTaskEnabled(id)
    }

    /**
     * Override this method to implement task-specific logic.
     * Return true if the task executed successfully, false otherwise.
     */
    abstract suspend fun execute(): Boolean

    /**
     * Start the task if it's enabled in config
     */
    fun start() {
        if (!isTaskEnabled()) {
            logi("Task $id is disabled")
            return
        }

        job = GrindrPlus.taskManager.startPeriodicTask(
            taskId = id,
            initialDelayMillis = initialDelayMillis,
            intervalMillis = intervalMillis,
            action = {
                try {
                    val success = execute()
                    if (success) {
                        logd("Task $id executed successfully")
                    } else {
                        logw("Task $id run was unsuccessful")
                    }
                } catch (e: Exception) {
                    loge("Task $id failed: ${e.message}")
                    Logger.writeRaw(e.stackTraceToString())
                }
            }
        )

        logi("Task $id started")
    }

    /**
     * Stop the task
     */
    fun stop() {
        job?.let {
            if (GrindrPlus.taskManager.isTaskRunning(id)) {
                GrindrPlus.taskManager.cancelTask(id)
                logi("Task $id stopped")
            }
        }
        job = null
    }

    /**
     * Called when task is first registered
     */
    open fun register() {
        Config.initTaskSettings(
            id,
            description,
            false // disabled by default
        )
    }
}