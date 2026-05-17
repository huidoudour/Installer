# 安装对话框 VersionCode 对比改进

## 改进概述

修改了安装对话框的逻辑，使其能够根据 APK 的 VersionCode 与已安装版本的 VersionCode 进行智能对比，从而决定显示"升级"还是"安装"按钮。

## 问题分析

### 之前的实现
```kotlin
val isUpgrade = installedPkg != null
```

**问题：**
- 只要应用已安装，就显示"升级"按钮
- 没有考虑 VersionCode 的大小关系
- 当安装的 APK 版本低于已安装版本时，仍然显示"升级"，这不符合用户预期

### 改进后的实现
```kotlin
// 根据 VersionCode 对比决定是否显示升级
val isUpgrade = if (installedPkg != null) {
    val installedVersionCode = installedPkg.longVersionCode
    // 只有当 APK 的 VersionCode 大于已安装版本时，才显示升级
    versionCode > installedVersionCode
} else {
    false
}
```

## 行为说明

### 1. 未安装应用
- **条件**：`installedPkg == null`
- **显示**：安装按钮（"安装"）
- **isUpgrade**：`false`

### 2. 已安装且 APK 版本更高
- **条件**：`installedPkg != null && versionCode > installedVersionCode`
- **显示**：升级按钮（"升级"）
- **isUpgrade**：`true`
- **示例**：
  - 已安装：v1.0 (versionCode: 10)
  - APK文件：v2.0 (versionCode: 20)
  - 结果：显示"升级"

### 3. 已安装但 APK 版本相同或更低
- **条件**：`installedPkg != null && versionCode <= installedVersionCode`
- **显示**：安装按钮（"安装"）
- **isUpgrade**：`false`
- **示例**：
  - 已安装：v2.0 (versionCode: 20)
  - APK文件：v1.5 (versionCode: 15)
  - 结果：显示"安装"（允许降级安装）

## 技术细节

### 修改的文件
- `app/src/main/java/io/github/huidoudour/Installer/ui/InstallDialogScreen.kt`

### 关键代码位置
- **函数**：`parseApkInfo()`
- **行号**：约 665-680 行
- **修改类型**：逻辑增强

### VersionCode 获取
```kotlin
// APK 文件的 VersionCode
val versionCode = packageInfo.longVersionCode

// 已安装应用的 VersionCode
val installedVersionCode = installedPkg.longVersionCode
```

## 用户体验提升

### 改进前
```
场景：用户尝试安装一个旧版本的 APK
- 已安装 v2.0
- APK 是 v1.5
- 显示："升级" ❌ （误导用户）
```

### 改进后
```
场景：用户尝试安装一个旧版本的 APK
- 已安装 v2.0
- APK 是 v1.5
- 显示："安装" ✅ （准确描述操作）
```

## 边界情况处理

### 1. VersionCode 相等
- **行为**：显示"安装"
- **原因**：允许重新安装相同版本（例如修复损坏的安装）

### 2. VersionCode 为 0
- **行为**：正常比较
- **说明**：Android 系统允许 versionCode 为 0，虽然不推荐

### 3. 获取已安装信息失败
- **行为**：视为未安装，显示"安装"
- **容错**：try-catch 包裹，异常时返回 null

## 测试建议

### 测试场景 1：全新安装
1. 确保设备未安装目标应用
2. 打开任意 APK 文件
3. **预期**：显示"安装"按钮

### 测试场景 2：正常升级
1. 安装 v1.0 版本的应用
2. 打开 v2.0 版本的 APK 文件
3. **预期**：显示"升级"按钮

### 测试场景 3：降级安装
1. 安装 v2.0 版本的应用
2. 打开 v1.0 版本的 APK 文件
3. **预期**：显示"安装"按钮（而非"升级"）

### 测试场景 4：同版本重装
1. 安装 v1.0 版本的应用
2. 打开另一个 v1.0 版本的 APK 文件
3. **预期**：显示"安装"按钮

## 兼容性说明

### Android API 级别
- **最低支持**：API 21 (Android 5.0)
- **VersionCode 获取**：使用 `longVersionCode`（API 28+）或 `versionCode`（API 27-）
- **当前实现**：直接使用 `packageInfo.longVersionCode`，兼容所有支持的 API 级别

### PackageManager 权限
- 不需要额外权限
- 使用标准的 `PackageManager.GET_ACTIVITIES` 标志

## 相关功能

### 按钮文本显示
```kotlin
Text(
    text = if (state.isUpgrade) stringResource(R.string.upgrade) else stringResource(R.string.install),
    fontSize = 14.sp,
    fontWeight = FontWeight.Bold
)
```

### 国际化支持
- `R.string.upgrade` - "升级"（中文）/ "Upgrade"（英文）
- `R.string.install` - "安装"（中文）/ "Install"（英文）

## 总结

通过本次改进，安装对话框能够更准确地反映用户的实际操作意图：
- ✅ 智能识别升级场景
- ✅ 正确处理降级安装
- ✅ 避免误导性文案
- ✅ 提升用户体验

---

**完成时间**：2026-05-17  
**修改文件数**：1 个  
**代码行数变化**：+9 行，-1 行
