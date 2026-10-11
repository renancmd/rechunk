plugins {
    java
}

group = "dev.hytalemodding"
version = "1.0.0"

// Machine-specific paths live in ~/.gradle/gradle.properties, not in the repo.
val hytaleServerJar = providers.gradleProperty("hytaleServerJar").orNull
    ?: throw GradleException("Set hytaleServerJar=/path/to/HytaleServer.jar in ~/.gradle/gradle.properties")
val hytaleModsDir = providers.gradleProperty("hytaleModsDir")

dependencies {
    // The server provides its own classes at runtime, so they must not be bundled.
    compileOnly(files(hytaleServerJar))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("manifest.json") {
        expand(props)
    }
}

// Copies the built jar into the Hytale mods folder.
tasks.register<Copy>("deployMod") {
    group = "hytale"
    description = "Copies the mod jar into the Hytale mods folder."
    from(tasks.jar)
    into(hytaleModsDir.orElse(provider {
        throw GradleException("Set hytaleModsDir=/path/to/UserData/Mods in ~/.gradle/gradle.properties")
    }))
}
