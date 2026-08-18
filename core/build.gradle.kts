plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.luafabric.studio.falling.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 23

        vectorDrawables {
            useSupportLibrary = true
        }

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
            isShrinkResources = false

            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false

            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "DebugProbesKt.bin"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // Navigation
    api(libs.navigation.fragment)
    api(libs.navigation.ui)

    // Material Design
    api(libs.material)

    // AndroidX Misc
    api(libs.activity)
    api(libs.appcompat)
    api(libs.annotation)
    api(libs.collection)
    api(libs.constraintlayout)
    api(libs.coordinatorlayout)
    api(libs.customview)
    api(libs.documentfile)
    api(libs.drawerlayout)
    api(libs.dynamicanimation)
    api(libs.fragment)
    api(libs.gridlayout)
    api(libs.legacy.support.core.ui)
    api(libs.legacy.support.core.utils)
    api(libs.localbroadcastmanager)
    api(libs.palette)
    api(libs.preference)
    api(libs.startup.runtime)
    api(libs.swiperefreshlayout)
    api(libs.slidingpanelayout)
    api(libs.recyclerview)
    api(libs.transition)
    api(libs.window)
    api(libs.viewpager)
    api(libs.viewpager2)
    api(libs.cardview)
    api(libs.browser)

    // Networking & Parsing
    api(libs.gson)

    // Image Loading (Glide)
    api(libs.glide)
    api(libs.okhttp3.integration)

    // HTTP Client (OkHttp)
    api(libs.okhttp)
}