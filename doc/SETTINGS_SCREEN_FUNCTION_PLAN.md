# SettingsScreen 功能实现计划

## 问题分析

当前 SettingsScreen 中所有按钮的 `onClick` 都是空实现 `{ }`，导致以下功能失效：

1. ❌ **主题设置** - 点击无反应
2. ❌ **语言设置** - 点击无反应
3. ❌ **通知设置** - 点击无反应
4. ❌ **权限授权** - 点击无反应，状态显示"检查中"
5. ❌ **关于开发者** - 点击无反应
6. ❌ **版本信息** - 点击无反应，版本号硬编码为"v1.0.0"

---

## 已创建的文件

### SettingsViewModel.kt ✅

已创建 ViewModel，包含以下功能：
- 主题管理（读取/设置）
- 语言管理（读取/设置/获取显示名称）
- 权限状态管理（刷新/请求/切换模式）
- 状态文本转换

---

## 需要实现的功能

### 1. 主题设置对话框

**参考**: `SettingsFragment.java` 第480-600行

**需要创建的组件**:
- `ThemeSelectionDialog.kt` - 主题选择对话框
  - 三个选项：系统/浅色/深色
  - RadioButton 单选
  - 确认后调用 `viewModel.setTheme(theme)`

**主题常量**:
```kotlin
ThemeManager.THEME_SYSTEM = -1
ThemeManager.THEME_LIGHT = 1
ThemeManager.THEME_DARK = 2
```

---

### 2. 语言设置对话框

**参考**: `SettingsFragment.java` 第186-361行

**需要创建的组件**:
- `LanguageSelectionDialog.kt` - 语言选择对话框
  - 七个语言选项：
    - 系统默认 ("system")
    - 简体中文 ("zh")
    - 繁体中文 ("zh-TW")
    - English ("en")
    - Русский ("ru")
    - 日本語 ("ja")
    - 喵星语 ("zh-HK")
  - RadioButton 单选
  - 确认后调用 `viewModel.setLanguage(languageCode)`
  - 需要重启 Activity：`activity.recreate()`

---

### 3. 通知设置

**参考**: `SettingsFragment.java` 第385-468行

**功能**:
- 检查通知权限状态
- 已授权：显示锁定状态
- 未授权：弹出对话框请求权限
- Android 13+ 需要运行时权限

**需要**:
- `NotificationHelper.isNotificationPermissionGranted(context)`
- 权限请求 Launcher（需要在 Activity 中注册）

---

### 4. 权限授权设置

**参考**: `SettingsFragment.java` 第424-450行

**功能**:
- 显示当前权限模式（Shizuku/Dhizuku）
- 显示权限状态（已授权/未授权/未安装等）
- 点击后显示选择对话框

**需要创建的组件**:
- `PrivilegeSelectionDialog.kt` - 权限模式选择对话框
  - 显示 Shizuku 和 Dhizuku 选项
  - 显示各自的状态
  - 提供授权/下载/打开按钮

**状态显示**:
```kotlin
val statusText = viewModel.getStatusText(viewModel.privilegeStatus.value)
val modeName = PrivilegeHelper.getModeName(viewModel.privilegeMode.value)
subtitle = "$modeName: $statusText"
```

---

### 5. 关于开发者

**参考**: `SettingsFragment.java` 第90-106行

**功能**:
- 跳转到 MeActivity（关于页面）
- 添加滑动动画

**问题**: MeActivity 不存在，需要创建或移除该功能

**解决方案**:
- 方案A: 创建简单的 MeActivity/MeScreen
- 方案B: 显示一个 AlertDialog 展示开发者信息
- 方案C: 移除此功能

---

### 6. 版本信息

**参考**: `SettingsFragment.java` 第101-106行, 第700-800行（彩蛋）

**功能**:
- 显示实际的应用版本号
- 连续点击6次触发彩蛋

**需要**:
- 从 PackageInfo 获取版本号
- 实现彩蛋逻辑（可选）

**获取版本号代码**:
```kotlin
val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
val versionName = packageInfo.versionName  // e.g., "1.0.0"
val versionCode = packageInfo.versionCode  // e.g., 1
```

---

## 实现优先级

### 高优先级（核心功能）
1. ✅ SettingsViewModel - 已创建
2. 🔲 主题设置对话框
3. 🔲 语言设置对话框
4. 🔲 权限状态显示和刷新

### 中优先级（常用功能）
5. 🔲 通知设置
6. 🔲 权限授权对话框
7. 🔲 版本号动态获取

### 低优先级（增强功能）
8. 🔲 关于开发者页面
9. 🔲 版本彩蛋
10. 🔲 主题颜色选择器
11. 🔲 安装器包名设置

---

## 技术要点

### Activity 重启

语言切换后需要重启 Activity：
```kotlin
// 在 Compose 中
val activity = LocalContext.current as? Activity
activity?.recreate()
```

### 权限请求

Android 13+ 通知权限需要在 Activity 中注册 Launcher：
```kotlin
// 在 MainActivity 中
private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    // 处理结果
}
```

### FileProvider 配置

导出日志等功能需要 FileProvider，已在 AndroidManifest.xml 中配置。

---

## 下一步行动

由于完整实现所有功能需要大量代码（约2000+行），建议：

1. **先实现核心功能**：主题、语言、权限状态
2. **测试基本功能**：确保能正常工作
3. **逐步添加其他功能**：通知、关于、版本等

是否需要我立即开始实现这些功能？请告诉我您希望优先实现哪些功能。
