plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.radiocoche.radio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.radiocoche.radio"
        minSdk = 21
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }

    // Clave fija: así cada APK nueva se instala encima de la anterior sin desinstalar.
    signingConfigs {
        create("radio") {
            storeFile = file("radio.keystore")
            storePassword = "radiocoche"
            keyAlias = "radio"
            keyPassword = "radiocoche"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("radio")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("radio")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
