pluginManagement {
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
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
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
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
