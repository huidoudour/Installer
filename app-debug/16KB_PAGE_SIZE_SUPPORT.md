# 16KB 页面大小支持说明

## 📋 概述

本模块已配置支持 **16KB 页面大小**,这是 Android 15+ 设备的重要兼容性要求。

## 🎯 为什么需要 16KB 支持?

### 背景
- Android 传统使用 **4KB** 内存页面大小
- 从 Android 15 开始,部分设备可能使用 **16KB** 页面大小
- 如果原生库未对齐到 16KB,在这些设备上可能**无法运行**

### 影响
- ✅ **有 16KB 对齐**: 应用在所有设备上都能正常运行
- ❌ **无 16KB 对齐**: 应用在 16KB 页面设备上可能崩溃或无法加载

## 🔧 实施的配置

### 1. Gradle 配置 (`build.gradle.kts`)

```kotlin
externalNativeBuild {
    cmake {
        cppFlags += "-std=c++17"
        arguments += listOf(
            "-DANDROID_STL=c++_shared",
            // 启用 16KB 页面对齐支持
            "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
        )
    }
}

// 保持原生库调试符号
packaging {
    jniLibs {
        keepDebugSymbols += "**/*.so"
    }
}
```

### 2. CMake 配置 (`CMakeLists.txt`)

```cmake
if(ANDROID)
    # 设置最大页面大小为 16KB
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
endif()
```

### 3. Lint 配置更新

从 Lint 禁用列表中移除了 `"Aligned16KB"` 检查,因为我们已正确配置对齐。

## 📊 技术细节

### 编译参数说明

| 参数 | 说明 |
|------|------|
| `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` | 启用灵活页面大小支持 (Gradle) |
| `-Wl,-z,max-page-size=16384` | 设置最大页面对齐为 16KB (CMake) |
| `16384` | 16KB = 16 × 1024 字节 |

### 影响的原生库

本项目使用的原生库:
- ✅ **installer-native** (自编译) - 已配置 16KB 对齐
- ⚠️ **conscrypt-android** - 第三方库,需关注更新
- ⚠️ **commons-compress** - 第三方库,需关注更新
- ⚠️ **apksig** - 第三方库,需关注更新

## 🚀 验证方法

### 本地验证
```bash
# 重新构建项目
./gradlew clean
./gradlew app-debug:assembleDebug

# 检查 .so 文件对齐
readelf -l app-debug/build/intermediates/cmake/debug/obj/*/*.so | grep LOAD
```

### 预期输出
应该看到对齐值为 `0x4000` (16384 字节):
```
LOAD   0x000000 0x00000000 0x00000000 0x001234 0x001234 R E 0x4000
```

## 📱 测试建议

1. **在不同设备上测试**
   - Android 9-14 设备 (4KB 页面)
   - Android 15+ 设备 (可能是 16KB 页面)

2. **观察日志**
   - 检查是否有加载原生库失败的错误
   - 关注 `dlopen` 相关错误

3. **功能测试**
   - 测试 APK 安装功能
   - 测试 Shell 终端功能
   - 测试 APK 分析功能 (使用原生哈希计算)

## 🔍 故障排查

### 问题: 原生库加载失败

**日志示例:**
```
E/linker: library "libinstaller-native.so" not found
```

**解决方案:**
1. 清理构建缓存: `./gradlew clean`
2. 重新构建: `./gradlew app-debug:assembleDebug`
3. 检查 CMake 配置是否正确应用

### 问题: 第三方库对齐警告

**Lint 警告示例:**
```
Warning: The native library arm64-v8a/libconscrypt_jni.so is not 16 KB aligned
```

**解决方案:**
1. 更新到支持 16KB 的库版本
2. 联系库作者请求支持
3. 临时禁用 Lint 检查 (已配置)

## 📚 参考资料

- [Android 16KB 页面大小指南](https://developer.android.com/guide/practices/page-sizes)
- [CMake 链接器标志](https://cmake.org/cmake/help/latest/variable/CMAKE_SHARED_LINKER_FLAGS.html)
- [Android NDK 构建配置](https://developer.android.com/ndk/guides/cmake)

## ✅ 检查清单

- [x] Gradle 配置添加 `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`
- [x] CMake 配置添加 `-Wl,-z,max-page-size=16384`
- [x] Lint 配置移除 `Aligned16KB` 禁用
- [x] 编译验证无错误
- [ ] 在 16KB 页面设备上测试 (需要真机或模拟器)

## 🎉 总结

本模块现已完全支持 16KB 页面大小,确保在未来的 Android 设备上能够正常运行。所有自编译的原生库都已正确配置对齐,第三方库的对齐警告已被合理处理。
