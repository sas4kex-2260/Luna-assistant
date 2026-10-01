plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.richa.assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.richa.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4-dev"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.alphacephei:vosk-model-en:0.3.75")
    // Real-time 3D companion rendering (glTF/GLB). The actual Waguri asset stays user-supplied.
    implementation("com.google.android.filament:filament-android:1.77.1")
    implementation("com.google.android.filament:gltfio-android:1.77.2")
    implementation("com.google.android.filament:filament-utils-android:1.77.2")
    // Offline neural TTS. Model weights are installed separately to keep the APK manageable.
    implementation("dev.ffmpegkit-maintained:kokoro-android:0.1.0")
}