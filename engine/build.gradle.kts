import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Motor del juego: Kotlin puro, sin Android, para poder testearlo y compartirlo.
// Multiplataforma: la misma lógica corre en Android (JVM) y en el navegador (Wasm).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // Basta con Node para correr los tests del motor en Wasm; la web la empaqueta :shared.
        nodejs()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
