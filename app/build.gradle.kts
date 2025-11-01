plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.huidoudour.Installer"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.huidoudour.Installer"
        minSdk = 28  // Android 9
        targetSdk = 36  //Android 16
        versionCode = 1
        versionName = "1.0"
        
        // 添加原生库支持
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
            }
        }
        
        // 支持的CPU架构
        ndk {
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
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
    buildFeatures {
        viewBinding = true
    }
    
    // Lint 配置 - 与 app-debug 模块保持一致
    lint {
        warningsAsErrors = false
        abortOnError = true
        disable += setOf(
            "HardcodedText",
            "SetTextI18n",
            "DefaultLocale",
            "SdCardPath",
            "UseTomlInstead",
            "ObsoleteSdkInt",
            "UnusedResources",
            "Overdraw",
            "UselessParent",
            "Autofill",
            "FragmentTagUsage",
            "GradleDependency",
            "NewerVersionAvailable"
            // 注意: 已移除 "Aligned16KB"，因为我们已正确配置 16KB 对齐
        )
        checkOnly += setOf(
            "NotSibling",
            "DuplicateIds",
            "UnknownId"
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
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.cardview:cardview:1.0.0")
    
    // 添加 Fragment 和 Navigation 组件
    implementation("androidx.fragment:fragment:1.6.1")
    implementation("androidx.navigation:navigation-fragment:2.9.3")
    implementation("androidx.navigation:navigation-ui:2.9.3")
    
    // 添加 Activity 组件
    implementation("androidx.activity:activity:1.11.0")
    
    // 添加 Core 组件
    implementation("androidx.core:core:1.12.0")
    
    // 添加 Lifecycle 组件
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")

    // Shizuku依赖
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // 统一 Kotlin 版本
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
    
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
}