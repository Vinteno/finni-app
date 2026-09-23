pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "finni"

// core — экономика, контент и модель без Android: тестируется на JVM за секунды.
// app — интерфейс на Compose и хранение на устройстве.
include(":core", ":app")
