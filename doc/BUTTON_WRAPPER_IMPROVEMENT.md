# 按钮包裹样式美化方案

## 问题分析

当前项目的按钮直接放置在布局中，缺少合适的包裹容器，导致视觉效果不够统一和美观。

## 参考方案（InstallerX-Revived）

参考仓库采用了以下优秀实践：

### 1. 按钮容器设计
```kotlin
Column(
    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
    verticalArrangement = Arrangement.spacedBy(4.dp)
) {
    // 按钮内容
}
```

**特点：**
- 外层容器使用 12dp 圆角
- 按钮之间保持 4dp 间距
- 整体视觉更加统一

### 2. 单双按钮布局策略

```kotlin
// 计算单个按钮数量（奇数时第一个单独显示）
val single = if (buttons.size > 2) buttons.size % 2 else buttons.size

// 渲染单个按钮（顶部区域）
for (i in 0 until single) {
    InnerButton(buttons[i])
}

// 渲染成对按钮（底部区域）
for (i in single until buttons.size step 2) {
    Box {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.height(IntrinsicSize.Max)  // 关键：确保等高
        ) {
            // 左右两个按钮
        }
    }
}
```

**优势：**
- 单个按钮全宽显示，更醒目
- 成对按钮等高显示，更整齐
- 使用 `IntrinsicSize.Max` 确保同行按钮高度一致

### 3. 按钮样式设计

```kotlin
TextButton(
    onClick = { ... },
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(4.dp),  // 小圆角
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ),
    contentPadding = PaddingValues(16.dp),
    interactionSource = interactionSource
) {
    Text(button.text)
}
```

**设计理念：**
- 按钮本身使用小圆角（4dp）
- 外层容器使用大圆角（12dp）
- 形成层次感，避免圆角冲突

## 改进计划

### 需要修改的文件

1. **InstallDialogScreen.kt** - 安装对话框按钮
2. **InstallerScreen.kt** - 主界面按钮
3. **LogsScreen.kt** - 日志界面按钮
4. **SettingsScreen.kt** - 设置界面按钮项

### 改进要点

#### 1. InstallDialogScreen.kt
- 为确认界面的按钮添加 Column 包裹
- 使用 12dp 圆角容器
- 按钮间距调整为 4dp
- 按钮圆角改为 4dp

#### 2. InstallerScreen.kt
- 权限请求按钮、选择文件按钮、安装按钮分别用 Card 包裹
- 统一按钮容器的圆角和间距

#### 3. LogsScreen.kt
- 清除和分享按钮使用 Row + IntrinsicSize.Max 包裹
- 添加统一的容器背景

#### 4. SettingsScreen.kt
- 设置项点击效果优化
- 添加按压状态的视觉反馈

## 视觉效果对比

### 改进前
```
┌─────────────────────┐
│  [安装按钮]          │  ← 直接放置，无包裹
│  [权限] [取消]       │  ← 直接放置，无包裹
└─────────────────────┘
```

### 改进后
```
┌─────────────────────┐
│ ┏━━━━━━━━━━━━━━━━━┓ │  ← 12dp 圆角容器
│ ┃   [安装按钮]     ┃ │  ← 4dp 圆角按钮
│ ┗━━━━━━━━━━━━━━━━━┛ │
│                     │  ← 4dp 间距
│ ┏━━━━━━━┓ ┏━━━━━━━┓ │  ← 12dp 圆角容器
│ ┃[权限] ┃ ┃[取消] ┃ │  ← 4dp 圆角按钮，等高
│ ┗━━━━━━━┛ ┗━━━━━━━┛ │
└─────────────────────┘
```

## 实施步骤

1. ✅ 分析参考仓库的按钮实现模式
2. 🔄 创建按钮容器组件（可选）
3. 🔄 修改 InstallDialogScreen.kt
4. 🔄 修改其他屏幕的按钮布局
5. 🔄 测试不同场景下的显示效果
6. 🔄 调整细节（间距、圆角、颜色等）

## 技术要点

### IntrinsicSize.Max 的作用
```kotlin
Row(modifier = Modifier.height(IntrinsicSize.Max)) {
    Button(Modifier.weight(1f)) { Text("短") }
    Button(Modifier.weight(1f)) { Text("很长的文本") }
}
```
- 确保 Row 的高度等于最高子元素的高度
- 所有按钮都会拉伸到相同高度
- 避免按钮高度不一致的问题

### clip vs shape
- `Modifier.clip()` - 裁剪内容，适用于容器
- `shape` 参数 - 定义形状，适用于按钮本身
- 两者配合使用，实现双层圆角效果

## 参考资料

- InstallerX-Revived: `DialogButtons.kt`
- Material Design 3 按钮规范
- Compose Layout 最佳实践
