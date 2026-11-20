plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.feng.filesios27"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.feng.filesios27"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.3.1"
    }
    signingConfigs {
        create("feng") {
            storeFile = file("feng-files.jks")
            storePassword = "fengfiles27"
            keyAlias = "feng"
            keyPassword = "fengfiles27"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("feng") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("feng")
        }
    }
    androidResources { noCompress += listOf("ttf", "otf") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
}
