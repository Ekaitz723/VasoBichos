import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pantallas, animación y red, comunes a Android y a la web.
// Lo que solo existe en una plataforma va en androidMain / wasmJsMain.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// APP_VERSION para el código común, sacada de gradle.properties.
val generateAppVersion by tasks.registering {
    val version = providers.gradleProperty("vasobichos.versionName")
    val out = layout.buildDirectory.dir("generated/appVersion")
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val file = out.get().file("com/vaso/bichos/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.vaso.bichos\n\n" +
                "/** Generado desde gradle.properties. */\n" +
                "const val APP_VERSION = \"${version.get()}\"\n",
        )
    }
}

kotlin {
    android {
        namespace = "com.vaso.bichos.shared"
        compileSdk = 37
        minSdk = 27
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("vasobichos")
        browser {
            commonWebpackConfig {
                outputFileName = "vasobichos.js"
                // Sin mapas de código: con Kotlin 2.4 el plugin de webpack los rechaza, y no hacen falta.
                sourceMaps = false
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppVersion)
        }
        commonMain.dependencies {
            api(project(":engine"))
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.material3)
            implementation(libs.kmp.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.auth)
            implementation(libs.firebase.database)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.androidx.activity.compose)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
