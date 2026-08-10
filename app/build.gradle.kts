plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing credentials are read from environment variables — never
// hardcoded and never committed. Locally, set them in your shell before
// running `./gradlew assembleRelease`. In CI (GitHub Actions), they come
// from repository Secrets (see .github/workflows/build.yml).
//
//   export SAFE_TELEGRAM_KEYSTORE_PATH=/absolute/path/to/safetelegram-release.jks
//   export SAFE_TELEGRAM_KEYSTORE_PASSWORD=...
//   export SAFE_TELEGRAM_KEY_ALIAS=safetelegram
//   export SAFE_TELEGRAM_KEY_PASSWORD=...
//
// If they're not set, the release build type simply has no signingConfig
// assigned (assembleRelease will fail loudly rather than silently falling
// back to debug-signing an unsigned/insecure build).
val releaseKeystorePath: String? = System.getenv("SAFE_TELEGRAM_KEYSTORE_PATH")
val releaseKeystorePassword: String? = System.getenv("SAFE_TELEGRAM_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("SAFE_TELEGRAM_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("SAFE_TELEGRAM_KEY_PASSWORD")
val hasReleaseSigningEnv = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.safetelegram.guard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.safetelegram.guard"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseSigningEnv) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // Sign with both v1 (JAR) and v2/v3 (APK Signature Scheme) —
                // v1 alone is legacy/deprecated; keeping it too costs nothing
                // and maximizes compatibility with very old installers.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8 (Android's modern default shrinker/obfuscator — ProGuard is
            // the legacy name still used for the rule-file syntax/tooling).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            if (hasReleaseSigningEnv) {
                signingConfig = signingConfigs.getByName("release")
            }
            // else: release build type has no signingConfig — Gradle will
            // fail assembleRelease with a clear "not signed" error instead
            // of silently producing an unsigned or debug-signed APK.
        }
        debug {
            isDebuggable = true
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // MVVM: ViewModel + StateFlow collection tied to the Activity lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
}
