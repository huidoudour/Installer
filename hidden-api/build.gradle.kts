plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.huidoudour.hidden_api"
    //noinspection GradleDependency
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    //noinspection UseTomlInstead
    implementation("com.github.L-JINBIN:MTDataFilesProvider:v1.0.0")
}
