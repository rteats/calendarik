import java.util.Properties
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// Android Studio reads this ignored local file; CI continues to use environment variables.
val localSigningFile = rootProject.file("signing.properties")
val localSigning = Properties().apply {
    if (localSigningFile.isFile) localSigningFile.inputStream().use { load(it) }
}
fun signingValue(name: String) =
    providers.environmentVariable(name).orNull ?: localSigning.getProperty(name)

val identity = Properties().apply { rootProject.file("build-identity.properties").inputStream().use { load(it) } }
val databaseSource = rootProject.file("app/src/main/java/com/rteats/calendarik/data/CalendarDatabase.kt").readText()
val databaseVersion = Regex("version = (\\d+)").find(databaseSource)!!.groupValues[1].toInt()
val sdkProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
val sdkDir = sdkProperties.getProperty("sdk.dir") ?: providers.environmentVariable("ANDROID_HOME").orNull
val adbExecutable = sdkDir?.let { file("$it/platform-tools/adb${if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""}") }
// Provider output is rechecked on each build, including with the configuration cache.
// Reuse the phone's code so a later published release can still replace local iterations.
val devicePackage = if (providers.environmentVariable("CI").orNull != "true" && adbExecutable?.isFile == true) {
    providers.exec {
        commandLine(adbExecutable.absolutePath, "shell", "dumpsys", "package", "com.rteats.calendarik")
        isIgnoreExitValue = true
    }.standardOutput.asText.get()
} else ""
val installedCode = Regex("versionCode=(\\d+)").find(devicePackage)?.groupValues?.get(1)?.toInt() ?: 0
val installedSchema = Regex("versionName=.*[+]db(\\d+)").find(devicePackage)?.groupValues?.get(1)?.toInt() ?: 0
check(installedSchema <= databaseVersion) {
    "Phone database is v$installedSchema, checkout is v$databaseVersion. Pull the newer source before Run; keep app data."
}
val buildCode = providers.gradleProperty("VERSION_CODE").orNull?.toIntOrNull()
    ?: maxOf(identity.getProperty("BASE_VERSION_CODE").toInt(), installedCode)
val buildName = providers.gradleProperty("VERSION_NAME").orNull ?: "0.1.0-dev"
// As in MPEI Lite, CI opts into R8 for the production APK only.
// Keep normal debug builds fast and easy to inspect.
val optimizeRelease = providers.gradleProperty("OPTIMIZE_RELEASE").orNull == "true"
val releaseKeystorePath = signingValue("CALENDARIK_KEYSTORE_FILE")
val releaseStorePassword = signingValue("CALENDARIK_STORE_PASSWORD")
val releaseKeyAlias = signingValue("CALENDARIK_KEY_ALIAS")
val releaseKeyPassword = signingValue("CALENDARIK_KEY_PASSWORD")
val signingReady = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }
if (localSigningFile.isFile && !signingReady) {
    throw GradleException("signing.properties must contain all four CALENDARIK signing values")
}
if (signingReady) {
    val store = KeyStore.getInstance("PKCS12")
    rootProject.file(releaseKeystorePath!!).inputStream().use { store.load(it, releaseStorePassword!!.toCharArray()) }
    val certificate = store.getCertificate(releaseKeyAlias) ?: error("Signing alias has no certificate")
    val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }
    check(fingerprint == identity.getProperty("SIGNING_CERTIFICATE_SHA256")) { "Signing certificate differs from the installed Calendarik release key" }
}

val requireStableSigning = tasks.register("requireStableSigning") {
    inputs.property("ready", signingReady)
    doLast { check(inputs.properties["ready"] == true) { "Configure signing.properties or CALENDARIK signing environment variables before packaging an APK" } }
}
tasks.matching { it.name == "packageDebug" || it.name == "packageRelease" }.configureEach { dependsOn(requireStableSigning) }

android {
    namespace = "com.rteats.calendarik"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rteats.calendarik"
        minSdk = 26
        targetSdk = 36
        versionCode = buildCode
        versionName = "$buildName+db$databaseVersion"
        buildConfigField("int", "DATABASE_VERSION", databaseVersion.toString())
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
            // Keep the release application ID and certificate for in-place installs from Android Studio.
            versionNameSuffix = "-debug"
            if (signingReady) {
                signingConfig = signingConfigs.getByName("rolling")
            }
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
    // R8 retains only the icon vectors referenced by the release UI.
    implementation("androidx.compose.material:material-icons-extended")
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

