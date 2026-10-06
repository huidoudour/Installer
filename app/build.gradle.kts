import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.baselineprofile)
}

val baseVersionCode = 8000
val baseVersionName = "26.09.27"

fun getBuildDateTime(): String {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmm"))
}

fun getGitCommitCount(): Int {
    return try {
        providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }.standardOutput.asText.get().trim().toInt()
    } catch (_: Exception) {
        baseVersionCode
    }
}

fun getGitCommitHash(): String {
    return try {
        providers.exec {
            commandLine("git", "rev-parse", "--short=7", "HEAD")
        }.standardOutput.asText.get().trim()
    } catch (_: Exception) {
        getBuildDateTime()
    }
}

val appVersionCode = baseVersionCode + getGitCommitCount()
val appVersionName = "${baseVersionName}.${getGitCommitCount()}.${getGitCommitHash()}"
// 本地构建产物的文件名哈希，与 CI 使用的 7 位短 hash 一致
val gitShortHash = getGitCommitHash()

// 构建开始横幅：独立任务（被 assemble/bundle 依赖），无输出故每次都会执行，配置缓存复用也不例外
val buildBanner = tasks.register("buildBanner") {
    description = "执行时间戳"
    // CC 兼容：配置期求值成普通 List<String>，Action 只捕获它
    val requested = gradle.startParameter.taskNames
        .map { it.substringAfterLast(':') }
        .filter { it.startsWith("assemble") || it.startsWith("bundle") || it == "build" }
        .distinct()
    doLast {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        println(">>> app-[${requested.joinToString(", ")}] @ $ts <<<")
    }
}

// 让所有任务都排在开始横幅之后，确保横幅始终位于日志最前
rootProject.allprojects.forEach { p ->
    p.tasks.configureEach {
        if (path != buildBanner.get().path) mustRunAfter(buildBanner)
    }
}

// CC 兼容：doLast 只捕获普通值，避免引用脚本作用域成员
tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }.configureEach {
    dependsOn(buildBanner)
    val taskName = name
    val versionName = appVersionName
    val versionCode = appVersionCode
    doLast {
        // 时间戳在任务执行期获取，仅用 JDK API，避免捕获 Script 对象
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        println(">>> app-[$taskName]: $versionName($versionCode) @ $timestamp <<<")
    }
}

val generateGitLog = tasks.register("generateGitLog") {
    description = "ChangeLog"
    // CC 兼容：文件定位与日志内容在配置期求值，doLast 仅执行写入
    val outputFile = layout.projectDirectory.file("src/main/assets/git_commits.md")
    val text = try {
        providers.exec {
            commandLine("git", "log", "--pretty=format:### %s%n%b%n")
            workingDir(rootProject.projectDir)
        }.standardOutput.asText.get()
    } catch (_: Exception) {
        "# 暂无提交记录"
    }
    doLast {
        val file = outputFile.asFile
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }
}

tasks.named("preBuild") {
    dependsOn(generateGitLog)
}

android {
    namespace = "dev.huidoudour.installer"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    ndkVersion = "30.0.14904198"

    defaultConfig {
        applicationId = "io.github.huidoudour.Installer"
        minSdk = 28
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                abiFilters += setOf( "arm64-v8a" , "x86_64" )
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

    buildFeatures {
        viewBinding = true
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
        )
        // 仅检查致命错误
        checkOnly += setOf(
            "NotSibling",              // 必须检查布局引用错误
            "DuplicateIds",            // 必须检查重复ID
            "UnknownId"                // 必须检查未知ID引用
        )
    }
}

// 直接定义本地构建产物文件名（无需再靠 CI 脚本重命名）：
//   release → Installer-universal-R-<hash>.apk
//   debug   → Installer-universal-D-<hash>.apk
// outputFileName 等变体 API 目前被 AGP 标记为 @Incubating，按项目规范抑制该警告
@Suppress("UnstableApiUsage")
androidComponents {
    onVariants { variant ->
        val suffix = if (variant.buildType == "release") "R" else "D"
        variant.outputs.forEach { output ->
            output.outputFileName.set("Installer-universal-$suffix-$gitShortHash.apk")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
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
    implementation(libs.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

    debugImplementation(libs.compose.ui.tooling.preview)

    implementation(libs.material.kolor)

    // ====== 必要依赖开始 ======
    // Hidden API for Dhizuku binder wrapper
    compileOnly(project(":hidden-api"))
    // Shizuku api/provider
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // 原版 Dhizuku API（用于 com.rosan.dhizuku）
    implementation("io.github.iamr0s:Dhizuku-API:2.6.0")

    // 绕过隐式 API
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    implementation("androidx.core:core-ktx:1.19.0")

    // APK 签名校验（apksig：完整校验 v1/v2/v3/v3.1/v4 签名方案）
    implementation(libs.apksig)

    // ====== 必要依赖结束 ======
    // 测试依赖
    testImplementation("junit:junit:4.13.2")
    // MTDataFilesProvider
    //debugImplementation("com.github.L-JINBIN:MTDataFilesProvider:v1.0.0")
    implementation(project(":mt-provider"))

    val localFile = file("libs/android.aar")
    if (localFile.exists()) {
        debugImplementation(files(localFile))
    }
}
