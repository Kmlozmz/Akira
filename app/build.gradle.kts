plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

import java.util.Properties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import com.android.build.api.artifact.SingleArtifact

val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName = appVersion.getProperty("versionName")
val appVersionCode = appVersion.getProperty("versionCode").toInt()
require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?").matches(appVersionName)) {
    "versionName must follow semantic versioning"
}
require(appVersionCode > 0) { "versionCode must be positive and increase for every published release" }

// Local configuration may point to protected credentials outside the checkout.
// CI can supply the same external file through AKIRA_SIGNING_PROPERTIES.
val keystorePropsFile = rootProject.file("keystore.properties")
val localSigningProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val signingPropertiesPath = providers.environmentVariable("AKIRA_SIGNING_PROPERTIES").orNull
    ?.takeIf { it.isNotBlank() } ?: localSigningProps.getProperty("signingPropertiesFile")
val signingPropertiesFile = signingPropertiesPath?.let { rootProject.file(it) } ?: keystorePropsFile
val keystoreProps = Properties().apply {
    if (signingPropertiesFile.exists()) signingPropertiesFile.inputStream().use { load(it) }
}
val releaseIdentity = Properties().apply {
    rootProject.file("release-signing.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val hasReleaseKeystore = (keystoreProps.getProperty("storeFile") ?: "")
    .let { storePath -> storePath.isNotBlank() && file(storePath).exists() }

if (!hasReleaseKeystore) {
    logger.warn(
        "Akira: the production signing key is unavailable " +
            "(external signing configuration + keystore). Release APK packaging is blocked."
    )
}

android {
    namespace = "com.akira.app"
    compileSdk = 36
    // Match the Flutter toolchain so native debug symbols can be stripped
    // instead of silently packaging the original unstripped libraries.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.akira.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // Package only complete Flutter runtime targets. x86-only auxiliary
        // AndroidX libraries must not advertise an unsupported 32-bit runtime.
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                storeType = keystoreProps.getProperty("storeType", "PKCS12")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    packaging.jniLibs.useLegacyPackaging = true

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    // Keep maintained regression suites versioned independently of the local,
    // historically ignored test directories.
    sourceSets {
        getByName("test").java.srcDir("src/regressionTest/kotlin")
        getByName("androidTest").java.srcDir("src/regressionAndroidTest/kotlin")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// AGP can omit validateSigningRelease when the signing config is incomplete,
// so gate packaging explicitly rather than relying on that optional task.
val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Requires the configured production key before Release packaging"
    doLast {
        require(hasReleaseKeystore) {
            "Akira Release requires the configured production keystore; Debug signing is not a fallback."
        }
        require(listOf("storePassword", "keyAlias", "keyPassword").all { keystoreProps.getProperty(it) != null }) {
            "Akira Release signing configuration is incomplete."
        }
        require(appVersionCode >= 10 && appVersionName.substringBefore('.').toInt() >= 1) {
            "The new production identity is reserved for Akira 1.0.0 onward, starting at versionCode 10."
        }
        val storeFile = file(keystoreProps.getProperty("storeFile")).canonicalFile
        require(!storeFile.toPath().startsWith(rootProject.rootDir.canonicalFile.toPath())) {
            "The production private keystore must remain outside the repository."
        }
        val expected = releaseIdentity.getProperty("certificateSha256", "").lowercase()
        require(Regex("[0-9a-f]{64}").matches(expected)) {
            "The approved public production certificate fingerprint is missing."
        }
        val storePassword = keystoreProps.getProperty("storePassword").toCharArray()
        val keyPassword = keystoreProps.getProperty("keyPassword").toCharArray()
        try {
            val store = KeyStore.getInstance(keystoreProps.getProperty("storeType", "PKCS12"))
            storeFile.inputStream().use { store.load(it, storePassword) }
            val alias = keystoreProps.getProperty("keyAlias")
            require(store.isKeyEntry(alias)) { "The configured Release alias has no private key." }
            val certificate = requireNotNull(store.getCertificate(alias)) { "The configured Release key has no certificate." }
            val actual = MessageDigest.getInstance("SHA-256")
                .digest(certificate.encoded).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            require(actual == expected) {
                "The Release signer does not match the approved Akira production identity."
            }
            require(store.getKey(alias, keyPassword) is PrivateKey) {
                "The configured production private key is unavailable."
            }
        } catch (_: GeneralSecurityException) {
            throw GradleException("Cannot open the configured production identity; check the protected signing configuration.")
        } finally {
            storePassword.fill('\u0000')
            keyPassword.fill('\u0000')
        }
    }
}
tasks.matching { it.name in setOf("packageRelease", "packageReleaseBundle", "signReleaseBundle", "assembleRelease", "bundleRelease") }
    .configureEach { dependsOn(verifyReleaseSigning) }

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("com.akira.ui:flutter_release:1.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation(project(":providers:innertube"))
    implementation("io.ktor:ktor-client-core:3.4.0")
    implementation("io.ktor:ktor-client-okhttp:3.4.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.navigation.compose)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    implementation("sh.calvin.reorderable:reorderable:3.0.0")
    // Coil
    implementation(libs.coil.compose)

    // Palette for dynamic artwork colors
    implementation(libs.androidx.palette)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

androidComponents.onVariants { variant ->
    val suffix = if (variant.buildType == "release") "" else "-${variant.name}"
    val export = tasks.register<Copy>("export${variant.name.replaceFirstChar { it.uppercase() }}Apk") {
        group = "distribution"
        description = "Exports the ${variant.name} APK with the application name and version"
        from(variant.artifacts.get(SingleArtifact.APK)) {
            include("*.apk")
            rename { "Akira-$appVersionName$suffix.apk" }
        }
        into(layout.buildDirectory.dir("distributions/${variant.name}"))
    }
    tasks.matching { it.name == "assemble${variant.name.replaceFirstChar { it.uppercase() }}" }
        .configureEach { finalizedBy(export) }
}

tasks.register("prepareGitHubRelease") {
    group = "distribution"
    description = "Builds the APK for the public GitHub release"
    dependsOn("exportReleaseApk")
    doLast {
        require(hasReleaseKeystore) { "A production signing key is required to prepare a public release" }
        val destination = layout.buildDirectory.dir("distributions/github/$appVersionName").get().asFile.apply { mkdirs() }
        destination.resolve("Akira-$appVersionName-debug.apk").delete()
        destination.resolve("Akira-update.json").delete()
        destination.resolve("SHA256SUMS.txt").delete()
        val source = layout.buildDirectory.file("distributions/release/Akira-$appVersionName.apk").get().asFile
        require(source.isFile) { "Missing APK: ${source.name}" }
        source.copyTo(destination.resolve(source.name), overwrite = true)
    }
}
