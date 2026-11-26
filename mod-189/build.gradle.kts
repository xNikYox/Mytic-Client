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

// Bausteine des neuen Mytic-Designs (gui/ui, Java 8) kommen aus ../mod; nur die Zeichenfläche heißt hier anders
val sharedUi = layout.buildDirectory.dir("shared-ui")
val copySharedUi by tasks.registering(Sync::class) {
    from("../mod/src/main/java") {
        include("de/myticlegacy/client/gui/ui/**")
        // Clips: Ringpuffer und AVI-Schreiber sind reines Java 8 und für alle Versionen gleich
        include("de/myticlegacy/client/clips/ClipBuffer.java", "de/myticlegacy/client/clips/MjpegAvi.java")
        // Emotes (Katalog, Posen, Echtzeit-Verbindung) und „Beitreten“-Regeln: reines Java 8, für alle Versionen gleich
        include("de/myticlegacy/client/emotes/**", "de/myticlegacy/client/friends/JoinTarget.java")
        // Diagnose-Messung: Sampling-Profiler ist reines Java 8
        include("de/myticlegacy/client/diag/SamplingProfiler.java")
        filter { line -> line.replace("import net.minecraft.client.gui.GuiGraphics;", "import de.myticlegacy.client.compat.GuiGraphics;") }
    }
    into(sharedUi)
}
sourceSets.main { java.srcDir(sharedUi) }
tasks.compileJava { dependsOn(copySharedUi) }
tasks.matching { it.name == "sourcesJar" }.configureEach { dependsOn(copySharedUi) }

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") { expand("version" to project.version) }
}
