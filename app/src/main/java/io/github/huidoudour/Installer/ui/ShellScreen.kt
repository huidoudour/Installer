package io.github.huidoudour.Installer.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.huidoudour.Installer.ui.theme.SmallShape
import io.github.huidoudour.Installer.util.CommandBookmarks
import io.github.huidoudour.Installer.util.ShellExecutor
import kotlinx.coroutines.launch

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

    var showHistoryDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showQuickCommandsDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    LaunchedEffect(viewModel.outputLineCount) {
        if (!viewModel.userScrolledAwayFromBottom) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top toolbar
        TopToolbar(
            onHistoryClick = { showHistoryDialog = true },
            onBookmarksClick = { showBookmarksDialog = true },
            onSearchClick = { viewModel.toggleSearch() },
            onSaveClick = {
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

        // Search bar
        if (isSearchVisible) {
            SearchInput(
                searchText = searchText,
                onSearchTextChange = { viewModel.updateSearchText(it) },
                onClose = { viewModel.toggleSearch() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Terminal output (fills remaining space)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isSearchVisible && searchText.isNotEmpty()) {
                        val searchTextValue = viewModel.searchResult.value
                        val displayStr = searchTextValue.ifEmpty { outputText }
                        androidx.compose.material3.Text(
                            text = displayStr,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .verticalScroll(scrollState),
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    } else {
                        androidx.compose.material3.Text(
                            text = viewModel.getAnnotatedOutput(),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .verticalScroll(scrollState),
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }

                    // Scroll buttons overlay
                    if (scrollState.maxValue > 0) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    kotlinx.coroutines.MainScope().launch {
                                        scrollState.animateScrollTo(0)
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = CircleShape
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.scroll_to_top),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    kotlinx.coroutines.MainScope().launch {
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                    viewModel.userScrolledAwayFromBottom = false
                                },
                                modifier = Modifier.size(32.dp),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = CircleShape
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.scroll_to_bottom),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Function keys
        FunctionKeysRow(
            onHistoryUp = { viewModel.navigateHistoryUp() },
            onHistoryDown = { viewModel.navigateHistoryDown() },
            onTab = { viewModel.insertTab() },
            onCtrlC = { viewModel.cancelCommand() },
            onEsc = { viewModel.clearInput() },
            onClearScreen = { viewModel.clearScreen() },
            onCopy = { viewModel.copyOutput() },
            onQuickCommands = { showQuickCommandsDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Command input at bottom
        CommandInput(
            commandText = commandText,
            onCommandTextChange = { viewModel.updateCommandText(it) },
            onSendCommand = { viewModel.executeCommand() },
            isExecuting = isExecuting
        )
    }

    // History dialog
    if (showHistoryDialog) {
        ShellHistoryDialog(
            onDismiss = { showHistoryDialog = false },
            onSelectCommand = { cmd ->
                viewModel.updateCommandText(cmd)
                showHistoryDialog = false
            }
        )
    }

    // Bookmarks dialog
    if (showBookmarksDialog) {
        ShellBookmarksDialog(
            context = context,
            onDismiss = { showBookmarksDialog = false },
            onSelectCommand = { cmd ->
                viewModel.updateCommandText(cmd)
                showBookmarksDialog = false
            }
        )
    }

    // Quick commands dialog
    if (showQuickCommandsDialog) {
        QuickCommandsDialog(
            onDismiss = { showQuickCommandsDialog = false },
            onSelectCommand = { cmd ->
                viewModel.updateCommandText(cmd)
                showQuickCommandsDialog = false
            }
        )
    }
}

@Composable
fun TopToolbar(
    onHistoryClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolbarButton(
                iconId = R.drawable.ic_shell_history,
                contentDescription = stringResource(R.string.content_description_history),
                onClick = onHistoryClick
            )
            ToolbarButton(
                iconId = R.drawable.ic_shell_bookmark,
                contentDescription = stringResource(R.string.content_description_bookmarks),
                onClick = onBookmarksClick
            )
            ToolbarButton(
                iconId = R.drawable.ic_shell_search,
                contentDescription = stringResource(R.string.content_description_search),
                onClick = onSearchClick
            )
            ToolbarButton(
                iconId = R.drawable.ic_shell_save,
                contentDescription = stringResource(R.string.content_description_save),
                onClick = onSaveClick
            )
        }
    }
}

@Composable
fun ToolbarButton(
    iconId: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = ImageVector.vectorResource(iconId),
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
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
                                text = stringResource(R.string.search_in_output_hint),
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
                    contentDescription = stringResource(R.string.close_search)
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
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
            FunctionKeyButton(text = stringResource(R.string.up_arrow), contentDescription = stringResource(R.string.up_direction), onClick = onHistoryUp)
            FunctionKeyButton(text = stringResource(R.string.down_arrow), contentDescription = stringResource(R.string.down_direction), onClick = onHistoryDown)
            FunctionKeyButton(text = stringResource(R.string.tab), contentDescription = stringResource(R.string.tab_key), onClick = onTab)
            FunctionKeyButton(text = stringResource(R.string.ctrl_c), contentDescription = stringResource(R.string.ctrl_c_key), onClick = onCtrlC,
                textColor = MaterialTheme.colorScheme.error)
            FunctionKeyButton(text = stringResource(R.string.esc), contentDescription = stringResource(R.string.escape_key), onClick = onEsc)
            FunctionKeyButton(text = stringResource(R.string.letter_c), contentDescription = stringResource(R.string.clear_screen), onClick = onClearScreen)
            FunctionKeyButton(text = stringResource(R.string.clipboard), contentDescription = stringResource(R.string.clipboard_action), onClick = onCopy)
            FunctionKeyButton(text = stringResource(R.string.lightning), contentDescription = stringResource(R.string.quick_commands_action), onClick = onQuickCommands)
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

@Composable
fun CommandInput(
    commandText: String,
    onCommandTextChange: (String) -> Unit,
    onSendCommand: () -> Unit,
    isExecuting: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        BasicTextField(
            value = commandText,
            onValueChange = onCommandTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSendCommand() }),
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

// ============ Dialogs ============

@Composable
fun ShellHistoryDialog(
    onDismiss: () -> Unit,
    onSelectCommand: (String) -> Unit
) {
    val history = remember { ShellExecutor.CommandHistory.getAll() }

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
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(history.reversed()) { cmd ->
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
