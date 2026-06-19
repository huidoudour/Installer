package io.github.huidoudour.Installer.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.util.CommandBookmarks
import io.github.huidoudour.Installer.util.ShellExecutor
import kotlinx.coroutines.launch

/**
 * 终端 Shell 页面 - 类似 Termux 的全屏终端模拟器
 *
 * 功能:
 * - 基于 PTY + TerminalEmulator 的完整终端模拟
 * - 直接键盘输入 (光标闪烁、方向键、Ctrl组合等)
 * - ANSI 颜色/样式渲染
 * - 功能键工具栏
 * - 命令历史/书签/快速命令
 */
@Composable
fun ShellScreen(
    viewModel: ShellViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showQuickCommandsDialog by remember { mutableStateOf(false) }

    // 终端尺寸计算 (增大字符尺寸提升可读性)
    val charWidth = 11f
    val charHeight = 24f
    var terminalWidth by remember { mutableIntStateOf(0) }
    var terminalHeight by remember { mutableIntStateOf(0) }

    // 当终端区域尺寸变化时，更新 PTY 窗口大小
    LaunchedEffect(terminalWidth, terminalHeight) {
        if (terminalWidth > 0 && terminalHeight > 0) {
            val cols = (terminalWidth / charWidth).toInt().coerceAtLeast(20)
            val rows = (terminalHeight / charHeight).toInt().coerceAtLeast(4)
            viewModel.setTerminalSize(rows, cols)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 功能键栏（移至顶部，含保存按钮）
            Spacer(modifier = Modifier.height(4.dp))
            FunctionKeysRow(
                onCtrlC = { viewModel.sendCtrlC() },
                onCtrlD = { viewModel.sendCtrlD() },
                onEsc = { viewModel.sendEscape() },
                onTab = { viewModel.sendTab() },
                onClearScreen = { viewModel.clearScreen() },
                onCopy = { viewModel.copyOutput() },
                onQuickCommands = { showQuickCommandsDialog = true },
                onSave = {
                    val result = viewModel.saveOutput(context)
                    if (result.isSuccess) {
                        val file = result.getOrNull()!!
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                                    context, "${context.packageName}.provider", file
                                ))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_output_title)))
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.share_failed, e.message), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))

            // 终端视图 (填充剩余空间)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .onSizeChanged { size ->
                        terminalWidth = size.width
                        terminalHeight = size.height
                    }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    TerminalView(
                        terminal = viewModel.terminal,
                        onKeyInput = { bytes -> viewModel.sendKeyInput(bytes) },
                        needLocalEcho = viewModel.needLocalEcho,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }
            }

        }
    }

    // 快速命令对话框
    if (showQuickCommandsDialog) {
        QuickCommandsDialog(
            onDismiss = { showQuickCommandsDialog = false },
            onSelectCommand = { cmd ->
                viewModel.sendKeyInput((cmd + "\n").toByteArray(Charsets.UTF_8))
                showQuickCommandsDialog = false
            }
        )
    }
}

@Composable
fun FunctionKeysRow(
    onCtrlC: () -> Unit,
    onCtrlD: () -> Unit,
    onEsc: () -> Unit,
    onTab: () -> Unit,
    onClearScreen: () -> Unit,
    onCopy: () -> Unit,
    onQuickCommands: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FunctionKeyButton(text = stringResource(R.string.btn_shell_cancel), contentDescription = stringResource(R.string.ctrl_c_key), onClick = onCtrlC, textColor = MaterialTheme.colorScheme.error)
            FunctionKeyButton(text = stringResource(R.string.btn_shell_close), contentDescription = stringResource(R.string.ctrl_d_key), onClick = onCtrlD, textColor = MaterialTheme.colorScheme.error)
            FunctionKeyButton(text = stringResource(R.string.btn_shell_esc), contentDescription = stringResource(R.string.escape_key), onClick = onEsc)
            FunctionKeyButton(text = stringResource(R.string.btn_shell_tab), contentDescription = stringResource(R.string.tab_key), onClick = onTab)
            FunctionKeyButton(text = stringResource(R.string.btn_shell_clear), contentDescription = stringResource(R.string.clear_screen), onClick = onClearScreen, textColor = MaterialTheme.colorScheme.error)
            FunctionKeyButton(text = stringResource(R.string.btn_shell_paste), contentDescription = stringResource(R.string.clipboard_action), onClick = onCopy)
            FunctionKeyButton(text = stringResource(R.string.btn_shell_quick), contentDescription = stringResource(R.string.quick_commands_action), onClick = onQuickCommands)
            FunctionKeyButton(text = stringResource(R.string.btn_shell_save), contentDescription = stringResource(R.string.content_description_save), onClick = onSave)
        }
    }
}

@Composable
fun FunctionKeyButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(36.dp)
            .width(if (text.length > 2) 54.dp else 44.dp),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = textColor
        )
    ) {
        Text(
            text = text,
            fontSize = if (text.length > 2) 11.sp else 16.sp
        )
    }
}

// ============ Dialogs ============

@Composable
fun ShellHistoryDialog(
    onDismiss: () -> Unit,
    onSelectCommand: (String) -> Unit
) {
    val history = remember { ShellExecutor.CommandHistory.getAll() }
    val reversedHistory = remember(history) { history.reversed() }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.content_description_history),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_command_history),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(reversedHistory) { cmd ->
                        Text(
                            text = cmd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCommand(cmd) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun ShellBookmarksDialog(
    context: android.content.Context,
    onDismiss: () -> Unit,
    onSelectCommand: (String) -> Unit
) {
    var bookmarks by remember { mutableStateOf(CommandBookmarks.getBookmarks(context)) }
    var showAddDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.content_description_bookmarks),
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_bookmark_action))
                }
            }
        },
        text = {
            if (bookmarks.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_bookmarks_saved),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(bookmarks) { cmd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCommand(cmd) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cmd,
                                modifier = Modifier.weight(1f),
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            IconButton(
                                onClick = {
                                    CommandBookmarks.removeBookmark(context, cmd)
                                    bookmarks = CommandBookmarks.getBookmarks(context)
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.remove_bookmark_action),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )

    if (showAddDialog) {
        var newBookmark by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text(stringResource(R.string.add_bookmark_title), fontWeight = FontWeight.SemiBold) },
            text = {
                BasicTextField(
                    value = newBookmark,
                    onValueChange = { newBookmark = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (newBookmark.isEmpty()) {
                                Text(
                                    stringResource(R.string.enter_command_bookmark_hint),
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newBookmark.isNotBlank()) {
                        CommandBookmarks.addBookmark(context, newBookmark.trim())
                        bookmarks = CommandBookmarks.getBookmarks(context)
                    }
                    showAddDialog = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun QuickCommandsDialog(
    onDismiss: () -> Unit,
    onSelectCommand: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.quick_commands),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                val commands = ShellExecutor.QuickCommands.COMMANDS
                val names = ShellExecutor.QuickCommands.COMMAND_NAMES
                items(commands.indices.toList()) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCommand(commands[index]) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = names[index],
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = commands[index],
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
