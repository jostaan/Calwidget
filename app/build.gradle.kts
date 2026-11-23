import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.xin.clockwidget"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.xin.clockwidget"
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    // Khóa debug cố định: mọi bản build cùng chữ ký nên cài đè được lên bản cũ.
    val fixedKeystore = file("debug.keystore")
    if (fixedKeystore.exists()) {
        signingConfigs.getByName("debug") {
            storeFile = fixedKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
