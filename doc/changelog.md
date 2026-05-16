## 签出分支dev
### 05161453-实现日志管理功能并重构相关界面
 - 添加 LogManager 类用于全局日志管理，支持日志持久化到 SharedPreferences
 - 创建 LogAdapter 适配器用于 RecyclerView 高效显示大量日志条目
 - 在 InstallDialog 中集成日志记录功能，记录安装过程的关键事件和错误信息
 - 重构 LogsFragment 使用 RecyclerView 替代传统 TextView，提升大量日志下的性能
 - 在 LogsFragment 中实现日志滚动跟随功能，支持手动滚动查看历史日志
 - 为 ShellFragment 添加 SharedPreferences 记录首次启动状态并显示欢迎信息
 - 更新 ShellFragment 界面按钮图标，使用标准 Material 图标替换文本标签
 - 添加多个界面字符串资源的国际化支持，包括主题和语言选项名称
### 05161353-功能与UI
 - 集成输出搜索功能，支持关键词匹配和实时过滤
 - 实现保存输出功能，自动保存到Download目录
 - 添加历史命令对话框，支持快速访问和重新执行
 - 增加快速滚动按钮，智能显示并支持一键跳转
 - 更新快捷命令列表，参考aShell项目新增15个实用命令
 - 优化UI界面，添加顶部工具栏和Material You风格设计
 - 修复蝴蝶图标颜色显示问题
 - 更新语言设置界面，优化交互逻辑和选中状态
### 05091252-调试
### 05070006-接口文档demo APP
- 在 settings.gradle.kts 中包含 :hidden-api:demo 模块
- 添加 demo 模块的 .gitignore 配置文件
- 创建 demo 模块的 build.gradle.kts 构建配置
- 实现 MainActivity 展示 hidden-api 中各个接口的文档信息
- 添加应用图标、布局文件和主题资源
- 配置 AndroidManifest.xml 文件
### 05062319-功能优化
 - UI优化
 - 语言切换功能修复
 - 增加原生库
 - 日志捕获功能
### 05062148-功能优化
### 05062143-功能优化；添加测试语言翻译