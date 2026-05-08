package com.grindrplus.manager.ui

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grindrplus.manager.tasks.TaskErrorExplainer
import com.grindrplus.manager.tasks.TaskRunEvent
import com.grindrplus.manager.tasks.TaskRunViewModel
import com.grindrplus.manager.ui.components.TaskFilterPanel
import com.grindrplus.manager.ui.components.TaskLogFilters
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksScreen(innerPadding: PaddingValues) {
    val activity = LocalContext.current as ComponentActivity
    val viewModel: TaskRunViewModel = viewModel()
    val events by viewModel.events.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val filteredEvents by viewModel.filteredEvents.collectAsState()
    val taskHealthScores by viewModel.taskHealthScores.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadRuns()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Clear all task runs") },
            text = { Text("Are you sure you want to clear all task run history? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearRuns()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        viewModel.exportRunsToFile(activity)
                        scope.launch {
                            snackbarHostState.showSnackbar("Saving task runs JSON file...")
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Export JSON"
                    )
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear all"
                    )
                }
            }

            TaskFilterPanel(
                filters = filters,
                onFiltersChanged = { newFilters ->
                    viewModel.updateFilters(newFilters)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )



            Box(
                modifier = Modifier.weight(1f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (filteredEvents.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "No runs",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (filters.isActive && events.isNotEmpty())
                                "No runs match your filters"
                            else
                                "No task runs recorded yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (filters.isActive && events.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.updateFilters(TaskLogFilters()) }
                            ) {
                                Text("Reset Filters")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            if (taskHealthScores.isNotEmpty()) {
                                LazyRow(
                                    contentPadding = PaddingValues(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(taskHealthScores.toList()) { (taskId, score) ->
                                        TaskHealthCard(taskId = taskId, score = score)
                                    }
                                }
                            }

                            if (filters.isActive) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Filtered: ${filteredEvents.size} of ${events.size} runs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                        val grouped = filteredEvents.groupBy {
                            dateFormat.format(Date(it.timestamp))
                        }

                        grouped.forEach { (date, dateEvents) ->
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            items(dateEvents) { event ->
                                TaskRunItem(event = event)
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
fun TaskRunItem(event: TaskRunEvent) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val time = timeFormat.format(Date(event.timestamp))

    val durationText = remember(event.durationMs) {
        when {
            event.durationMs < 1000 -> "${event.durationMs}ms"
            event.durationMs < 60_000 -> "%.1fs".format(event.durationMs / 1000.0)
            else -> "${event.durationMs / 60_000}m ${(event.durationMs % 60_000) / 1000}s"
        }
    }

    val explanation = remember(event.error) {
        TaskErrorExplainer.explain(event.error)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (event.success)
                        Icons.Default.CheckCircle
                    else
                        Icons.Default.Error,
                    contentDescription = null,
                    tint = if (event.success)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.size(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = event.taskId,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = if (event.success) "Completed successfully" else "Failed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (!event.success && event.error != null) {
                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = event.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )

                if (explanation != null) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = explanation,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskHealthCard(taskId: String, score: Float) {
    val scorePercentage = (score * 100).toInt()
    val color = when {
        score >= 0.8f -> MaterialTheme.colorScheme.primary
        score >= 0.5f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    
    Card(
        modifier = Modifier.padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = taskId, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { score },
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surface,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "$scorePercentage%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}
