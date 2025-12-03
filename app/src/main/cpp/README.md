# C++ 原生库源码

## 📁 文件说明

### native-lib.cpp
C++ 源代码实现,包含以下 JNI 方法:

- `getNativeVersion()` - 返回库版本号
- `getCPUArchitecture()` - 检测并返回 CPU 架构
- `calculateSimpleHash()` - 快速哈希计算
- `performanceTest()` - 性能基准测试
- `isNativeLibraryLoaded()` - 验证库加载状态
- `getBuildInfo()` - 编译器和构建信息

### CMakeLists.txt
CMake 构建配置文件,特性:

- ✅ 支持 C++17 标准
- ✅ 16KB 页面对齐 (Android 15+ 兼容)
- ✅ 优化编译选项 (-O2)
- ✅ 符号隐藏 (减小库体积)
- ✅ 详细构建日志

## 🔧 构建流程

1. Gradle 调用 CMake
2. CMake 读取 CMakeLists.txt
3. 编译 native-lib.cpp
4. 生成 libinstaller-native.so
5. 打包到 APK 的 lib/<abi>/ 目录

## 📊 编译输出

构建后生成的文件:
```
build/intermediates/cmake/debug/obj/
├── arm64-v8a/
│   └── libinstaller-native.so  (~20KB)
└── x86_64/
    └── libinstaller-native.so  (~20KB)
```

## 🎯 16KB 对齐

CMakeLists.txt 中的关键配置:

```cmake
set(CMAKE_SHARED_LINKER_FLAGS 
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_SHARED_LINKER_FLAGS 
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,common-page-size=16384")
```

这确保生成的 .so 文件在 Android 15+ 设备上正确运行。

## 🚀 快速开始

### 修改代码
1. 编辑 `native-lib.cpp`
2. 添加新的 JNI 方法
3. 在 `NativeHelper.java` 中声明对应的 native 方法

### 重新编译
```bash
.\gradlew clean
.\gradlew assembleDebug
```

### 验证
检查 APK 中是否包含新的 .so 文件:
```bash
unzip -l app\build\outputs\apk\debug\app-debug.apk | findstr "\.so"
```

## 📚 JNI 命名规范

JNI 方法命名格式:
```
Java_<package>_<class>_<method>
```

示例:
```cpp
// Java 方法: io.github.huidoudour.Installer.utils.NativeHelper.getNativeVersion()
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_huidoudour_Installer_utils_NativeHelper_getNativeVersion(
    JNIEnv* env,
    jobject thiz)
```

## ⚠️ 注意事项

1. **包名变更**: 如果修改 Java 包名,必须同步更新 JNI 方法名
2. **内存管理**: 使用 `env->ReleaseStringUTFChars()` 释放 Java 字符串
3. **异常处理**: JNI 中不能直接使用 C++ 异常,需要转换为 Java 异常
4. **线程安全**: 确保 JNI 方法是线程安全的

## 🔮 扩展建议

### 添加新功能示例

```cpp
// 1. 在 native-lib.cpp 中添加实现
extern "C" JNIEXPORT jint JNICALL
Java_io_github_huidoudour_Installer_utils_NativeHelper_add(
        JNIEnv* env,
        jobject thiz,
        jint a,
        jint b) {
    return a + b;
}
```

```java
// 2. 在 NativeHelper.java 中声明
public native int add(int a, int b);
```

```java
// 3. 使用
NativeHelper helper = new NativeHelper();
int result = helper.add(10, 20); // 返回 30
```

## 📖 参考资料

- [JNI 规范](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/)
- [Android NDK 指南](https://developer.android.com/ndk/guides)
- [CMake 变量参考](https://cmake.org/cmake/help/latest/manual/cmake-variables.7.html)
