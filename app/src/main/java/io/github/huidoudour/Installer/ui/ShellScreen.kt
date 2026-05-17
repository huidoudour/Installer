package io.github.huidoudour.Installer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.ui.theme.SmallShape

@Composable
fun ShellScreen(
    viewModel: ShellViewModel = viewModel()
) {
    val context = LocalContext.current

    val outputText: String by viewModel.outputText.collectAsState()
    val commandText: String by viewModel.commandText.collectAsState()
    val isExecuting: Boolean by viewModel.isExecuting.collectAsState()
    val isSearchVisible: Boolean by viewModel.isSearchVisible.collectAsState()
    val searchText: String by viewModel.searchText.collectAsState()

    val scrollState = rememberScrollState()
    val outputScrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 页面标题
        Text(
            text = stringResource(R.string.title_shell),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        // 顶部工具栏
        TopToolbar(
            onHistoryClick = { /* 显示历史 */ },
            onBookmarksClick = { /* 显示书签 */ },
            onSearchClick = { viewModel.toggleSearch() },
            onSaveClick = { /* 保存输出 */ }
        )

        // 搜索框
        if (isSearchVisible) {
            SearchInput(
                searchText = searchText,
                onSearchTextChange = { viewModel.updateSearchText(it) },
                onClose = { viewModel.toggleSearch() }
            )
        }

        // 命令输入区域 - 移到顶部
        CommandInput(
            commandText = commandText,
            onCommandTextChange = { viewModel.updateCommandText(it) },
            onSendCommand = { viewModel.executeCommand() },
            isExecuting = isExecuting
        )

        // 功能键容器 - 移到顶部
        FunctionKeysRow(
            onHistoryUp = { viewModel.navigateHistoryUp() },
            onHistoryDown = { viewModel.navigateHistoryDown() },
            onTab = { viewModel.insertTab() },
            onCtrlC = { viewModel.cancelCommand() },
            onEsc = { viewModel.clearInput() },
            onClearScreen = { viewModel.clearScreen() },
            onCopy = { viewModel.copyOutput() },
            onQuickCommands = { /* 快捷命令 */ }
        )

        // 终端输出区域 - 移到底部，占据剩余空间
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = SmallShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = if (outputText.isEmpty()) "$ " else outputText,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(outputScrollState),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

@Composable
fun TopToolbar(
    onHistoryClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    // 使用更轻量的包裹样式
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolbarButton(
                icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_shell_history),
                contentDescription = stringResource(R.string.content_description_history),
                onClick = onHistoryClick
            )
            ToolbarButton(
                icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_shell_bookmark),
                contentDescription = stringResource(R.string.content_description_bookmarks),
                onClick = onBookmarksClick
            )
            ToolbarButton(
                icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_shell_search),
                contentDescription = stringResource(R.string.content_description_search),
                onClick = onSearchClick
            )
            ToolbarButton(
                icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_shell_save),
                contentDescription = stringResource(R.string.content_description_save),
                onClick = onSaveClick
            )
        }
    }
}

@Composable
fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}

@Composable
fun SearchInput(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClose: () -> Unit
) {
    // 使用更轻量的包裹样式
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchText.isEmpty()) {
                            Text(
                                text = "Search in output...",
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

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close search"
                )
            }
        }
    }
}

@Composable
fun FunctionKeysRow(
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
    onTab: () -> Unit,
    onCtrlC: () -> Unit,
    onEsc: () -> Unit,
    onClearScreen: () -> Unit,
    onCopy: () -> Unit,
    onQuickCommands: () -> Unit
) {
    // 使用 MD3 风格的包裹容器
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FunctionKeyButton(text = "↑", onClick = onHistoryUp)
            FunctionKeyButton(text = "↓", onClick = onHistoryDown)
            FunctionKeyButton(text = "TAB", onClick = onTab)
            FunctionKeyButton(text = "^C", onClick = onCtrlC, textColor = MaterialTheme.colorScheme.error)
            FunctionKeyButton(text = "ESC", onClick = onEsc)
            FunctionKeyButton(text = "C", onClick = onClearScreen)
            FunctionKeyButton(text = stringResource(R.string.clipboard), onClick = onCopy)
            FunctionKeyButton(text = stringResource(R.string.lightning), onClick = onQuickCommands)
        }
    }
}

@Composable
fun FunctionKeyButton(
    text: String,
    onClick: () -> Unit,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer
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

@Composable
fun CommandInput(
    commandText: String,
    onCommandTextChange: (String) -> Unit,
    onSendCommand: () -> Unit,
    isExecuting: Boolean
) {
    // 使用 MD3 风格的包裹容器
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        BasicTextField(
            value = commandText,
            onValueChange = onCommandTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            enabled = !isExecuting,
            decorationBox = { innerTextField ->
                Box {
                    if (commandText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.shell_hint),
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
