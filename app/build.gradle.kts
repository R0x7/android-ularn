plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ularn.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ularn.android"
        minSdk = 24
        targetSdk = 35
        // CI numbers each build (GITHUB_RUN_NUMBER) so every new APK is a
        // higher version than the last and Android treats it as an update.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100) ?: 4
        versionName = "2.2 (Ularn 1.6.3a)"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=none")
            }
        }
    }

    // CI signs every build with one fixed key, taken from GitHub Actions
    // secrets, so each new APK installs over the previous one (Android rejects
    // updates signed with a different key, and uninstalling deletes saved
    // games). Without those secrets (e.g. a local build) the normal debug key
    // is used.
    val ciKeystore = System.getenv("ULARN_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (ciKeystore != null) {
            create("sideload") {
                storeFile = ciKeystore
                storePassword = System.getenv("ULARN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ULARN_KEY_ALIAS") ?: "ularn"
                keyPassword = System.getenv("ULARN_KEYSTORE_PASSWORD")
            }
        }
    }
    val buildSigning = signingConfigs.findByName("sideload") ?: signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = buildSigning
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = buildSigning
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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

dependencies {
    // No third-party dependencies: framework widgets + Kotlin stdlib only.
    testImplementation("junit:junit:4.13.2")
}
