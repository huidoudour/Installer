# C++ 原生库集成 - 快速开始指南

## ✅ 已完成的配置

### 1. 文件结构
```
app/
├── build.gradle.kts                 ✅ 已启用 NDK 和 CMake
├── src/main/
│   ├── cpp/
│   │   ├── native-lib.cpp           ✅ C++ 源代码
│   │   ├── CMakeLists.txt           ✅ CMake 配置 (16KB 对齐)
│   │   └── README.md                ✅ C++ 源码说明
│   └── java/.../utils/
│       └── NativeHelper.java        ✅ JNI 包装类
└── doc/
    └── CPP_NATIVE_LIBRARY.md        ✅ 详细文档
```

### 2. 架构支持
- ✅ arm64-v8a (64位手机)
- ✅ x86_64 (模拟器)
- ✅ 16KB 页面对齐 (Android 15+)

### 3. Gradle 配置

已在 `build.gradle.kts` 中添加:

```kotlin
defaultConfig {
    // NDK 配置
    externalNativeBuild {
        cmake {
            abiFilters("arm64-v8a", "x86_64")
            cppFlags += listOf("-std=c++17")
            arguments += listOf(
                "-DANDROID_STL=c++_shared",
                "-DCMAKE_VERBOSE_MAKEFILE=ON"
            )
        }
    }
    ndkVersion = "27.0.12077973"
}

// CMake 路径配置
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}
```

## 🚀 构建步骤

### 方法 1: Android Studio

1. **同步项目**
   - 点击 `File` → `Sync Project with Gradle Files`
   - 等待 Gradle 同步完成

2. **构建 APK**
   - 点击 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
   - 或使用快捷键: `Ctrl+F9` (Windows)

3. **查看输出**
   - 构建完成后,查看 `app/build/outputs/apk/debug/`
   - 检查 APK 中的 .so 文件

### 方法 2: 命令行

**Windows (PowerShell):**
```powershell
# 清理项目
.\gradlew clean

# 构建 Debug APK
.\gradlew assembleDebug

# 构建 Release APK
.\gradlew assembleRelease

# 查看 APK 内容
Expand-Archive -Path app\build\outputs\apk\debug\app-debug.apk -DestinationPath extracted
ls extracted\lib\*\*.so
```

**Linux/Mac:**
```bash
# 清理项目
./gradlew clean

# 构建 Debug APK
./gradlew assembleDebug

# 查看 APK 内容
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep "\.so"
```

## 🔍 验证原生库

### 1. 检查编译输出

构建过程中应该看到:
```
> Task :app:buildCMakeDebug[arm64-v8a]
=== 16KB 页面对齐配置 ===
已设置链接器标志: -Wl,-z,max-page-size=16384
目标架构: arm64-v8a
...
BUILD SUCCESSFUL
```

### 2. 检查 APK 内容

应该包含以下文件:
```
lib/
├── arm64-v8a/
│   ├── libinstaller-native.so    ← 你的原生库
│   ├── libc++_shared.so          ← C++ 运行时
│   ├── libshizuku.so             ← 已有的库
│   └── ...
└── x86_64/
    ├── libinstaller-native.so
    └── libc++_shared.so
```

### 3. 在设备上测试

```java
// 在任何 Activity 中测试
import io.github.huidoudour.Installer.utils.NativeHelper;

if (NativeHelper.isNativeLibraryAvailable()) {
    NativeHelper helper = new NativeHelper();
    String version = helper.getNativeVersion();
    String arch = helper.getCPUArchitecture();
    
    Log.i("Native", "Version: " + version);
    Log.i("Native", "Architecture: " + arch);
} else {
    Log.e("Native", "加载失败: " + NativeHelper.getLoadError());
}
```

## 📦 生成的文件

### 编译中间文件
```
app/build/intermediates/cmake/debug/obj/
├── arm64-v8a/
│   └── libinstaller-native.so
└── x86_64/
    └── libinstaller-native.so
```

### 最终 APK
```
app/build/outputs/apk/debug/
├── app-arm64-v8a-debug.apk        # arm64 专用
├── app-x86_64-debug.apk           # x86_64 专用
└── app-universal-debug.apk        # 包含所有架构
```

## ⚠️ 常见问题

### 问题 1: NDK 未安装

**错误信息:**
```
NDK is not installed
```

**解决方案:**
1. 打开 Android Studio
2. `Tools` → `SDK Manager`
3. `SDK Tools` 标签
4. 勾选 `NDK (Side by side)` 和 `CMake`
5. 点击 `Apply` 下载安装

### 问题 2: CMake 版本不匹配

**错误信息:**
```
CMake '3.22.1' was not found
```

**解决方案:**
- 在 SDK Manager 中安装对应版本的 CMake
- 或修改 `build.gradle.kts` 中的 CMake 版本号

### 问题 3: 编译错误

**错误信息:**
```
undefined reference to 'std::chrono::...'
```

**解决方案:**
- 确认 C++ 标准设置正确: `-std=c++17`
- 检查 `CMakeLists.txt` 中的编译选项

### 问题 4: 库加载失败

**错误信息:**
```
java.lang.UnsatisfiedLinkError: dlopen failed
```

**解决方案:**
1. 检查设备架构是否匹配:
   ```bash
   adb shell getprop ro.product.cpu.abi
   ```
2. 确认 APK 中包含对应架构的 .so 文件
3. 清理重建项目

## 🎯 下一步

### 1. 添加更多原生功能

编辑 `native-lib.cpp`,添加新的 JNI 方法:

```cpp
extern "C" JNIEXPORT jint JNICALL
Java_io_github_huidoudour_Installer_utils_NativeHelper_multiply(
        JNIEnv* env,
        jobject thiz,
        jint a,
        jint b) {
    return a * b;
}
```

在 `NativeHelper.java` 中声明:
```java
public native int multiply(int a, int b);
```

### 2. 集成第三方 C++ 库

例如 OpenSSL, zlib 等:

```cmake
# 在 CMakeLists.txt 中添加
find_package(OpenSSL REQUIRED)
target_link_libraries(installer-native OpenSSL::SSL)
```

### 3. 优化性能

- 使用 `-O3` 优化级别
- 启用 LTO (Link Time Optimization)
- 使用 NEON 指令集 (ARM)

### 4. 运行性能测试

使用提供的 `NativeHelper`:

```java
NativeHelper helper = new NativeHelper();
String perfResult = helper.runPerformanceComparison();
String hashResult = helper.testHashPerformance();

Log.i("Performance", perfResult);
Log.i("Performance", hashResult);
```

## 📚 参考文档

- `app/doc/CPP_NATIVE_LIBRARY.md` - 详细使用说明
- `app/src/main/cpp/README.md` - C++ 源码说明
- [Android NDK 官方文档](https://developer.android.com/ndk)

## ✅ 检查清单

在发布前确认:

- [ ] 项目成功构建,无编译错误
- [ ] APK 包含所有目标架构的 .so 文件
- [ ] 在真实设备上测试原生库加载成功
- [ ] 性能测试结果符合预期
- [ ] 16KB 对齐验证通过 (Android 15+)
- [ ] APK 体积在可接受范围内

---

**配置完成日期**: 2025-12-04  
**NDK 版本**: 27.0.12077973  
**CMake 版本**: 3.22.1  
**支持架构**: arm64-v8a, x86_64
