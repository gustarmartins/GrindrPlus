package com.grindrplus.manager.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindrplus.GrindrPlus
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import com.grindrplus.manager.ui.components.FilterTimeRange
import com.grindrplus.manager.ui.components.TaskLogFilters
import com.grindrplus.manager.utils.FileOperationHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.grindrplus.core.Config
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskRunViewModel : ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableStateFlow<List<TaskRunEvent>>(emptyList())
    val events: StateFlow<List<TaskRunEvent>> = _events.asStateFlow()

    private val _filters = MutableStateFlow(TaskLogFilters())
    val filters: StateFlow<TaskLogFilters> = _filters.asStateFlow()

    private val _filteredEvents = MutableStateFlow<List<TaskRunEvent>>(emptyList())
    val filteredEvents: StateFlow<List<TaskRunEvent>> = _filteredEvents.asStateFlow()

    private val _taskHealthScores = MutableStateFlow<Map<String, Float>>(emptyMap())
    val taskHealthScores: StateFlow<Map<String, Float>> = _taskHealthScores.asStateFlow()

    init {
        viewModelScope.launch {
            events.collect { applyFilters() }
        }

        viewModelScope.launch {
            filters.collect { applyFilters() }
        }
    }

    private fun applyFilters() {
        val currentFilters = _filters.value
        val allEvents = _events.value

        _filteredEvents.value = allEvents
            .filter { event ->
                if (event.success) currentFilters.showSuccess else currentFilters.showFailures
            }
            .applyTimeFilter(currentFilters.timeRange)
        
        calculateHealthScores()
    }

    private fun calculateHealthScores() {
        val allEvents = _events.value
        val scores = mutableMapOf<String, Float>()
        
        val groupedByTask = allEvents.groupBy { it.taskId }
        
        for ((taskId, runs) in groupedByTask) {
            if (runs.size < 2) {
                scores[taskId] = 1.0f
                continue
            }
            
            val expectedIntervalMs = when(taskId) {
                "Always Online" -> (Config.get("always_online_interval_mins", 15) as Number).toLong() * 60 * 1000
                else -> 10 * 60 * 1000L
            }
            
            val sortedRuns = runs.sortedBy { it.timestamp }
            var totalRatio = 0.0
            var count = 0
            
            for (i in 1 until sortedRuns.size) {
                val diffMs = sortedRuns[i].timestamp - sortedRuns[i - 1].timestamp
                if (diffMs <= 0) continue
                
                val ratio = if (diffMs <= expectedIntervalMs + 10_000) 1.0 else (expectedIntervalMs.toDouble() / diffMs.toDouble())
                totalRatio += ratio.coerceIn(0.0, 1.0)
                count++
            }
            
            if (count > 0) {
                scores[taskId] = (totalRatio / count).toFloat()
            }
        }
        _taskHealthScores.value = scores
    }

    fun updateFilters(newFilters: TaskLogFilters) {
        _filters.value = newFilters
    }

    fun loadRuns() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = _events.value.isEmpty()
            try {
                val runsArray = GrindrPlus.bridgeClient.getTaskRuns()
                val runsList = mutableListOf<TaskRunEvent>()

                for (i in 0 until runsArray.length()) {
                    val run = runsArray.getJSONObject(i)
                    runsList.add(
                        TaskRunEvent(
                            taskId = run.getString("taskId"),
                            success = run.getBoolean("success"),
                            error = if (run.isNull("error")) null else run.getString("error"),
                            timestamp = run.getLong("timestamp"),
                            durationMs = run.getLong("durationMs")
                        )
                    )
                }

                runsList.sortByDescending { it.timestamp }
                _events.value = runsList
            } catch (e: Exception) {
                Logger.e("Failed to load task runs: ${e.message}", LogSource.MANAGER)
                Logger.writeRaw(e.stackTraceToString())
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearRuns() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                GrindrPlus.bridgeClient.clearTaskRuns()
                loadRuns()
            } catch (e: Exception) {
                Logger.e("Failed to clear task runs: ${e.message}", LogSource.MANAGER)
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }

    fun exportRunsJson(): String {
        val runsToExport = _filteredEvents.value.ifEmpty { _events.value }
        val jsonArray = JSONArray()

        runsToExport.forEach { run ->
            val jsonObject = JSONObject()
            jsonObject.put("taskId", run.taskId)
            jsonObject.put("success", run.success)
            jsonObject.put("error", run.error ?: JSONObject.NULL)
            jsonObject.put("timestamp", run.timestamp)
            jsonObject.put("durationMs", run.durationMs)
            jsonArray.put(jsonObject)
        }

        return jsonArray.toString(4)
    }

    fun exportRunsToFile(context: Context) {
        val jsonContent = exportRunsJson()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "task_runs_$timestamp.json"
        FileOperationHandler.exportFile(filename, jsonContent)
    }
}

private fun List<TaskRunEvent>.applyTimeFilter(timeRange: FilterTimeRange): List<TaskRunEvent> {
    if (timeRange == FilterTimeRange.ALL_TIME) return this

    val currentTime = System.currentTimeMillis()
    val cutoffTime = when (timeRange) {
        FilterTimeRange.LAST_24H -> currentTime - (24 * 60 * 60 * 1000)
        FilterTimeRange.LAST_WEEK -> currentTime - (7 * 24 * 60 * 60 * 1000)
        FilterTimeRange.LAST_MONTH -> currentTime - (30L * 24 * 60 * 60 * 1000)
        else -> 0
    }

    return this.filter { it.timestamp >= cutoffTime }
}
