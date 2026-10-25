buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Keep the AGP toolchain on patched libraries without changing the app's runtime.
        classpath(platform("io.netty:netty-bom:4.1.139.Final"))
        classpath("com.android.tools:r8:9.3.35")
        constraints {
            add("classpath", "org.apache.commons:commons-compress:1.28.0")
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.7")
            add("classpath", "org.jdom:jdom2:2.0.6.1")
            listOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on").forEach { module ->
                add("classpath", "org.bouncycastle:$module:1.85")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
