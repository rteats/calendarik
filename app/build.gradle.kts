plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val buildCode = providers.gradleProperty("VERSION_CODE").orNull?.toIntOrNull() ?: 1
val buildName = providers.gradleProperty("VERSION_NAME").orNull ?: "0.1.0-dev"
// As in MPEI Lite, CI opts into R8 for the production APK only.
// Keep normal debug builds fast and easy to inspect.
val optimizeRelease = providers.gradleProperty("OPTIMIZE_RELEASE").orNull == "true"
val releaseKeystorePath = providers.environmentVariable("CALENDARIK_KEYSTORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("CALENDARIK_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("CALENDARIK_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("CALENDARIK_KEY_PASSWORD").orNull
val signingReady = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.rteats.calendarik"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rteats.calendarik"
        minSdk = 26
        targetSdk = 36
        versionCode = buildCode
        versionName = buildName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingReady) {
            create("rolling") {
                storeFile = rootProject.file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        debug {
            // Firebase config currently registers com.rteats.calendarik only.
            // A debug APK still cannot update the signed release because its certificate differs.
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = optimizeRelease
            isShrinkResources = optimizeRelease
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signingReady) signingConfig = signingConfigs.getByName("rolling")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES"
        )
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

