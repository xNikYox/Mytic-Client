pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.minecraftforge.net/")
        maven("https://repo.essential.gg/repository/maven-releases/")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "gg.essential.loom") useModule("gg.essential:architectury-loom:${requested.version}")
        }
    }
}
rootProject.name = "mytic-client-mod-189"
