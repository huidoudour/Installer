#include <jni.h>
#include <string>
#include <sstream>
#include <iomanip>
#include <chrono>
#include <cstring>

/**
 * C++ Native Library 示例
 * 演示如何使用共享库(.so)实现高性能计算
 * 
 * 功能：
 * 1. 字符串哈希计算（简化版SHA-256）
 * 2. 系统信息获取
 * 3. 性能测试
 * 
 * 注意：为了简化示例，这里使用简单哈希算法
 * 实际项目中建议使用conscrypt或BoringSSL
 */

// 简单的哈希函数（用于演示，实际应使用OpenSSL或conscrypt）
std::string simpleHash(const char* str) {
    unsigned long hash = 5381;
    int c;
    const char* s = str;
    
    while ((c = *s++)) {
        hash = ((hash << 5) + hash) + c;
    }
    
    // 扩展为256位格式（模拟SHA-256）
    std::stringstream ss;
    ss << std::hex << std::setw(16) << std::setfill('0') << hash;
    
    // 填充到64个字符（SHA-256长度）
    std::string result = ss.str();
    while (result.length() < 64) {
        result += "0";
    }
    
    return result;
}

extern "C" {

/**
 * 计算字符串的哈希值（简化版，用于演示）
 * @param env JNI环境
 * @param obj 对象引用
 * @param input 输入字符串
 * @return 十六进制格式的哈希值
 */
JNIEXPORT jstring JNICALL
Java_io_github_huidoudour_Installer_debug_utils_NativeHelper_calculateSHA256(
        JNIEnv* env,
        jobject /* this */,
        jstring input) {
    
    // 获取输入字符串
    const char *nativeString = env->GetStringUTFChars(input, nullptr);
    
    // 计算哈希
    std::string hash = simpleHash(nativeString);
    
    // 释放字符串
    env->ReleaseStringUTFChars(input, nativeString);
    
    return env->NewStringUTF(hash.c_str());
}

/**
 * 获取Native库版本信息
 */
JNIEXPORT jstring JNICALL
Java_io_github_huidoudour_Installer_debug_utils_NativeHelper_getNativeVersion(
        JNIEnv* env,
        jobject /* this */) {
    
    std::string version = "Native Library v1.0.0 (Demo)";
    return env->NewStringUTF(version.c_str());
}

/**
 * 获取CPU架构信息
 */
JNIEXPORT jstring JNICALL
Java_io_github_huidoudour_Installer_debug_utils_NativeHelper_getCPUArchitecture(
        JNIEnv* env,
        jobject /* this */) {
    
    std::string arch;
    
    #if defined(__aarch64__)
        arch = "ARM64 (aarch64)";
    #elif defined(__arm__)
        arch = "ARM (32-bit)";
    #elif defined(__x86_64__)
        arch = "x86_64";
    #elif defined(__i386__)
        arch = "x86 (32-bit)";
    #else
        arch = "Unknown";
    #endif
    
    return env->NewStringUTF(arch.c_str());
}

/**
 * 性能测试：批量哈希计算
 * @param count 计算次数
 * @return 执行时间(毫秒)
 */
JNIEXPORT jlong JNICALL
Java_io_github_huidoudour_Installer_debug_utils_NativeHelper_performanceTest(
        JNIEnv* env,
        jobject /* this */,
        jint count) {
    
    auto start = std::chrono::high_resolution_clock::now();
    
    for(int i = 0; i < count; i++) {
        std::string data = "Performance test data " + std::to_string(i);
        simpleHash(data.c_str());
    }
    
    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);
    
    return duration.count();
}

} // extern "C"
