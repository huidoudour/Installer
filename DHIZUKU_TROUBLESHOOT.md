# Dhizuku 安装问题诊断指南

## 🔍 问题症状
- 安装时点击"安装"后，应用卡住约 50 秒
- 日志显示 "Session committed, waiting for result..." 后没有后续
- 最终可能超时或显示错误

## 🔧 已修复内容

### 1. LocalIntentReceiver.java
**修复了 Binder.onTransact() 返回值问题**：
- 原代码返回 `false`，可能导致 IntentSender.send() 调用失败
- 现改为返回 `true`，允许 transact 调用继续

**添加了超时机制**：
- 超时时间设为 120 秒（原为 5 分钟）
- 超时会抛出明确的错误信息

**添加了调试日志**：
- `LocalIntentReceiver: send() called`
- `LocalIntentReceiver: Received result: status=X`
- 方便追踪回调是否被正确触发

### 2. DhizukuInstallHelper.java
**增强日志**：
- 记录每个关键步骤
- 超时时输出可能的根本原因

## 📋 排查步骤

### 步骤 1：重新测试
1. 重新编译安装应用
2. 清空日志（设置 → 清空日志）
3. 再次尝试安装 APK
4. 查看新日志中是否有 "LocalIntentReceiver: send() called"

### 步骤 2：检查 IntentSender 是否被调用
查看日志，如果看到：
- ✅ **"LocalIntentReceiver: send() called"** → IntentSender 被调用了，问题可能在后续处理
- ❌ **没有这条日志** → Dhizuku 服务端没有调用 IntentSender

### 步骤 3：检查 Dhizuku 版本兼容性
```
当前 Dhizuku API 版本: 2.5.4
检查方法：查看手机上 Dhizuku 应用的版本号
```

如果 Dhizuku 版本较旧，尝试更新到最新版本。

### 步骤 4：检查 Dhizuku 服务器状态
1. 打开 Dhizuku 应用
2. 确保 Dhizuku 服务正在运行（显示"正在运行"）
3. 检查是否有任何错误提示

### 步骤 5：尝试使用 Shizuku
如果问题持续，可以尝试使用 Shizuku 作为替代方案：
1. 设置 → 切换授权器 → 选择 Shizuku
2. 按照提示授权 Shizuku
3. 再次尝试安装

## ⚠️ 可能的原因

### 原因 1：Dhizuku 版本不兼容
Dhizuku 2.5.4 可能与当前 Android 版本存在兼容性问题。

**解决方案**：更新 Dhizuku 到最新版本

### 原因 2：Dhizuku 服务器未响应
Dhizuku 服务端可能卡住或无响应。

**解决方案**：
1. 强制停止 Dhizuku 应用
2. 重新打开 Dhizuku
3. 重新授权

### 原因 3：IntentSender 回调机制问题
这是 Dhizuku API 的已知问题，某些情况下回调不会被触发。

**解决方案**：
1. 使用 Shizuku 替代
2. 等待 Dhizuku 更新修复此问题

## 📞 获取更多日志

如果问题仍然存在，请在 GitHub 提交 Issue，包含：
1. Android 版本
2. Dhizuku 应用版本
3. 完整的日志文件（清空后重新安装获取）
4. 手机型号

## 🔗 相关资源
- Dhizuku GitHub: https://github.com/iamr0s/Dhizuku
- Dhizuku API: https://github.com/iamr0s/Dhizuku-API
