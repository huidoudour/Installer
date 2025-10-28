plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.huidoudour.Installer.debug"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.huidoudour.Installer.debug"
        minSdk = 28 //Android 9
        targetSdk = 36 //Android 16
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 启用NDK - 配置C++共享库编译
        ndk {
            // 支持的CPU架构
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
        
        // CMake配置
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    // 启用 16KB 页面对齐支持 (Android 15+兼容性)
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
            }
        }
        
        // 启用 16KB 页面大小支持 (适配未来 Android 版本)
        // 这确保原生库在 16KB 页面大小的设备上正常运行
        packaging {
            jniLibs {
                // 保持原生库的调试符号
                keepDebugSymbols += "**/*.so"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    
    // Lint 配置 - 避免非关键警告阻塞 CI
    lint {
        // 将警告视为警告,不要作为错误
        warningsAsErrors = false
        // 出现错误时终止构建
        abortOnError = true
        // 禁用某些检查
        disable += setOf(
            "HardcodedText",           // 允许硬编码文本(调试阶段)
            "SetTextI18n",             // 允许文本拼接
            "DefaultLocale",           // 允许默认Locale
            "SdCardPath",              // 允许硬编码路径(系统工具)
            "UseTomlInstead",          // 暂不强制使用版本目录
            "ObsoleteSdkInt",          // 允许过时的SDK版本检查
            "UnusedResources",         // 允许未使用资源(可能被动态引用)
            "Overdraw",                // 允许过度绘制
            "UselessParent",           // 允许冗余父布局
            "Autofill",                // 不强制自动填充提示
            "FragmentTagUsage",        // 允许使用fragment标签
            "GradleDependency",        // 不强制更新依赖
            "NewerVersionAvailable"    // 不强制更新到最新版本
            // 注意: 已移除 "Aligned16KB"，因为我们已正确配置 16KB 对齐
        )
        // 仅检查致命错误
        checkOnly += setOf(
            "NotSibling",              // 必须检查布局引用错误
            "DuplicateIds",            // 必须检查重复ID
            "UnknownId"                // 必须检查未知ID引用
        )
    }
    
    // 配置外部原生构建（C++ CMake）
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    
    // Shizuku dependencies
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    
    // === 原生库功能依赖 (.so 文件) ===
    
    // 1. 高性能加密库 (包含原生 .so 库，用于文件哈希计算)
    implementation("org.conscrypt:conscrypt-android:2.5.2")
    
    // 2. Apache Commons Compress (包含原生压缩库，支持 ZIP/XAPK 解压)
    implementation("org.apache.commons:commons-compress:1.25.0")
    
    // 3. APK 签名验证库 (包含原生库)
    implementation("com.android.tools.build:apksig:8.3.0")
    
    // 4. RecyclerView (用于显示 APK 分析结果列表)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // 注意: Shell 终端功能使用 Shizuku 的原生能力，不需要额外的终端库
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}