plugins {
    id("fabric-loom") version "1.14.10"
}

version = "2.62.0"
group = "de.myticlegacy"

base { archivesName.set("mytic-client") }

repositories { mavenCentral() }

dependencies {
    minecraft("com.mojang:minecraft:1.21.11")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Selbsttest für Clips (Ringpuffer, JPEG, AVI) – reines Java ohne Minecraft, läuft bei jedem Build
val clipsTest by tasks.registering(JavaExec::class) {
    classpath = sourceSets.test.get().output + sourceSets.main.get().output
    mainClass.set("de.myticlegacy.client.clips.ClipsSelfTest")
    jvmArgs("-Djava.awt.headless=true")
}
// Selbsttest der Diagnose-Messung (Thread-Familien, Leerlauf-Erkennung, Bericht) – ebenfalls reines Java
val diagTest by tasks.registering(JavaExec::class) {
    classpath = sourceSets.test.get().output + sourceSets.main.get().output
    mainClass.set("de.myticlegacy.client.diag.DiagSelfTest")
}
tasks.check { dependsOn(clipsTest, diagTest) }
// keine JUnit-Tests – die übliche test-Aufgabe würde nur Laufzeit-Bibliotheken von Loom laden (offline nicht im Cache)
tasks.test { enabled = false }

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}
