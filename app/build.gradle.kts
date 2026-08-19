import java.time.LocalDate

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.luafabric.studio.falling"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.luafabric.studio.falling"
        minSdk = 24
        targetSdk = 36
        versionCode = 26081903
        versionName = "26.08.19-gamma"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime()}\"")
        buildConfigField("String", "COPYRIGHT_YEAR", "\"${getCurrentYear()}\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DANDROID_ARM_MODE=arm",
                    "-DANDROID_ARM_NEON=TRUE",
                    "-DANDROID_TOOLCHAIN=clang",
                    "-DANDROID_LD=lld",
                    "-DANDROID=ON",
                    "-DCMAKE_BUILD_TYPE=${if (gradle.startParameter.taskNames.any { it.contains("Release") }) "Release" else "Debug"}"
                )

                val defaultBuildType =
                    if (gradle.startParameter.taskNames.any { it.contains("Release") }) "release" else "debug"
                arguments += listOf("-DANDROID_BUILD_TYPE=${defaultBuildType}")

                cFlags += listOf("-std=c99", "-pipe", "-fno-strict-aliasing")
                cppFlags += listOf("-std=c++11", "-pipe")
            }
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
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")

            externalNativeBuild {
                cmake {
                    arguments += listOf("-DANDROID_BUILD_TYPE=release")
                    cFlags += listOf("-DRELEASE_BUILD")
                    cppFlags += listOf("-DRELEASE_BUILD")
                }
            }
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
            isJniDebuggable = true

            externalNativeBuild {
                cmake {
                    arguments += listOf("-DANDROID_BUILD_TYPE=debug")
                    cFlags += listOf("-DDEBUG_BUILD")
                    cppFlags += listOf("-DDEBUG_BUILD")
                }
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin",
                "**/*.proto",
                "**/kotlin/**",
                "**/*.version",
                "**/androidsupportmultidexversion.txt"
            )
        }
        jniLibs {
            useLegacyPackaging = true
            excludes += listOf(
                "**/libc++_shared.so",
                "**/libc++_static.a",
                "**/*.a"
            )
        }
    }
}

// 工具函数
fun getBuildTime(): String = try {
    System.currentTimeMillis().toString()
} catch (e: Exception) {
    "1735651200000"
}

fun getCurrentYear(): String = LocalDate.now().year.toString()

// 复制 core.apk 到 assets
tasks.register<Copy>("copyCoreApkToAssets") {
    dependsOn(":core-apk:assembleRelease")

    from(project(":core-apk").layout.buildDirectory.file("outputs/apk/release/core-apk-release.apk"))
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "core.apk" }
}

tasks.named("preBuild") {
    dependsOn("copyCoreApkToAssets")
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // Module Dependencies
    api(project(":editor"))
    api(project(":core"))
    api(project(":signer"))

    // Compose Core
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.material3)
    api(libs.compose.material3.window.size)
    api(libs.compose.material.icons.extended)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    api(libs.compose.animation.graphics)

    // AndroidX Core & Lifecycle
    api(libs.core.ktx)
    api(libs.core)
    api(libs.activity.compose)
    api(libs.lifecycle.runtime.ktx)
    api(libs.lifecycle.viewmodel.compose)

    // Navigation
    api(libs.navigation.compose)
    api(libs.navigation.common)
    api(libs.navigation.fragment)
    api(libs.navigation.runtime)
    api(libs.navigation.ui)

    // DataStore
    api(libs.datastore.preferences)

    // Material & Accompanist
    api(libs.material)
    api(libs.accompanist.permissions)

    // Third-party Libraries
    api(libs.compose.scrollbars) {
        exclude(group = "androidx.compose", module = "compose-bom")
    }
    api(libs.coil.compose)
    api(libs.gson)
    api(libs.ktoast)
    
    api("org.eclipse.jdt:ecj:3.33.0")
    api("com.android.tools:r8:8.2.42")
    api("io.github.kyant0:backdrop-android:2.0.0-alpha01")

}
