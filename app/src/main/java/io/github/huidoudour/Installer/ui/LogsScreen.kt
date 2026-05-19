package io.github.huidoudour.Installer.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.ui.theme.SmallShape

@Composable
fun LogsScreen(
    viewModel: LogsViewModel = viewModel()
) {
    val context = LocalContext.current
    val logs: List<String> by viewModel.logs.collectAsState()
    val logCount: Int by viewModel.logCount.collectAsState()

    val listState = rememberLazyListState()
    var userScrolledAway by remember { mutableStateOf(false) }
    val previousLogCount = remember { mutableStateOf(logCount) }

    // Auto-scroll to bottom on new logs, unless user scrolled away
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            if (previousLogCount.value != logCount && !userScrolledAway) {
                listState.animateScrollToItem(logs.size - 1)
            }
            previousLogCount.value = logCount
        }
    }

    // Detect user scroll
    LaunchedEffect(listState.firstVisibleItemIndex, listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index) {
        if (logs.isNotEmpty()) {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            userScrolledAway = lastVisibleItem < logs.size - 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Page title
        Text(
            text = stringResource(R.string.full_log),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Log card
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = SmallShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header: subtitle + log count, clear + export buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.real_time_logs),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (logs.isNotEmpty()) "$logCount entries" else "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Row {
                        TextButton(onClick = {
                            viewModel.clearLogs()
                            userScrolledAway = false
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.clear))
                        }
                        TextButton(onClick = {
                            val result = viewModel.exportLogs()
                            result.onSuccess { file ->
                                try {
                                    val shareIntent = viewModel.shareLogFile(file)
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Export Log")
                                    )
                                    Toast.makeText(context, "Log exported: ${file.name}", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }.onFailure { error ->
                                Toast.makeText(context, "Export failed: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.export))
                        }
                    }
                }

                // Log list
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.no_logs_available),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        shape = SmallShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            state = listState
                        ) {
                            items(logs) { log ->
                                LogEntryItem(log = log)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(log: String) {
    val levelColor = when {
        log.contains("ERROR") -> MaterialTheme.colorScheme.error
        log.contains("WARN") -> MaterialTheme.colorScheme.tertiary
        log.contains("INFO") -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = log,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        ),
        color = levelColor,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
