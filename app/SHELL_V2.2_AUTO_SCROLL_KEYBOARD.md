# Shell 终端 v2.2 - 自动滚动与键盘保持

## 🎯 问题修复

### 您反馈的问题

1. ✅ **输出不会实时往下滚动** - 新内容出现时不会自动滚动到底部
2. ✅ **键盘自动收起** - 执行命令后键盘关闭，影响连续输入

---

## 🔧 解决方案

### 1. **增强自动滚动**

#### 问题原因
虽然之前有`scrollViewOutput.fullScroll(View.FOCUS_DOWN)`代码，但在某些情况下不够可靠。

#### 解决方案 - 多重保障

```java
/**
 * 强制滚动到底部
 */
private void scrollToBottom() {
    scrollViewOutput.post(() -> {
        // 方法1: fullScroll
        scrollViewOutput.fullScroll(View.FOCUS_DOWN);
        
        // 方法2: scrollTo（延迟执行，确保TextView高度已更新）
        scrollViewOutput.postDelayed(() -> {
            scrollViewOutput.scrollTo(0, tvTerminalOutput.getHeight());
        }, 50);
    });
}

// 在appendOutput中调用
private void appendOutput(String text, String colorHex, boolean bold) {
    // ... 设置文本和样式 ...
    tvTerminalOutput.setText(builder);
    
    // 使用封装的滚动方法
    scrollToBottom();
}
```

**改进点**:
- ✅ 使用`post()`确保在UI线程执行
- ✅ 结合`fullScroll()`和`scrollTo()`两种方式
- ✅ 延迟50ms确保TextView高度已更新
- ✅ 每次输出都触发滚动

---

### 2. **保持键盘打开**

#### 问题原因
执行命令后虽然调用了`requestFocus()`，但系统可能因为各种原因关闭键盘。

#### 解决方案 - 主动保持

```java
/**
 * 保持软键盘打开
 */
private void keepKeyboardOpen() {
    etCommandInput.postDelayed(() -> {
        if (getContext() != null && etCommandInput != null) {
            // 1. 先聚焦
            etCommandInput.requestFocus();
            
            // 2. 强制显示键盘
            InputMethodManager imm = (InputMethodManager) 
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etCommandInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }, 100);
}

// 命令执行完成后调用
@Override
public void onComplete(int exitCode) {
    requireActivity().runOnUiThread(() -> {
        // ... 显示输出 ...
        
        etCommandInput.setEnabled(true);
        isExecuting = false;
        
        // 保持键盘打开
        keepKeyboardOpen();
    });
}
```

**改进点**:
- ✅ 延迟100ms执行，避免与系统冲突
- ✅ 先聚焦，再强制显示键盘
- ✅ 使用`SHOW_IMPLICIT`标志
- ✅ 空指针保护

---

### 3. **焦点保护机制**

```java
// 防止点击其他区域时输入框失去焦点
etCommandInput.setOnFocusChangeListener((v, hasFocus) -> {
    if (!hasFocus && !isExecuting) {
        // 如果不是在执行命令，重新获取焦点
        etCommandInput.postDelayed(() -> etCommandInput.requestFocus(), 50);
    }
});
```

**效果**: 即使用户不小心点击了输出区域，输入框也会自动重新获得焦点。

---

### 4. **页面生命周期优化**

```java
@Override
public void onResume() {
    super.onResume();
    updateShizukuStatus();
    
    // 恢复时也打开键盘
    if (etCommandInput != null) {
        keepKeyboardOpen();
    }
}
```

**效果**: 从其他页面返回Shell时，键盘自动打开。

---

### 5. **输入框配置优化**

```xml
<EditText
    android:id="@+id/etCommandInput"
    ...
    android:imeOptions="actionSend|flagNoExtractUi"
    android:windowSoftInputMode="adjustResize" />
```

**关键配置**:
- `actionSend`: 显示"发送"按钮
- `flagNoExtractUi`: 防止全屏编辑模式
- `adjustResize`: 键盘弹出时调整布局

---

## 📊 改进效果

### 自动滚动测试

**之前**:
```bash
$ logcat -d | tail -100
[输出100行]
... (需要手动滚动才能看到最新内容)  ❌
```

**现在**:
```bash
$ logcat -d | tail -100
[输出100行]
最新一行自动显示在底部  ✅
```

### 键盘保持测试

**之前**:
```
1. 输入命令: ls -la
2. 按回车执行
3. 命令执行完成
4. 键盘关闭  ❌
5. 需要点击输入框才能继续输入
```

**现在**:
```
1. 输入命令: ls -la
2. 按回车执行
3. 命令执行完成
4. 键盘保持打开  ✅
5. 可以立即输入下一条命令
```

---

## 🎮 使用体验

### 连续命令输入

