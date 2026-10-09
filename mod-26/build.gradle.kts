// Mytic-Mod für Minecraft 26.x (unverschleiert, Java 25). Nutzt denselben Quellcode wie die 1.21.11-Mod
// in ../mod; versionsspezifische Abweichungen liegen in src/main/java und überschreiben gleichnamige Dateien.
plugins {
    id("net.fabricmc.fabric-loom") version "1.18-SNAPSHOT"
}

val mc = providers.gradleProperty("mc").get()
val fabricApi = mapOf("26.3" to "0.161.0+26.3", "26.2" to "0.161.0+26.2", "26.1.2" to "0.155.3+26.1.2", "26.1.1" to "0.145.4+26.1.1", "26.1" to "0.145.1+26.1")

version = "${file("../mod/build.gradle.kts").readLines().first { it.startsWith("version") }.substringAfter("\"").substringBefore("\"")}+$mc"
group = "de.myticlegacy"
base { archivesName.set("mytic-client") }

// Vorrang: src/mc<version>/ (z. B. mc26.1.2) > src/mc<major.minor>/ (z. B. mc26.1) > src/main/ > gemeinsamer Code aus ../mod
val shared = file("../mod/src/main/java")
val minor = mc.split(".").take(2).joinToString(".")
val overrideDirs = listOf("src/mc$mc/java", "src/mc$minor/java", "src/main/java").distinct().map { file(it) }.filter { it.isDirectory }
val versionDirs = overrideDirs.dropLast(1)
val overrides = file("src/main/java")
val sharedFiltered = layout.buildDirectory.dir("shared-src")
// Umbenennungen von 1.21.11 → 26.x (Zeichen-API heißt jetzt "extract", Eingabe über SDL statt GLFW)
val renames = listOf(
    Regex("""\bGuiGraphics\b""") to "GuiGraphicsExtractor",
    Regex("""\.drawString\(""") to ".text(",
    Regex("""\.drawCenteredString\(""") to ".centeredText(",
    Regex("""\.renderItem\(""") to ".item(",
    Regex("""\.renderOutline\(""") to ".outline(",
    Regex("""\brenderPanorama\(""") to "extractPanorama(",
    Regex("""void render\(GuiGraphicsExtractor (\w+), int mouseX, int mouseY, float delta\)""") to "void extractRenderState(GuiGraphicsExtractor $1, int mouseX, int mouseY, float delta)",
    Regex("""super\.render\(""") to "super.extractRenderState(",
    Regex("""\brenderBackground\(""") to "extractBackground(",
    Regex("""original\.render\(""") to "original.extractRenderState(",
    Regex("""setTooltipForNextFrame\(font, """) to "setTooltipForNextFrame(",
)
val copyShared by tasks.registering(Sync::class) {
    inputs.property("mc", mc)
    inputs.property("renames", renames.map { it.first.pattern + "=>" + it.second })
    from(shared) {
        exclude { f -> !f.isDirectory && overrideDirs.any { it.resolve(f.relativePath.pathString).exists() } }
        filter { line -> renames.fold(line) { acc, (regex, replacement) -> regex.replace(acc, replacement) } }
    }
    into(sharedFiltered)
}
val mainFiltered = layout.buildDirectory.dir("main-src")
val copyMain by tasks.registering(Sync::class) {
    from(overrides) { exclude { f -> !f.isDirectory && versionDirs.any { it.resolve(f.relativePath.pathString).exists() } } }
    into(mainFiltered)
}
sourceSets.main {
    java.setSrcDirs(versionDirs + listOf(mainFiltered, sharedFiltered))
    resources.setSrcDirs(listOf("src/main/resources", "../mod/src/main/resources"))
}
tasks.compileJava { dependsOn(copyShared, copyMain) }
tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version, "mc" to mc) }
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation("net.fabricmc.fabric-api:fabric-api:${fabricApi[mc]}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
tasks.withType<JavaCompile>().configureEach { options.release.set(25) }
