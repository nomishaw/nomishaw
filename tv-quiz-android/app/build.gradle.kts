plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nomishaw.tvquiz"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nomishaw.tvquiz"
        minSdk = 24
        targetSdk = 33
        versionCode = 3
        versionName = "1.2"
    }

    // A fixed key committed with the project so every CI build can be installed
    // over the previous one. Fine for a family sideload; don't reuse it elsewhere.
    signingConfigs {
        create("shared") {
            storeFile = file("tvquiz.keystore")
            storePassword = "tvquiz123"
            keyAlias = "tvquiz"
            keyPassword = "tvquiz123"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("shared") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
