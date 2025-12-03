#include <jni.h>
#include <string>
#include <sstream>
#include <chrono>
#include <android/log.h>

#define LOG_TAG "InstallerNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * 获取原生库版本信息
 */
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_huidoudour_Installer_utils_NativeHelper_getNativeVersion(
        JNIEnv* env,
        jobject /* this */) {
    LOGI("getNativeVersion called");
    return env->NewStringUTF("1.0.0");
}

/**
 * 获取 CPU 架构信息
 */
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_huidoudour_Installer_utils_NativeHelper_getCPUArchitecture(
        JNIEnv* env,
        jobject /* this */) {
    LOGI("getCPUArchitecture called");
    
#if defined(__aarch64__)
    return env->NewStringUTF("arm64-v8a");
#elif defined(__x86_64__)
    return env->NewStringUTF("x86_64");
#elif defined(__arm__)
    return env->NewStringUTF("armeabi-v7a");
#elif defined(__i386__)
    return env->NewStringUTF("x86");
#else
    return env->NewStringUTF("unknown");
#endif
}

/**
 * 简单的哈希计算示例 (用于演示性能)
 * 注意: 这是简化版,实际生产环境请使用 OpenSSL 或 conscrypt
 */
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_huidoudour_Installer_utils_NativeHelper_calculateSimpleHash(
        JNIEnv* env,
        jobject /* this */,
        jstring input) {
    
    const char* nativeString = env->GetStringUTFChars(input, nullptr);
    if (nativeString == nullptr) {
        LOGE("Failed to get input string");
        return env->NewStringUTF("");
    }
    
    LOGI("calculateSimpleHash called with input length: %zu", strlen(nativeString));
    
    // 简单的哈希算法 (仅用于演示)
    unsigned long hash = 5381;
    int c;
    const char* str = nativeString;
    
    while ((c = *str++)) {
        hash = ((hash << 5) + hash) + c;
    }
    
    env->ReleaseStringUTFChars(input, nativeString);
    
    // 转换为十六进制字符串
    std::stringstream ss;
    ss << std::hex << hash;
    
    return env->NewStringUTF(ss.str().c_str());
}

/**
 * 字符串处理性能测试
 */
extern "C" JNIEXPORT jlong JNICALL
Java_io_github_huidoudour_Installer_utils_NativeHelper_performanceTest(
        JNIEnv* env,
        jobject /* this */,
        jint iterations) {
    
    LOGI("performanceTest called with %d iterations", iterations);
    
    auto start = std::chrono::high_resolution_clock::now();
    
    // 执行密集计算
    volatile long long result = 0;
    for (int i = 0; i < iterations; i++) {
        result += i * i;
    }
    
    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end - start);
    
    LOGI("Performance test completed in %lld microseconds", (long long)duration.count());
    
    return static_cast<jlong>(duration.count());
}

/**
 * 验证原生库是否正确加载
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_huidoudour_Installer_utils_NativeHelper_isNativeLibraryLoaded(
        JNIEnv* env,
        jobject /* this */) {
    LOGI("Native library is loaded and working!");
    return JNI_TRUE;
}

/**
 * 获取构建信息
 */
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_huidoudour_Installer_utils_NativeHelper_getBuildInfo(
        JNIEnv* env,
        jobject /* this */) {
    
    std::stringstream info;
    info << "Build Date: " << __DATE__ << " " << __TIME__ << "\n";
    info << "Compiler: ";
    
#if defined(__clang__)
    info << "Clang " << __clang_major__ << "." << __clang_minor__;
#elif defined(__GNUC__)
    info << "GCC " << __GNUC__ << "." << __GNUC_MINOR__;
#else
    info << "Unknown";
#endif
    
    info << "\nC++ Standard: " << __cplusplus;
    
    LOGI("Build info requested");
    return env->NewStringUTF(info.str().c_str());
}
