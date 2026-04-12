# 分享安装功能测试指南

## 功能说明

此功能允许用户通过系统分享菜单，将 APK、XAPK、APKS 等安装包文件分享到你的应用进行安装。

## 已实现的功能

### 1. Intent Filter 注册
在 `AndroidManifest.xml` 中为 `InstallDialog` Activity 添加了以下支持：

- **APK 文件**: `application/vnd.android.package-archive`
- **XAPK 文件**: `application/x-xapk`, `application/octet-stream`
- **APKS 文件**: `application/octet-stream`
- **APKM 文件**: `application/vnd.apkm`, `application/octet-stream`

支持的操作：
- `ACTION_VIEW` - 直接打开文件
- `ACTION_INSTALL_PACKAGE` - 安装包的专用操作
- `ACTION_SEND` - 从分享菜单接收文件

### 2. 代码改进

#### InstallDialog.java
- ✅ 添加 `handleSendIntent()` 方法处理分享意图
- ✅ 增强 `getFilePathFromUri()` 方法，正确识别文件扩展名
- ✅ 添加 `getFileNameFromUri()` 辅助方法获取文件名
- ✅ 添加 `getFileExtension()` 辅助方法获取文件扩展名
- ✅ 根据文件类型创建正确的临时文件（保留 .xapk/.apks/.apkm 扩展名）
- ✅ 增加缓冲区大小（8KB）提高文件复制性能

## 测试步骤

### 测试 1: 通过文件管理器分享 APK

1. 打开手机上的文件管理器
2. 找到一个 APK 文件
3. 长按文件，点击"分享"或"发送"
4. 在分享菜单中选择 "Installer" 应用
5. 应该看到安装对话框显示 APK 信息
6. 点击"安装"按钮开始安装

### 测试 2: 通过文件管理器分享 XAPK

1. 找到一个 XAPK 文件
2. 长按文件，点击"分享"
3. 选择 "Installer" 应用
4. 应该看到安装对话框显示 "XAPK安装包"
5. 显示 APK 数量（例如："3 个 APK 文件"）
6. 点击"安装"按钮开始安装

### 测试 3: 通过浏览器下载后分享

1. 使用浏览器下载一个 APK/XAPK 文件
2. 下载完成后，点击下载通知或打开下载文件夹
3. 长按文件，选择"分享"
4. 选择 "Installer" 应用
5. 验证安装对话框正常显示

### 测试 4: 从其他应用分享

1. 在任何应用中（如微信、QQ、Telegram）
2. 收到一个 APK/XAPK 文件
3. 点击下载/保存文件
4. 在文件管理器中找到该文件
5. 分享并选择 "Installer"

## 预期行为

### 成功情况
- ✅ 安装对话框正确显示
- ✅ APK 文件显示应用图标、名称、版本等信息
- ✅ XAPK/APKS 文件显示 "XAPK安装包" 和 APK 数量
- ✅ 文件大小正确显示
- ✅ 点击"安装"后显示进度条
- ✅ 安装完成后显示完成界面

### 错误处理
- ❌ 如果文件格式不支持，显示错误提示
- ❌ 如果无法读取文件，显示"无法访问安装文件"
- ❌ 如果 Shizuku 未授权，禁用安装按钮并提示

## 技术细节

### 文件处理流程

```
用户分享文件
    ↓
InstallDialog 接收 Intent (ACTION_SEND)
    ↓
handleSendIntent() 提取 URI
    ↓
getFilePathFromUri() 复制到临时文件
    ↓
检测文件类型 (APK/XAPK/APKS)
    ↓
displayInstallInfo() 显示文件信息
    ↓
用户点击安装
    ↓
ShizukuInstallHelper 执行安装
```

### 临时文件管理

- APK 文件: `cache/temp_install.apk`
- XAPK 文件: `cache/temp_install_1234567890.xapk`
- APKS 文件: `cache/temp_install_1234567890.apks`
- APKM 文件: `cache/temp_install_1234567890.apkm`

临时文件会在应用缓存目录中自动清理。

## 调试技巧

### 查看日志

```bash
adb logcat | grep InstallDialog
```

关键日志：
- "接收到分享的文件: [URI]"
- "临时文件已创建: [路径]"
- "处理分享意图失败"（如果有错误）

### 常见问题

**问题 1: 分享菜单中没有显示 Installer 应用**
- 检查 AndroidManifest.xml 中的 intent-filter 是否正确
- 确保文件类型被支持（MIME type 匹配）

**问题 2: 安装对话框闪退**
- 检查日志中的异常信息
- 确认 Shizuku 已授权

**问题 3: XAPK 文件识别为 APK**
- 检查文件扩展名是否正确
- 验证 `XapkInstaller.isXapkFile()` 方法

## 兼容性

- ✅ Android 7.0+ (API 24+)
- ✅ 支持 file:// 和 content:// URI
- ✅ 支持所有常见的安装包格式

## 下一步优化建议

1. **批量安装**: 支持同时分享多个文件
2. **历史记录**: 记录最近安装的文件
3. **文件预览**: 在安装前预览 XAPK 内容
4. **自动清理**: 定期清理过期的临时文件
5. **权限请求**: 对未知来源的应用给出警告

---

**创建时间**: 2026-04-12  
**版本**: 1.0  
**作者**: AI Assistant
