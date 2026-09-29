import java.net.URI
import java.security.MessageDigest

val releaseKeystoreFile = providers.environmentVariable("KEYSTORE_FILE").orNull
val releaseKeystorePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }
val microsoftOAuthClientId = providers.gradleProperty("KEEP_SPACE_MICROSOFT_CLIENT_ID")
    .orElse(providers.environmentVariable("KEEP_SPACE_MICROSOFT_CLIENT_ID"))
    .orElse("3f3a9ce1-efd8-4c61-bd72-5fcce0aa47f6")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.smartstorage.cleaner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.keepspace.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MICROSOFT_OAUTH_CLIENT_ID", "\"${microsoftOAuthClientId.get()}\"")
        manifestPlaceholders["appAuthRedirectScheme"] = "keepspace"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Tesseract models for Thai OCR (see fetchTessdata below).
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/tessdata"))
}

/**
 * Tesseract "fast" models (Apache-2.0, ~5 MB) for reading Thai on device. Downloaded at build time
 * from a pinned tag and checked by SHA-256 rather than committed to the repository.
 */
val fetchTessdata by tasks.registering {
    val models = mapOf(
        "tha" to "294227cc2d1292b0acb28d61d4115c88252b96d466ca90b417cf4cf0c67bf07c",
        "eng" to "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
    )
    val outDir = layout.buildDirectory.dir("generated/tessdata/tessdata")
    inputs.property("models", models)
    outputs.dir(outDir)
    doLast {
        fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val dir = outDir.get().asFile.apply { mkdirs() }
        for ((lang, checksum) in models) {
            val file = File(dir, "$lang.traineddata")
            if (file.exists() && sha256(file) == checksum) continue
            URI("https://github.com/tesseract-ocr/tessdata_fast/raw/4.1.0/$lang.traineddata").toURL().openStream()
                .use { input -> file.outputStream().use { input.copyTo(it) } }
            check(sha256(file) == checksum) { "Checksum mismatch for $lang.traineddata" }
        }
    }
}
tasks.named("preBuild") { dependsOn(fetchTessdata) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.navigation.suite)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    // Bundled model: face detection runs fully on device, nothing is downloaded at runtime.
    implementation(libs.mlkit.face.detection)
    // Bundled models for reading screenshots on device (Latin script) and finding QR/boarding-pass codes.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // On-device video re-encoding for compression.
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.muxer)
    // Weekly Smart Clean reminder: a weekly slot that survives reboots.
    implementation(libs.androidx.work.runtime.ktx)
    // OneDrive uses Authorization Code + PKCE; Google Drive uses the official AuthorizationClient.
    implementation(libs.appauth)
    implementation(libs.play.services.auth)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    // On-device Thai OCR (ML Kit reads Latin only); used for receipts and Thai-looking screenshots.
    implementation(libs.tesseract4android)
    // Thai OCR spike (instrumented): compares Tesseract with ML Kit.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
