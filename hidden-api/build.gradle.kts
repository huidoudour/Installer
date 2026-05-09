plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.huidoudour.hidden_api"
    //noinspection GradleDependency
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
}
