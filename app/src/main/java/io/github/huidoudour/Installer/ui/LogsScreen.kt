package io.github.huidoudour.Installer.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.ui.theme.SmallShape
import io.github.huidoudour.Installer.util.LogEntry
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LogsScreen(
    viewModel: LogsViewModel = viewModel()
) {
    val context = LocalContext.current
    val logs: List<LogEntry> by viewModel.logs.collectAsState()
    val logCount: Int by viewModel.logCount.collectAsState()

    val listState = rememberLazyListState()
    var userScrolledAway by remember { mutableStateOf(false) }
    var isInitialScrollDone by remember { mutableStateOf(false) }

    // 初始加载时滚动到底部
    LaunchedEffect(Unit) {
        if (logs.isNotEmpty()) {
            listState.scrollToItem(logs.size - 1)
            isInitialScrollDone = true
        }
    }

    // 新日志到来时自动滚动到底部，除非用户手动滚开了
    LaunchedEffect(logs) {
        if (logs.isNotEmpty() && isInitialScrollDone) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .collectLatest { lastVisibleIndex ->
                    val currentLastVisible = lastVisibleIndex ?: 0
                    val isAtBottom = currentLastVisible >= logs.size - 1
                    userScrolledAway = !isAtBottom
                }
        }
    }

    // 自动跟随滚动
    LaunchedEffect(logs.size, userScrolledAway) {
        if (logs.isNotEmpty() && !userScrolledAway && isInitialScrollDone) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        // Page title + action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.full_log),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Row {
                // 清除按钮
                Surface(
                    shape = SmallShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = {
                        viewModel.clearLogs()
                        userScrolledAway = false
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.clear),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 导出按钮
                Surface(
                    shape = SmallShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = {
                        val result = viewModel.exportLogs()
                        result.onSuccess { file ->
                            try {
                                val shareIntent = viewModel.shareLogFile(file)
                                context.startActivity(
                                    Intent.createChooser(shareIntent, context.getString(R.string.export_log))
                                )
                                Toast.makeText(context, context.getString(R.string.log_exported, file.name), Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
                            }
                        }.onFailure { error ->
                            Toast.makeText(context, context.getString(R.string.export_failed, error.message), Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.export),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header: title + count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.real_time_logs),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (logs.isNotEmpty()) context.getString(R.string.log_entries_count, logCount) else "",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        shape = RoundedCornerShape(0.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentWidth()
                                .padding(12.dp),
                            state = listState
                        ) {
                            items(
                                items = logs,
                                key = { log -> log.id }
                            ) { log ->
                                LogEntryItem(log = log.text)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = log,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = levelColor,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}
