plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ularn.android"
    compileSdk = 35

    defaultConfig {
        // Wild Edition installs beside the normal Ularn app (own saves, own icon).
        applicationId = "com.ularn.android.wild"
        minSdk = 24
        targetSdk = 35
        versionCode = 100
        versionName = "3.0-wild (Ularn 1.6.3a)"

        ndk {
            // -Pularn.abis=x86_64 (comma-separated) limits a local build to
            // fewer ABIs: the NDK build is the slow part.
            val abis = (project.findProperty("ularn.abis") as String?)
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            abiFilters += abis
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
