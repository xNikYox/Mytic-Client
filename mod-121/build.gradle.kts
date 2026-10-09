// Mytic-Mod für Minecraft 1.21 bis 1.21.10 (Java 21, Mojang-Mappings). Nutzt denselben Quellcode wie die
// 1.21.11-Mod in ../mod; Abweichungen liegen in src/mc<version>/java bzw. src/mc<bereich>/java.
plugins {
    id("fabric-loom") version "1.14.10"
}

val mc = providers.gradleProperty("mc").get()
val fabricApi = mapOf(
    "1.21.10" to "0.138.4+1.21.10", "1.21.9" to "0.134.1+1.21.9", "1.21.8" to "0.136.1+1.21.8",
    "1.21.7" to "0.129.0+1.21.7", "1.21.6" to "0.128.2+1.21.6", "1.21.5" to "0.128.2+1.21.5",
    "1.21.4" to "0.119.4+1.21.4", "1.21.3" to "0.114.1+1.21.3", "1.21.2" to "0.106.1+1.21.2",
    "1.21.1" to "0.116.17+1.21.1", "1.21" to "0.102.0+1.21",
)
/** Versionsgruppen mit gleicher API, von speziell nach allgemein. */
val groups = mapOf(
    "1.21.10" to listOf("1.21.10", "1.21.9"), "1.21.9" to listOf("1.21.9"),
    "1.21.8" to listOf("1.21.8", "1.21.9"), "1.21.7" to listOf("1.21.7", "1.21.8", "1.21.9"), "1.21.6" to listOf("1.21.6", "1.21.8", "1.21.9"),
)
val patch = mc.split(".").getOrNull(2)?.toInt() ?: 0

version = "${file("../mod/build.gradle.kts").readLines().first { it.startsWith("version") }.substringAfter("\"").substringBefore("\"")}+$mc"
group = "de.myticlegacy"
base { archivesName.set("mytic-client") }

val shared = file("../mod/src/main/java")
val versionDirs = (groups[mc] ?: listOf(mc)).map { file("src/mc$it/java") }.filter { it.isDirectory }
val sharedFiltered = layout.buildDirectory.dir("shared-src")
// Umbenennungen gegenüber 1.21.11 (Mojang-Namen älterer Versionen)
val renames = buildList {
    add(Regex("""\bIdentifier\b""") to "ResourceLocation")
    add(Regex("""\.identifier\(\)""") to ".location()")
    if (patch >= 9) add(Regex("""\.renderOutline\(""") to ".submitOutline(")
}
val copyShared by tasks.registering(Sync::class) {
    inputs.property("mc", mc)
    inputs.property("renames", renames.map { it.first.pattern + "=>" + it.second })
    from(shared) {
        exclude { f -> !f.isDirectory && versionDirs.any { it.resolve(f.relativePath.pathString).exists() } }
        filter { line -> renames.fold(line) { acc, (regex, replacement) -> regex.replace(acc, replacement) } }
    }
    into(sharedFiltered)
}
sourceSets.main {
    java.setSrcDirs(versionDirs + listOf(sharedFiltered))
    resources.setSrcDirs(listOf("src/main/resources", "../mod/src/main/resources"))
}
tasks.compileJava { dependsOn(copyShared) }
tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version, "mc" to mc) }
}

repositories { mavenCentral() }

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabricApi[mc]}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
