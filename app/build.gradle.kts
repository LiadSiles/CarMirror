plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI passes the run number so every build installs as an update over the last one.
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "1").toInt()

android {
    namespace = "com.liad.carmirror"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.liad.carmirror"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        // One fixed key for every build, so new versions install over old ones
        // without uninstalling. Fine for a personal sideloaded app.
        create("carmirror") {
            storeFile = file("carmirror.jks")
            storePassword = "carmirror"
            keyAlias = "carmirror"
            keyPassword = "carmirror"
        }
    }

    // full = with touch control (Accessibility). lite = no Accessibility, so Play Protect
    // doesn't block it. Same package and key: either one installs over the other.
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            resValue("bool", "has_touch", "true")
        }
        create("lite") {
            dimension = "edition"
            resValue("bool", "has_touch", "false")
            versionNameSuffix = "-lite"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("carmirror")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("carmirror")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            // BouncyCastle jars ship duplicate metadata files.
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/BCKEY.DSA", "META-INF/BCKEY.SF",
            )
        }
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")

    // Talks to the phone's own Wireless debugging (like a computer's adb would),
    // so Car Mirror can reinstall itself marked as "installed from Google Play".
    implementation("com.github.osservatorionessuno:libadb-android-bc:3.2.0")
    implementation("org.bouncycastle:bcprov-jdk15to18:1.84")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.84")
}
