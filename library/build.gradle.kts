import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "org.symera"
version = "1"

android {
    namespace = "org.symera.source"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.okhttp)
    compileOnly(libs.jsoup)
    compileOnly(libs.coroutines)
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.kotlinx.serialization.json.okio)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "extensions-lib"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
