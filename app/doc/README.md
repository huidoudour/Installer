# App-Debug Installer 设计说明

## 概述
这是一个基于 app-debug 模板的现代化 APK 安装器应用，集成了 app 模块的 Shizuku 安装功能。

## 功能特性

### 1. 底部导航栏设计
- **主页 (Home)**: APK 安装器主功能
- **工具 (Dashboard)**: 扩展工具页面
- **关于我 (Notifications)**: 应用信息和关于页面

### 2. Installer 主页功能

#### Shizuku 状态卡片
- 实时显示 Shizuku 服务状态
- 状态指示器（绿色=已授权，橙色=未授权，红色=未运行）
- 一键请求 Shizuku 权限

#### 文件选择卡片
- Material Design 3 风格
- 支持选择 APK 文件
- 自动处理存储权限（Android 11+ MANAGE_EXTERNAL_STORAGE）
- 显示选中的文件名

#### 安装选项卡片
- **替换现有应用**: 允许覆盖安装（pm install -r）
- **自动授予权限**: 安装时自动授予运行时权限（pm install -g）
- 智能安装按钮（仅在 Shizuku 已授权且文件已选择时可用）

#### 日志卡片
- 实时显示安装过程日志
- Monospace 字体便于阅读
- 带时间戳的日志记录
- 一键清空日志功能

## 设计特点

### UI/UX
1. **Material Design 3**: 使用最新的 Material 组件
2. **卡片式布局**: 清晰的功能区域划分
3. **响应式设计**: 支持滚动，适配不同屏幕尺寸
4. **颜色语义化**: 状态指示清晰直观

### 技术实现
1. **ViewBinding**: 类型安全的视图绑定
2. **Fragment 架构**: 模块化设计，易于扩展
3. **Activity Result API**: 现代化的权限和文件选择处理
4. **Shizuku 集成**: 利用 Shizuku 实现无需 Root 的 APK 安装

## 权限说明
- `READ_EXTERNAL_STORAGE`: Android 10 及以下读取存储
- `MANAGE_EXTERNAL_STORAGE`: Android 11+ 全盘访问
- `REQUEST_INSTALL_PACKAGES`: 安装应用权限
- Shizuku 权限: 通过 Shizuku 执行 pm install 命令

## 依赖项
```kotlin
// Shizuku
implementation("dev.rikka.shizuku:api:13.1.5")
implementation("dev.rikka.shizuku:provider:13.1.5")

// AndroidX
implementation("androidx.navigation:navigation-fragment")
implementation("androidx.navigation:navigation-ui")
implementation("com.google.android.material:material")
```

## 使用流程
1. 启动应用，检查 Shizuku 状态
2. 如果 Shizuku 未授权，点击"授予 Shizuku 权限"
3. 点击"选择 APK 文件"选择要安装的应用
4. 根据需要调整安装选项（替换/授权）
5. 点击"安装 APK"开始安装
6. 查看日志了解安装进度和结果

## 扩展建议
- **Dashboard 页面**: 可添加批量安装、应用管理等功能
- **Notifications 页面**: 可显示安装历史、应用信息等
- **设置页面**: 可配置默认安装选项、日志级别等

## 兼容性
- 最低 SDK: 24 (Android 7.0)
- 目标 SDK: 36 (Android 14+)
- Shizuku 最低版本: 11

---
基于 app 模块的 MainActivity 设计思路，应用到 app-debug 的 Fragment 架构中。
