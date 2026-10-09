plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "cn.mt2.datafilesprovider"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
