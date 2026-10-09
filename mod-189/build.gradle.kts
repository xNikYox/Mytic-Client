// Mytic-Mod für Minecraft 1.8.9 (Forge). Eigener Quellcode, weil sich die alten APIs (LWJGL 2, Java 8) stark unterscheiden.
plugins {
    java
    id("gg.essential.loom") version "1.15.51"
    id("dev.architectury.architectury-pack200") version "0.1.3"
}

// gleiche Versionsnummer wie die Fabric-Mod
version = Regex("^version = \"(.+)\"", RegexOption.MULTILINE).find(file("../mod/build.gradle.kts").readText())!!.groupValues[1] + "+1.8.9"
group = "de.myticlegacy"

base { archivesName.set("mytic-client") }

loom {
    forge { pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter()) }
}

repositories { mavenCentral() }

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.release.set(8)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:-options")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") { expand("version" to project.version) }
}
