package io.github.huidoudour.installer.ui.changelog

import android.widget.TextView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.huidoudour.installer.R
import io.noties.markwon.Markwon

/**
 * 更新日志页面
 * 读取构建时由 generateGitLog 生成的 assets/git_commits.md，并通过 Markwon 渲染为 Markdown。
 * 每个提交为一个独立块（卡片），块内单行不换行、可独立横向滑动，不显示文本选择/链接等互动条。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    // 读取构建期生成的 Git 提交日志（最新提交在最上方）
    val mdText = remember {
        try {
            context.assets.open("git_commits.md")
                .bufferedReader()
                .use { it.readText() }
        } catch (_: Exception) {
            context.getString(R.string.changelog_empty)
        }
    }

    val markwon = remember { Markwon.create(context) }
    val textColor = MaterialTheme.colorScheme.onSurface

    // 每个以 "### " 开头的提交拆分为一个独立块
    val blocks = remember(mdText) { parseBlocks(mdText) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.changelog_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                ChangelogBlock(md = block, markwon = markwon, textColor = textColor)
            }
        }
    }
}

/**
 * 单个提交块：卡片内一个单行、可横向滑动且不换行的 Markdown 渲染视图
 */
@Composable
private fun ChangelogBlock(
    md: String,
    markwon: Markwon,
    textColor: Color
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        AndroidView(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            factory = { ctx ->
                TextView(ctx).apply {
                    // 保留多行换行显示；每行文字超出屏幕宽度时不自动折行，
                    // 而是靠外层 horizontalScroll 横向滑动查看完整的一行
                    setHorizontallyScrolling(true)
                    setTextColor(textColor.toArgb())
                    textSize = 15f
                    setTextIsSelectable(false)
                    setText(markwon.toMarkdown(md))
                }
            }
        )
    }
}

/**
 * 将整份 markdown 日志按 "### " 提交标题拆分为多个独立块
 */
private fun parseBlocks(md: String): List<String> {
    val trimmed = md.trim()
    if (trimmed.isBlank()) return emptyList()
    return trimmed.split(Regex("(?m)(?=^### )"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
