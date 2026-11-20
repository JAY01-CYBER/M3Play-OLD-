plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.seaglass.core"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        // Lowered from SeaGlass' 26 to match the app. Every API >= 26 call in SeaGlass is
        // guarded by a runtime Build.VERSION check and falls back to the lightweight tier.
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    api(libs.graphics.shapes)
}
