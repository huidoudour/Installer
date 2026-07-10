plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.huidoudour.Installer"
    compileSdk = 37

    ndkVersion = "30.0.14904198"

    defaultConfig {
        applicationId = "io.github.huidoudour.Installer"
        minSdk = 28 //Android 9
        targetSdk = 37 //Android 17
        versionCode = 699 //版本号
        versionName = "v26.06.699" //版本名

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            }
        }
    }

        val useSignKey = rootProject.hasProperty("storeFile") &&
        rootProject.hasProperty("storePassword") &&
        rootProject.hasProperty("keyAlias") &&
        rootProject.hasProperty("keyPassword")
    val devSignKey = rootProject.hasProperty("dbgFilePath") &&
        rootProject.hasProperty("dbgPassword") &&
        rootProject.hasProperty("dbgKeyAlias") &&
        rootProject.hasProperty("dbgKeyPaswd")

    signingConfigs {
        if (useSignKey) {
            create("sign_key") {
                storeFile = file(rootProject.property("storeFile") as String)
                storePassword = rootProject.property("storePassword") as String
                keyAlias = rootProject.property("keyAlias") as String
                keyPassword = rootProject.property("keyPassword") as String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
        if (devSignKey) {
            create("debug_key") {
                storeFile = file(rootProject.property("dbgFilePath") as String)
                storePassword = rootProject.property("dbgPassword") as String
                keyAlias = rootProject.property("dbgKeyAlias") as String
                keyPassword = rootProject.property("dbgKeyPaswd") as String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = if (useSignKey) {
                signingConfigs.getByName("debug_key")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            optimization {
                enable = false
            }
            signingConfig = if (useSignKey) {
                signingConfigs.getByName("sign_key")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // NDK 构建配置
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    
    // 启用 ViewBinding
    buildFeatures {
        viewBinding = true
    }

    // 配置 APK 分块 - 支持全部 4 个架构
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

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
    
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.accompanist.drawablepainter)
    implementation("androidx.palette:palette:1.0.0")

    // Material Kolor - 动态主题颜色生成
    implementation(libs.material.kolor)

    debugImplementation(libs.compose.ui.tooling)

    // ====== 必要依赖开始 ======
    // Hidden API for Dhizuku binder wrapper
    // (compileOnly - uses system framework at runtime)
    compileOnly(project(":hidden-api"))
    // Shizuku api/provider
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Dhizuku
    implementation("io.github.iamr0s:Dhizuku-API:2.6.0")

    // 绕过隐式 API
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    // ACRA - 崩溃捕获
    implementation("ch.acra:acra-core:5.11.3")
    // Kotlin support
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("androidx.core:core-ktx:1.15.0")
    // ====== 必要依赖结束 ======
    // Jetpack Graphics 原生库 - 用于高性能图形渲染
    implementation("androidx.graphics:graphics-path:1.0.1")
    implementation("androidx.graphics:graphics-core:1.0.1")
    // 测试依赖
    // MTDataFilesProvider,documentfile
    debugImplementation("com.github.L-JINBIN:MTDataFilesProvider:v1.0.0")
    debugImplementation("androidx.documentfile:documentfile:1.0.1")

}
