# Shell 工作目录优化

## 🐛 问题描述

用户反馈执行`ls`命令时没有输出，只显示退出码1：

```bash
user@ashell:~$ ls
[Process completed with exit code 1]

user@ashell:~$ pwd
/
```

## 🔍 问题分析

### 根本原因

1. **默认工作目录是根目录 `/`**
   - Shell会话启动后默认在根目录
   - 根目录需要root权限才能列出内容
   - 普通用户执行`ls /`会返回权限错误

2. **权限不足**
   ```bash
   $ ls /
   ls: /: Permission denied  # 在普通模式下
   ```

3. **退出码说明**
   - Exit code 1 表示命令执行失败
   - 但没有错误输出显示给用户（可能被过滤）

### 为什么`pwd`有输出而`ls`没有？

- `pwd`命令只是读取环境变量，不需要文件系统权限
- `ls`需要读取目录内容，在`/`目录下需要更高权限

## ✅ 解决方案

### 1. 自动切换到用户可访问的目录

在创建Shell会话时，根据权限模式自动切换工作目录：

```java
// 初始化环境 - 清除提示符并禁用作业控制
persistentShellWriter.write("export PS1=''\n");
persistentShellWriter.write("export PS2=''\n");
persistentShellWriter.write("set +m\n");

// 切换到用户可访问的目录
if (useShizuku) {
    // Shizuku模式下使用 /data/local/tmp
    persistentShellWriter.write("cd /data/local/tmp 2>/dev/null || cd /sdcard\n");
} else {
    // 普通模式使用 /sdcard
    persistentShellWriter.write("cd /sdcard 2>/dev/null || cd /data/local/tmp\n");
}

persistentShellWriter.flush();
```

### 2. 目录选择策略

| 模式 | 首选目录 | 备用目录 | 说明 |
|------|---------|---------|------|
| Shizuku (Root) | `/data/local/tmp` | `/sdcard` | 系统临时目录，适合测试 |
| Normal (User) | `/sdcard` | `/data/local/tmp` | 用户存储目录，始终可访问 |

### 3. 错误重定向

使用`2>/dev/null`抑制cd失败的错误信息：
```bash
cd /data/local/tmp 2>/dev/null || cd /sdcard
```

这样即使第一个目录不存在，也会静默失败并尝试备用目录。

### 4. 更新欢迎信息

在欢迎信息中显示当前工作目录：

```java
if (ShellExecutor.isShizukuAvailable()) {
    appendOutput("[*] Root mode enabled via Shizuku", colorPrimary, false);
    appendOutput("[*] Working directory: /data/local/tmp", colorOnSurfaceVariant, false);
} else {
    appendOutput("[!] User mode (grant Shizuku for root)", colorTertiary, false);
    appendOutput("[*] Working directory: /sdcard", colorOnSurfaceVariant, false);
}
```

## 📊 修改前后对比

### 修改前

```bash
user@ashell:~$ pwd
/

user@ashell:~$ ls
[Process completed with exit code 1]  # 权限错误
```

### 修改后

```bash
Welcome to aShell You - Style Terminal
Android Shell Environment v2.0

[!] User mode (grant Shizuku for root)
[*] Working directory: /sdcard

Type 'help' for command list

user@ashell:~$ pwd
/sdcard

user@ashell:~$ ls
Alarms
Android
DCIM
Download
...
[Process completed with exit code 0]
```

## 🎯 技术细节

### 为什么选择这些目录？

#### `/sdcard/` (首选 - 普通模式)
- ✅ 始终对应用可读写
- ✅ 用户熟悉的位置
- ✅ 实际映射到 `/storage/emulated/0/`
- ✅ Android 6.0+ 默认授予READ_EXTERNAL_STORAGE

#### `/data/local/tmp/` (首选 - Root模式)
- ✅ 传统的Unix临时目录
- ✅ Root权限下完全可访问
- ✅ 适合测试和临时文件
- ✅ 不会污染用户存储空间

#### 备用机制
使用`||`逻辑或运算符：
```bash
cd /data/local/tmp 2>/dev/null || cd /sdcard
```
- 如果第一个命令成功，不执行第二个
- 如果第一个失败，执行第二个作为备用

### 初始化时序

```
1. 创建Shell进程 (sh)
2. 设置环境变量 (PS1, PS2)
3. 禁用作业控制 (set +m)
4. 切换工作目录 (cd ...)  ← 新增
5. flush 输出流
6. 等待200ms初始化
7. 清空初始输出
```

## 🧪 测试场景

### 场景1：普通用户模式

```bash
user@ashell:~$ pwd
/sdcard

user@ashell:~$ ls
Download  DCIM  Pictures  ...

user@ashell:~$ cd Download
user@ashell:~$ pwd
/sdcard/Download
```

### 场景2：Shizuku Root模式

```bash
root@ashell:~# pwd
/data/local/tmp

root@ashell:~# ls
# 列出临时文件

root@ashell:~# cd /system
root@ashell:~# ls
app  bin  build.prop  ...
```

### 场景3：目录不存在时的降级

```bash
# 假设 /data/local/tmp 不可访问
cd /data/local/tmp 2>/dev/null || cd /sdcard
# → 自动降级到 /sdcard
```

## 📝 代码改进

### 修改文件
[ShellExecutor.java](file://d:\AppData\AndroidData\io.github.huidoudour.Installer\app-debug\src\main\java\io\github\huidoudour\Installer\debug\utils\ShellExecutor.java#L397-L421)

### 关键代码

```java
// 切换到用户可访问的目录（/sdcard 或 /data/local/tmp）
if (useShizuku) {
    // Shizuku模式下可以访问任何目录，使用/data/local/tmp
    persistentShellWriter.write("cd /data/local/tmp 2>/dev/null || cd /sdcard\n");
} else {
    // 普通模式使用/sdcard
    persistentShellWriter.write("cd /sdcard 2>/dev/null || cd /data/local/tmp\n");
}
```

## 💡 最佳实践

1. **权限感知**：根据运行模式选择合适的工作目录
2. **错误处理**：使用备用目录机制确保总能找到可用位置
3. **用户体验**：在欢迎信息中明确告知当前位置
4. **静默失败**：使用`2>/dev/null`避免干扰用户

## 🔗 相关改进

配合之前的TTY修复，现在Shell终端具备：
1. ✅ 无TTY错误
2. ✅ 合理的默认工作目录
3. ✅ 持久化会话
4. ✅ 命令历史
5. ✅ Material You界面
6. ✅ C++原生库支持

## 📚 Android目录结构参考

| 路径 | 权限要求 | 用途 | 可访问性 |
|------|---------|------|---------|
| `/` | Root | 根目录 | 仅Root |
| `/sdcard/` | User | 用户存储 | 始终可用 |
| `/data/local/tmp/` | Root | 临时文件 | 需要Root |
| `/data/data/` | Root | 应用私有数据 | 需要Root |
| `/system/` | Root | 系统分区 | 需要Root |
| `/storage/emulated/0/` | User | 实际的sdcard | 始终可用 |

## ✅ 总结

通过自动切换到用户可访问的目录，解决了：
1. 命令执行权限问题
2. 提升用户体验（不需要手动cd）
3. 根据权限模式智能选择目录
4. 提供备用机制确保可用性

现在用户可以直接执行`ls`、`pwd`等命令，无需担心权限问题！

---

**优化时间**: 2025-10-23  
**影响版本**: Shell v3.0.1  
**优化后版本**: Shell v3.0.2
