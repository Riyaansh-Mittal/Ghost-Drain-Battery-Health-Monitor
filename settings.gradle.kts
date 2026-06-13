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
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
        maven { url 'https://jitpack.io' }              // MPAndroidChart
        maven { url 'https://artifacts.applovin.com/android' }  // AppLovin MAX
    }
}

rootProject.name = "Battery Health Monitor"
include(":app")
 