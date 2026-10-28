pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    // PREFER_PROJECT: el plugin de Kotlin para web añade sus propios repositorios (Node, Binaryen)
    // al proyecto raíz para descargar herramientas; con FAIL_ON_PROJECT_REPOS no compila.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VasoBichos"
include(":app")
include(":engine")
include(":shared")
 