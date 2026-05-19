plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tkey.ble"
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
    implementation(libs.kotlinx.coroutines.android)
}
