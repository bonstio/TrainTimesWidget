import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val keystoreProps = Properties().apply {
    val propFiles = listOf(
        rootProject.file("keystore.properties"),
        file("/mnt/nas/docs/Development/play-store-traintimes/keystore.properties"),
        file(System.getProperty("user.home") + "/.gemini/play-store/keystore-windows.properties")
    )
    propFiles.firstOrNull { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "net.bonstio.traintimes"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "net.bonstio.traintimes"
        minSdk = 33
        targetSdk = 36
        versionCode = 56
        versionName = "3.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val defaultApiKey = (project.findProperty("DEFAULT_RAIL_DATA_API_KEY") as? String ?: "")
            .trim()
            .trim('"')
        buildConfigField("String", "DEFAULT_RAIL_DATA_API_KEY", "\"$defaultApiKey\"")
    }

    signingConfigs {
        create("release") {
            val ksPath = keystoreProps.getProperty("KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank()) {
                val ksFile = file(ksPath)
                if (ksFile.exists()) {
                    storeFile = ksFile
                    storePassword = keystoreProps.getProperty("KEYSTORE_PASSWORD")
                    keyAlias = keystoreProps.getProperty("KEY_ALIAS")
                    keyPassword = keystoreProps.getProperty("KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Train Times (Dev)")
        }
        release {
            resValue("string", "app_name", "Train Times")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSign = signingConfigs.getByName("release")
            signingConfig = if (releaseSign.storeFile?.exists() == true) {
                releaseSign
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("io.ktor:ktor-client-core:2.3.5")
    // CHANGE: Switched from CIO to Android engine
    implementation("io.ktor:ktor-client-android:2.3.5")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
    implementation("io.ktor:ktor-serialization-kotlinx-xml:2.3.5")
    
    implementation("com.google.android.gms:play-services-location:21.1.0")

    implementation(libs.androidx.work.runtime.ktx)
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.emoji2:emoji2-bundled:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}