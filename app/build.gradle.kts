import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// 共用版本号与版本名
val baseVersionCode = 699
val baseVersionName = "26.08"

// 构建时的日期+时间
fun getBuildDateTime(): String {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmm"))
}

// 获取 git 总提交数
fun getGitCommitCount(): Int {
    return try {
        providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }.standardOutput.asText.get().trim().toInt()
    } catch (_: Exception) {
        baseVersionCode
    }
}

// 获取 git 短哈希
fun getGitCommitHash(): String {
    return try {
        providers.exec {
            commandLine("git", "rev-parse", "--short=7", "HEAD")
        }.standardOutput.asText.get().trim()
    } catch (_: Exception) {
        getBuildDateTime()
    }
}

android {
    namespace = "io.github.huidoudour.Installer"
    compileSdk = 37
    ndkVersion = "30.0.14904198"

    defaultConfig {
        applicationId = "io.github.huidoudour.Installer"
        minSdk = 28 //Android 9
        targetSdk = 37 //Android 17
        versionCode = baseVersionCode // 自定义版本号
        // 重组版本名: 基础版本.总提交数.短哈希
        versionName = "${baseVersionName}.${getGitCommitCount()}.${getGitCommitHash()}"

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
    }

    buildTypes {
        debug {
            signingConfig = if (useSignKey) {
                signingConfigs.getByName("sign_key")
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
                enable = true
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
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.accompanist.drawablepainter)
    implementation("androidx.palette:palette:1.0.0")

    // Material Kolor - 动态主题颜色生成
    implementation(libs.material.kolor)

    // ====== 必要依赖开始 ======
    // Hidden API for Dhizuku binder wrapper
    // (compileOnly - uses system framework at runtime)
    compileOnly(project(":hidden-api"))
    // Shizuku api/provider
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Dhizuku
    // 原版 Dhizuku API（用于 com.rosan.dhizuku）
    implementation("io.github.iamr0s:Dhizuku-API:2.6.0")

    // 绕过隐式 API
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    implementation("androidx.core:core-ktx:1.19.0")
    // ====== 必要依赖结束 ======
    // 测试依赖
    // MTDataFilesProvider,documentfile
    debugImplementation("com.github.L-JINBIN:MTDataFilesProvider:v1.0.0")
    debugImplementation("androidx.documentfile:documentfile:1.1.0")
    debugImplementation(libs.compose.ui.tooling.preview)
}
