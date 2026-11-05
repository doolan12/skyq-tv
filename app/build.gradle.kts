plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.skyqlike.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.doolan.reelhouse"
        minSdk = 22          // covers older Fire TV sticks and Android TV boxes
        targetSdk = 34
        versionCode = 20
        versionName = "2.0.0"
    }

    // A permanent signing key, so new versions install over the old one.
    // It only switches on when the build has the key file and password (GitHub secrets); otherwise the default debug key is used.
    val keystorePath = System.getenv("KEYSTORE_FILE")
    val keystorePass = System.getenv("KEYSTORE_PASSWORD")
    val useOwnKey = !keystorePath.isNullOrBlank() && !keystorePass.isNullOrBlank() && file(keystorePath).exists()
    if (useOwnKey) {
        signingConfigs {
            create("reelhouse") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePass
                keyAlias = "reelhouse"
                keyPassword = keystorePass
            }
        }
    }

    buildTypes {
        if (useOwnKey) {
            getByName("debug") { signingConfig = signingConfigs.getByName("reelhouse") }
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Loads poster and logo pictures from the internet
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Video playback (the same engine the big streaming apps use)
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")

    // Android TV / Fire TV components (focus, remote-friendly widgets)
    implementation("androidx.tv:tv-material:1.0.0")
}
