package io.github.huidoudour.installer.ui.changelog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.huidoudour.installer.R

/**
 * 更新日志页面
 * 读取构建时由 generateGitLog 生成的 assets/git_commits.md。
 * 参考 app-base 的做法，用轻量文本解析替代昂贵的 Markwon 渲染，避免列表滚动卡顿：
 * 每个提交为一个独立块，块内单行不换行、可独立横向滑动，无文本选择/链接交互。
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

    // 轻量拆分：把 markdown 解析为结构化提交块，替代 Markwon 的整套渲染
    val blocks = remember(mdText) { parseBlocks(mdText) }
    val textColor = MaterialTheme.colorScheme.onSurface

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 懒加载：只组合可见的块；用索引作 key 保证唯一，即使存在内容重复的提交块也不会崩溃
            itemsIndexed(blocks) { index, block ->
                ChangelogBlock(index = index, block = block, textColor = textColor)
            }
        }
    }
}

/**
 * 单个提交块：单行不换行、可横向滑动的文本（无卡片包裹，与参考实现一致）
 */
@Composable
private fun ChangelogBlock(
    index: Int,
    block: MdBlock,
    textColor: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = if (index == 0) 0.dp else 12.dp, bottom = 8.dp)
    ) {
        Text(
            text = block.header,
            fontWeight = FontWeight.Bold,
            color = textColor,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(4.dp))
        block.items.forEach { item ->
            Text(
                text = "• $item",
                color = textColor,
                softWrap = false,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

/** 一个提交块：标题 + 若干正文行 */
private data class MdBlock(val header: String, val items: List<String>)

/**
 * 将整份 markdown 日志按 "### " 提交标题拆分为多个结构化块。
 * 只解析标题与列表项，避免 Markwon 对整份文本做沉重的 Markdown 解析。
 */
private fun parseBlocks(md: String): List<MdBlock> {
    val trimmed = md.trim()
    if (trimmed.isBlank()) return emptyList()

    val blocks = mutableListOf<MdBlock>()
    var header: String? = null
    val items = mutableListOf<String>()

    fun flush() {
        val h = header ?: return
        blocks.add(MdBlock(h, items.toList()))
        items.clear()
        header = null
    }

    for (line in trimmed.lines()) {
        val t = line.trim()
        when {
            t.startsWith("### ") -> {
                flush()
                header = t.removePrefix("### ").trim()
            }

            t.startsWith("- ") -> items.add(t.removePrefix("- ").trim())
            t.isBlank() -> flush()
            else -> items.add(t)
        }
    }
    flush()
    return blocks
}
