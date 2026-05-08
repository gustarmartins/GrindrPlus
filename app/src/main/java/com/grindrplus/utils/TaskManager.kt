package com.grindrplus.utils

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import com.grindrplus.core.TaskScheduler
import com.grindrplus.tasks.AlwaysOnline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

class TaskManager(private val scheduler: TaskScheduler? = null) {
    private val tasks = mutableMapOf<KClass<out Task>, Task>()

    fun registerTasks(startTasks: Boolean = true) {
        runBlocking(Dispatchers.IO) {
            val taskList = listOf(
                AlwaysOnline(),
            )

            taskList.forEach { task ->
                task.register()
            }

            tasks.clear()
            taskList.forEach { task ->
                tasks[task::class] = task

                if (startTasks && task.isTaskEnabled()) {
                    task.start()
                    Logger.i("Started task: ${task.id}", LogSource.MODULE)
                } else if (!startTasks) {
                    Logger.i("Registered task: ${task.id}", LogSource.MODULE)
                } else {
                    Logger.i("Task ${task.id} is disabled", LogSource.MODULE)
                }
            }
        }
    }

    fun reloadTasks() {
        runBlocking(Dispatchers.IO) {
            tasks.values.forEach { task -> task.stop() }
            tasks.clear()
            registerTasks()
            Logger.s("Reloaded tasks", LogSource.MODULE)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Task> getTask(taskClass: KClass<T>): T? {
        return tasks[taskClass] as? T
    }

    fun toggleTask(taskId: String, enabled: Boolean) {
        val task = tasks.values.find { it.id == taskId } ?: return

        Config.setTaskEnabled(taskId, enabled)

        if (enabled) {
            task.start()
        } else {
            task.stop()
        }
    }

    fun stopAllTasks() {
        tasks.values.forEach { it.stop() }
    }

    fun startPeriodicTask(
        taskId: String,
        initialDelayMillis: Long,
        intervalMillis: Long,
        action: suspend () -> Unit
    ): Job {
        val scheduler = scheduler ?: error("TaskScheduler is not initialized")

        if (scheduler.isTaskRunning(taskId)) {
            scheduler.cancelTask(taskId)
        }

        return scheduler.periodic(
            name = taskId,
            intervalMs = intervalMillis
        ) {
            if (initialDelayMillis > 0) {
                delay(initialDelayMillis)
            }
            action()
        }
    }

    fun startOnceTask(taskId: String, action: suspend () -> Unit): Job {
        val scheduler = scheduler ?: error("TaskScheduler is not initialized")
        return scheduler.once(taskId, action)
    }

    fun startTaskWithRetry(
        taskId: String,
        retries: Int = 3,
        delayMs: Long = 1000,
        action: suspend () -> Unit
    ): Job {
        val scheduler = scheduler ?: error("TaskScheduler is not initialized")
        return scheduler.withRetry(taskId, retries, delayMs, action)
    }

    fun triggerTask(taskId: String) {
        val task = tasks.values.find { it.id == taskId } ?: return
        GrindrPlus.executeAsync {
            val startTime = System.currentTimeMillis()
            try {
                Logger.i("Manually triggering task: ${task.id}", LogSource.MODULE)
                val success = task.execute()
                val durationMs = System.currentTimeMillis() - startTime
                try {
                    GrindrPlus.bridgeClient.logTaskRun(
                        task.id, success,
                        if (!success) (task.lastError ?: "Task returned false") else null,
                        durationMs
                    )
                } catch (_: Exception) {}
                if (success) {
                    Logger.s("Manual run of task ${task.id} succeeded", LogSource.MODULE)
                    GrindrPlus.showToast(android.widget.Toast.LENGTH_SHORT, "Task ${task.id} succeeded")
                } else {
                    Logger.w("Manual run of task ${task.id} failed", LogSource.MODULE)
                    GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, "Task ${task.id} failed. Check logs.")
                }
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTime
                try {
                    GrindrPlus.bridgeClient.logTaskRun(task.id, false, e.message, durationMs)
                } catch (_: Exception) {}
                Logger.e("Manual run of task ${task.id} failed with error: ${e.message}", LogSource.MODULE)
                Logger.writeRaw(e.stackTraceToString())
                GrindrPlus.showToast(android.widget.Toast.LENGTH_LONG, "Task ${task.id} failed: ${e.message}")
            }
        }
    }

    fun isTaskRunning(taskId: String): Boolean {
        return scheduler?.isTaskRunning(taskId) ?: false
    }

    fun cancelTask(taskId: String) {
        scheduler?.cancelTask(taskId)
    }
}