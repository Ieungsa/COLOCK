pluginManagement {
    repositories {
        google() // Add general google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")  // JTransforms 라이브러리
    }
}

rootProject.name = "My Application"
include(":app")