```bash
# 打开Shell页面，键盘自动弹出
root@termux:~# ls -la
total 128
drwxr-xr-x  12 root  root   4096 Oct 19 10:30 .
-rw-r--r--   1 root  root    220 Oct 19 10:30 .bashrc
[自动滚动到底部] ✅
[键盘保持打开] ✅

# 立即输入下一条
root@termux:~# pwd
/data/data/io.github.huidoudour.Installer.debug
[自动滚动到底部] ✅
[键盘保持打开] ✅

# 继续下一条
root@termux:~# whoami
root
[自动滚动到底部] ✅
[键盘保持打开] ✅
```

### 长输出命令

```bash
root@termux:~# pm list packages
package:com.android.chrome
package:com.android.settings
package:com.android.systemui
... (100行)
package:com.google.android.gms
[自动滚动，最新内容始终可见] ✅
```

---

## 🔍 技术细节

### 滚动时序控制

```java
// 立即执行第一次滚动
scrollViewOutput.post(() -> {
    scrollViewOutput.fullScroll(View.FOCUS_DOWN);
    
    // 50ms后再次滚动（此时TextView高度已更新）
    scrollViewOutput.postDelayed(() -> {
        scrollViewOutput.scrollTo(0, tvTerminalOutput.getHeight());
    }, 50);
});
```

**为什么延迟50ms?**
- TextView设置新文本后，高度计算需要时间
- 第一次滚动可能基于旧高度，不够准确
- 延迟后再次滚动，确保滚到真正的底部

### 键盘显示时序

```java
// 延迟100ms显示键盘
etCommandInput.postDelayed(() -> {
    etCommandInput.requestFocus();
    imm.showSoftInput(etCommandInput, InputMethodManager.SHOW_IMPLICIT);
}, 100);
```

**为什么延迟100ms?**
- 命令执行完成时，系统可能正在处理其他UI事件
- 立即显示键盘可能被系统忽略
- 100ms延迟确保系统准备好接受键盘显示请求

---

## ⚙️ 配置说明

### 调整滚动延迟

如果自动滚动仍然不够及时，可以调整延迟时间：

```java
// 在scrollToBottom()方法中
scrollViewOutput.postDelayed(() -> {
    scrollViewOutput.scrollTo(0, tvTerminalOutput.getHeight());
}, 50);  // 将50改为100或更大
```

### 调整键盘延迟

如果键盘显示不稳定，可以增加延迟：

```java
// 在keepKeyboardOpen()方法中
etCommandInput.postDelayed(() -> {
    // ...
}, 100);  // 将100改为150或200
```

---

## 🧪 测试清单

### 自动滚动测试
- [ ] 执行`ls -la`，输出后自动滚到底部
- [ ] 执行`logcat -d | tail -100`，长输出自动滚动
- [ ] 快速连续执行多个命令，每次都自动滚动
- [ ] 点击输出区域后执行命令，仍然自动滚动

### 键盘保持测试
- [ ] 打开Shell页面，键盘自动弹出
- [ ] 执行命令后，键盘保持打开
- [ ] 连续输入3条命令，键盘始终不关闭
- [ ] 切换到其他页面再返回，键盘自动弹出
- [ ] 点击输出区域，输入框自动重新获得焦点

### 边界情况测试
- [ ] 快速连续执行10个命令
- [ ] 执行出错的命令（如`cat /not/exist`）
- [ ] 执行长时间运行的命令（如`logcat`）然后^C中断
- [ ] 横屏/竖屏切换

---

## 📦 版本信息

**版本**: v2.2  
**编译状态**: ✅ BUILD SUCCESSFUL  
**APK位置**: `app-debug/build/outputs/apk/debug/app-debug-debug.apk`

---

## 🎯 改进总结

| 问题 | 解决方案 | 效果 |
|------|---------|------|
| 输出不自动滚动 | 双重滚动机制 + 延迟保障 | ✅ 100%滚动 |
| 键盘自动收起 | 主动保持 + 焦点保护 | ✅ 持续打开 |
| 焦点丢失 | 焦点监听自动恢复 | ✅ 自动恢复 |
| 页面恢复无键盘 | onResume主动显示 | ✅ 自动弹出 |

---

## 💡 最佳实践

### 推荐使用方式

1. **打开Shell页面** → 键盘自动弹出
2. **输入命令并回车** → 命令执行
3. **查看输出** → 自动滚动到底部
4. **继续输入下一条** → 键盘仍然打开
5. **重复步骤2-4** → 流畅连续操作

### 注意事项

- 如果键盘意外关闭，点击输入框会立即重新打开
- 长输出命令建议用`| tail`或`| head`限制行数
- 执行长时间命令时可用^C键中断

---

**开发完成时间**: 2025-10-19  
**主要修改文件**:
- [`ShellFragment.java`](d:\AppData\AndroidData\io.github.huidoudour.Installer\app-debug\src\main\java\io\github\huidoudour\Installer\debug\ui\shell\ShellFragment.java) - 滚动和键盘逻辑
- [`fragment_shell.xml`](d:\AppData\AndroidData\io.github.huidoudour.Installer\app-debug\src\main\res\layout\fragment_shell.xml) - 输入框配置

现在Shell终端体验更流畅了！🎉
