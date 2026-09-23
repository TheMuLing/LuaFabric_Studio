plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.luafabric.studio.falling.share"
    compileSdk = 36

    defaultConfig {
        minSdk = 29

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // 纯净交集：仅 androidx.core（LuaApplication FileProvider 用）；无 appcompat / material / compose
    api(libs.core)
}