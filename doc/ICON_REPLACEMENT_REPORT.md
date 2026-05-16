# 本地图标替换报告

## 📋 替换概述

已将应用中的 Material Design Icons 替换为本地 drawable 图标，使 UI 更加统一和个性化。

## ✅ 已完成的图标替换

### 1. **底部导航栏** (MainActivity.kt)

| 位置 | 原图标 | 新图标 | 文件 |
|------|--------|--------|------|
| 首页 | `Icons.Default.Home` | `ic_home_black` | ✅ |
| 终端 | `Icons.Default.Build` | `ic_terminal` | ✅ |
| 日志 | `Icons.Default.List` | `ic_list` | ✅ |
| 设置 | `Icons.Default.Settings` | `ic_settings` | ✅ |

### 2. **Shell 工具栏** (ShellScreen.kt)

| 按钮 | 原图标 | 新图标 | 文件 |
|------|--------|--------|------|
| 历史 | `Icons.Default.Refresh` | `ic_shell_history` | ✅ |
| 书签 | `Icons.Default.Star` | `ic_shell_bookmark` | ✅ |
| 搜索 | `Icons.Default.Search` | `ic_shell_search` | ✅ |
| 保存 | `Icons.Default.Create` | `ic_shell_save` | ✅ |

### 3. **设置页面** (SettingsScreen.kt)

| 设置项 | 原图标 | 新图标 | 文件 |
|--------|--------|--------|------|
| 主题设置 | `Icons.Default.Settings` | `ic_settings` | ✅ |
| 主题颜色 | `Icons.Default.Settings` | `ic_palette` | ✅ |
| 语言设置 | `Icons.Default.Info` | `ic_language` | ✅ |
| 通知设置 | `Icons.Default.Notifications` | `ic_notifications_outline` | ✅ |
| 安装包 | `Icons.Default.Build` | `ic_package` | ✅ |
| 权限授权 | `Icons.Default.Lock` | `ic_lock` | ✅ |
| 版本信息 | `Icons.Default.Info` | `ic_version` | ✅ |

### 4. **安装对话框** (InstallDialogScreen.kt)

| 位置 | 原图标 | 新图标 | 文件 |
|------|--------|--------|------|
| 默认应用图标 | `Icons.Default.Build` | `ic_terminal` | ✅ |

### 5. **安装器页面** (InstallerScreen.kt)

| 位置 | 原图标 | 新图标 | 文件 |
|------|--------|--------|------|
| 权限按钮（未授权） | `Icons.Default.Lock` | `ic_lock` | ✅ |

## 🎨 保留的 Material Icons

以下图标保持使用 Material Icons，因为它们没有合适的本地图标替代：

- ✅ `Icons.Default.CheckCircle` - 完成/成功状态
- ✅ `Icons.Default.Add` - 添加操作
- ✅ `Icons.Default.Refresh` - 刷新操作（部分场景）
- ✅ `Icons.Default.Delete` - 删除操作
- ✅ `Icons.Default.Share` - 分享操作
- ✅ `Icons.Default.Info` - 信息提示
- ✅ `Icons.Default.Person` - 用户相关
- ✅ `Icons.Default.ArrowDropDown` - 下拉箭头

## 📁 使用的本地图标资源

所有图标位于：`app/src/main/res/drawable/`

### 导航图标
- `ic_home_black.xml` - 首页
- `ic_terminal.xml` - 终端
- `ic_list.xml` - 列表/日志
- `ic_settings.xml` - 设置

### Shell 专用图标
- `ic_shell_history.xml` - 历史记录
- `ic_shell_bookmark.xml` - 书签
- `ic_shell_search.xml` - 搜索
- `ic_shell_save.xml` - 保存

### 设置图标
- `ic_palette.xml` - 调色板/主题颜色
- `ic_language.xml` - 语言
- `ic_notifications_outline.xml` - 通知
- `ic_package.xml` - 安装包
- `ic_lock.xml` - 锁/权限
- `ic_version.xml` - 版本

### 其他图标
- `ic_github.xml` - GitHub
- `ic_translate.xml` - 翻译
- `ic_warning.xml` - 警告
- `ic_colorize.xml` - 着色
- `ic_forward.xml` - 前进箭头
- `ic_arrow_down.xml` - 向下箭头

## 🔧 技术实现

### 导入语句
```kotlin
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
```

### 使用方式
```kotlin
// 在 Composable 中使用
Icon(
    imageVector = ImageVector.vectorResource(R.drawable.ic_home_black),
    contentDescription = stringResource(R.string.home)
)

// 在数据类中使用
BottomNavItemData(
    route = Screen.Home.route,
    titleResId = R.string.title_home,
    icon = ImageVector.vectorResource(R.drawable.ic_home_black)
)
```

## 📊 优势

### 1. **视觉一致性**
- 所有图标风格统一
- 符合应用整体设计语言
- 更好的品牌识别度

### 2. **自定义性**
- 可以完全控制图标设计
- 不受 Material Icons 限制
- 更容易匹配应用主题

### 3. **离线可用**
- 不依赖网络加载
- 启动速度更快
- 无额外依赖

### 4. **性能优化**
- Vector Drawable 体积小
- 支持任意尺寸缩放
- 渲染效率高

## ⚠️ 注意事项

1. **图标尺寸**
   - 所有图标应设计为 24dp 基准尺寸
   - Vector Drawable 会自动缩放到目标尺寸

2. **颜色适配**
   - 使用 `tint` 属性动态着色
   - 确保图标在不同主题下可见

3. **内容描述**
   - 始终提供 `contentDescription`
   - 保证无障碍访问支持

4. **向后兼容**
   - Vector Drawable 支持 Android 5.0+
   - 项目最低 SDK 为 28，完全兼容

## 🚀 后续建议

1. **补充缺失图标**
   - 为 `CheckCircle`、`Add` 等创建本地图标
   - 统一所有图标的视觉风格

2. **深色模式优化**
   - 检查图标在深色背景下的对比度
   - 必要时提供深色变体

3. **动画图标**
   - 考虑为关键操作添加AnimatedVectorDrawable
   - 提升交互体验

4. **图标管理**
   - 建立图标命名规范
   - 定期清理未使用的图标

---

**更新日期**: 2026-05-16  
**版本**: 5.5.7-alpha  
**执行人**: Qoder AI Assistant
