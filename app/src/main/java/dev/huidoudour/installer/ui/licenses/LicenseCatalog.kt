package dev.huidoudour.installer.ui.licenses

/**
 * 一条开源许可信息。
 *
 * @param name 项目/库名称
 * @param license 许可证标识（如 `Apache-2.0`）；为 null 表示上游未声明许可证
 * @param url 点击后打开的项目主页或许可证原文地址
 */
data class LicenseEntry(
    val name: String,
    val license: String?,
    val url: String,
)

/**
 * 应用内「开源许可」页面的静态清单。
 *
 * 许可证以各上游仓库声明为准（部分核对自本地 Gradle 缓存的 POM 元数据）。
 * 新增依赖时请同步补充本清单。
 */
object LicenseCatalog {

    private const val PROJECT_URL = "https://github.com/huidoudour/Installer"

    /** 本项目自身：MIT，随安装包一并分发 */
    val thisApp = listOf(
        LicenseEntry(
            name = "Installer",
            license = "MIT License",
            url = "$PROJECT_URL/blob/master/LICENSE",
        )
    )

    /** 随安装包分发的第三方开源库 */
    val dependencies = listOf(
        LicenseEntry(
            name = "AndroidX (Jetpack)",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx",
        ),
        LicenseEntry(
            name = "Material Components for Android",
            license = "Apache-2.0",
            url = "https://github.com/material-components/material-components-android",
        ),
        LicenseEntry(
            name = "Kotlin / kotlinx.coroutines",
            license = "Apache-2.0",
            url = "https://github.com/JetBrains/kotlin",
        ),
        LicenseEntry(
            name = "Shizuku API",
            license = "MIT License",
            url = "https://github.com/RikkaApps/Shizuku-API",
        ),
        LicenseEntry(
            name = "Dhizuku API",
            license = "MIT License",
            url = "https://github.com/iamr0s/Dhizuku-API",
        ),
        LicenseEntry(
            name = "HiddenApiBypass",
            license = "Apache-2.0",
            url = "https://github.com/LSPosed/AndroidHiddenApiBypass",
        ),
        LicenseEntry(
            name = "MaterialKolor",
            license = "MIT License",
            url = "https://github.com/jordond/materialkolor",
        ),
        LicenseEntry(
            name = "Accompanist DrawablePainter",
            license = "Apache-2.0",
            url = "https://github.com/google/accompanist",
        ),
        LicenseEntry(
            name = "apksig",
            license = "Apache-2.0",
            url = "https://android.googlesource.com/platform/tools/apksig",
        ),
        LicenseEntry(
            name = "Roboto Mono",
            license = "OFL-1.1",
            url = "https://github.com/googlefonts/robotomono",
        ),
        LicenseEntry(
            name = "MTDataFilesProvider",
            license = null,
            url = "https://github.com/L-JINBIN/MTDataFilesProvider",
        ),
    )

    /** 常见许可证原文入口 */
    val licenseTexts = listOf(
        LicenseEntry(
            name = "Apache License 2.0",
            license = "Apache-2.0",
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt",
        ),
        LicenseEntry(
            name = "MIT License",
            license = "MIT",
            url = "https://opensource.org/license/mit",
        ),
        LicenseEntry(
            name = "SIL Open Font License 1.1",
            license = "OFL-1.1",
            url = "https://openfontlicense.org",
        ),
    )
}
