import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Motor del juego: Kotlin puro, sin Android, para poder testearlo y compartirlo.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(libs.junit)
}
