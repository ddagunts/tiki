plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tkey.session"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    api(project(":core:proto"))
    api(project(":core:crypto"))
    api(project(":core:ble"))
    implementation(libs.kotlinx.coroutines.android)
}
