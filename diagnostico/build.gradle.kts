plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.radiocoche.diagnostico"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.radiocoche.diagnostico"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Misma clave fija que la radio.
    signingConfigs {
        create("radio") {
            storeFile = file("../app/radio.keystore")
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
}
