package io.github.huidoudour.Installer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Material 3 分段圆角常量
val CornerRadius = 16.dp
val ConnectionRadius = 5.dp

// 列表项形状（顶部、中间、底部、单独）- 用于分段列表
val topShape = RoundedCornerShape(
    topStart = CornerRadius,
    topEnd = CornerRadius,
    bottomStart = ConnectionRadius,
    bottomEnd = ConnectionRadius
)
val middleShape = RoundedCornerShape(ConnectionRadius)
val bottomShape = RoundedCornerShape(
    topStart = ConnectionRadius,
    topEnd = ConnectionRadius,
    bottomStart = CornerRadius,
    bottomEnd = CornerRadius
)
val singleShape = RoundedCornerShape(CornerRadius)

// 标准卡片圆角
val CardCornerRadius = 28.dp
val CardShape = RoundedCornerShape(CardCornerRadius)

// 按钮圆角
val ButtonCornerRadius = 20.dp
val ButtonShape = RoundedCornerShape(ButtonCornerRadius)

// 小元素圆角
val SmallCornerRadius = 12.dp
val SmallShape = RoundedCornerShape(SmallCornerRadius)

// 浮动按钮圆角
val FABCornerRadius = 16.dp
val FABShape = RoundedCornerShape(FABCornerRadius)

/**
 * 根据列表项位置获取对应的圆角形状
 */
@Composable
fun segmentedShape(index: Int, totalItems: Int): androidx.compose.ui.graphics.Shape {
    return when {
        totalItems == 1 -> singleShape
        index == 0 -> topShape
        index == totalItems - 1 -> bottomShape
        else -> middleShape
    }
}

/**
 * 分段列表项之间的间隙
 */
val SegmentedGap: Dp = 4.dp
