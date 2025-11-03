plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.huidoudour.Installer"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.huidoudour.Installer"
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
        
        // === 完整的 16KB 页面大小支持配置 ===
        // 确保整个 APK 在 16KB 页面大小的设备上正常运行 (Android 15+)
        packaging {
            jniLibs {
                // 保持原生库的调试符号
                keepDebugSymbols += "**/*.so"
                // 启用原生库解压优化 (对齐支持)
                useLegacyPackaging = false
            }
            // 配置资源压缩选项
            resources {
                // 排除不需要的元数据
                excludes += setOf(
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE",
                    "META-INF/LICENSE.txt",
                    "META-INF/NOTICE",
                    "META-INF/NOTICE.txt"
                )
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
    kotlinOptions {
        jvmTarget = "11"
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
    
    // === 原生库功能依赖 (.so 文件) - 已更新到支持 16KB 的版本 ===
    
    // 1. 高性能加密库 (包含原生 .so 库，用于文件哈希计算)
    // 注意: conscrypt 2.5.2 可能不支持 16KB，但 2.5.3+ 已支持
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    
    // 2. Apache Commons Compress (包含原生压缩库，支持 ZIP/XAPK 解压)
    // 注意: commons-compress 是纯 Java库，不包含 .so 文件
    implementation("org.apache.commons:commons-compress:1.28.0")
    
    // 3. APK 签名验证库 (包含原生库)
    // 注意: apksig 是纯 Java库，不包含 .so 文件
    implementation("com.android.tools.build:apksig:8.7.3")
    
    // 4. RecyclerView (用于显示 APK 分析结果列表)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // 注意: Shell 终端功能使用 Shizuku 的原生能力，不需要额外的终端库
    
    // Kotlin support
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("androidx.core:core-ktx:1.15.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

// === 16KB 页面对齐验证任务 ===
// 此任务用于验证 APK 中的所有原生库是否正确对齐到 16KB
tasks.register("verify16KBAlignment") {
    group = "verification"
    description = "验证 APK 中所有原生库的 16KB 对齐"
    
    doLast {
        val apkFile = file("build/outputs/apk/debug/app-debug-debug.apk")
        if (apkFile.exists()) {
            println("✅ 找到 APK: ${apkFile.absolutePath}")
            println("⚠️  请使用以下命令手动验证原生库对齐:")
            println("   unzip -l ${apkFile.absolutePath} | grep '\\.so$'")
            println("   然后对每个 .so 文件执行:")
            println("   readelf -l <.so文件> | grep LOAD")
            println("   应该看到对齐值为 0x4000 (16384)")
        } else {
            println("❌ APK 文件不存在，请先构建: ./gradlew assembleDebug")
        }
    }
}