pluginManagement {
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral { url = uri("https://repo1.maven.org/maven2") }
        gradlePluginPortal()
    }
}
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
// }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android\\.arch.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.firebase.*")
                includeGroupByRegex("com\\.google\\.mlkit.*")
                includeGroupByRegex("com\\.google\\.ai.*")
                includeGroupByRegex("com\\.google\\.testing.*")
            }
        }
        mavenCentral { url = uri("https://repo1.maven.org/maven2") }
        maven("https://jitpack.io")
    }
}

rootProject.name = "ZeroAgent"
include(":app")
include(":highlight")
include(":ai")
include(":local-llm")
include(":llama-cpp")
include(":search")
include(":speech")
include(":common")
include(":document")
include(":web")
include(":material3")
include(":workspace")
include(":oauth")
