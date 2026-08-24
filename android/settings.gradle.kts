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

rootProject.name = "SpaceMuseAI"

include(":app")
include(":core")
include(":ai")
include(":camera")

// Planned modules (see docs/architecture/mobile-architecture.md) — added
// once their corresponding roadmap phase starts, to avoid empty ceremony
// modules with no source:
//   :spatial, :design, :shopping, :budget, :visualization, :preferences, :ui
