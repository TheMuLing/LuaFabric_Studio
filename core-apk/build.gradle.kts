//import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    //alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.luafabric.studio.falling.core"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.luafabric.studio.falling.core"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    /*sourceSets {
        getByName("main") {
            if (project.rootProject.file("app/src/main/jniLibs").exists()) {
                jniLibs.srcDirs(project.rootProject.file("app/src/main/jniLibs"))
            }
        }
    }*/

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    /* 新的 Kotlin compilerOptions 配置
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }*/

    signingConfigs {
        getByName("debug") {
            keyAlias = "luaappxcore"
            keyPassword = "luaappxcore"
            storeFile = rootProject.file("debug.keystore")
            storePassword = "luaappxcore"
        }
        create("release") {
            keyAlias = "luaappxcore"
            keyPassword = "luaappxcore"
            storeFile = rootProject.file("debug.keystore")
            storePassword = "luaappxcore"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isShrinkResources = false
            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../app/src/main/jni/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    api(project(":core"))

    // Navigation
    api(libs.navigation.fragment)
    api(libs.navigation.ui)

    // Material Design
    api(libs.material)

    // 核心UI组件
    api(libs.activity)
    api(libs.appcompat)
    api(libs.fragment)
    api(libs.constraintlayout)
    api(libs.recyclerview)
    api(libs.viewpager2)
    api(libs.coordinatorlayout)
    api(libs.swiperefreshlayout)

    // 实用组件
    api(libs.preference)
    api(libs.drawerlayout)
    api(libs.transition)

    // 网络和图片
    api(libs.gson)
    api(libs.glide)
    api(libs.okhttp)
    api(libs.okhttp3.integration)
}