plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val buildCode = providers.gradleProperty("VERSION_CODE").orNull?.toIntOrNull() ?: 1
val buildName = providers.gradleProperty("VERSION_NAME").orNull ?: "0.1.0-dev"
val store = providers.environmentVariable("CALENDARIK_KEYSTORE_FILE").orNull
val storePassword = providers.environmentVariable("CALENDARIK_STORE_PASSWORD").orNull
val alias = providers.environmentVariable("CALENDARIK_KEY_ALIAS").orNull
val keyPassword = providers.environmentVariable("CALENDARIK_KEY_PASSWORD").orNull
val signingReady = listOf(store, storePassword, alias, keyPassword).all { !it.isNullOrBlank() }

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
                storeFile = file(store!!)
                storePassword = storePassword
                keyAlias = alias
                keyPassword = keyPassword
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
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
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

