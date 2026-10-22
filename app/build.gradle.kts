plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

import java.util.Properties
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

// Release signing uses a local keystore.properties file.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { stream -> keystoreProps.load(stream) }
}
val hasReleaseKeystore = (keystoreProps.getProperty("storeFile") ?: "")
    .let { storePath -> storePath.isNotBlank() && file(storePath).exists() }

if (!hasReleaseKeystore) {
    logger.warn(
        "Akira: no se encuentra una clave de release válida " +
            "(keystore.properties + .jks). El buildType release se firmará con la " +
            "clave de DEBUG y NO será publicable en Google Play."
    )
}

android {
    namespace = "com.akira.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.akira.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

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
            } else {
                // Degradación segura: misma clave que debug.
                storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
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

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
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

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
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
