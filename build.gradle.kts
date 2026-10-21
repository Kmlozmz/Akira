plugins {
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
