import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing credentials are read from (in order):
//   1. keystore.properties at the repo root (gitignored, for local releases)
//   2. env vars TKEY_KEYSTORE_FILE / _PASSWORD / _KEY_ALIAS / _KEY_PASSWORD (for CI)
// If none are present, assembleRelease still produces an unsigned APK (F-Droid mode).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

val releaseKeystore = signingValue("storeFile", "TKEY_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "TKEY_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "TKEY_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "TKEY_KEY_PASSWORD")
val releaseSigningReady =
    releaseKeystore != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.tkey"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tkey"
        minSdk = 31
        targetSdk = 36
        versionCode = 11
        versionName = "0.4.5"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:ble"))
    implementation(project(":core:crypto"))
    implementation(project(":core:proto"))
    implementation(project(":core:session"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
}
