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
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
}
