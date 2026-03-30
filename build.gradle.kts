plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.pixelagents"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2025.1")
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("251")
        untilBuild.set("253.*")
    }

    val serverBundleDir = layout.buildDirectory.dir("resources/main/pixel-agents-server")

    val copyServerFiles by registering(Copy::class) {
        from("${rootDir}/../standalone/dist/server/index.mjs")
        into(serverBundleDir)
    }

    val copyClientFiles by registering(Copy::class) {
        from("${rootDir}/../standalone/dist/client")
        into(serverBundleDir.map { it.dir("client") })
    }

    val copyAssetFiles by registering(Copy::class) {
        from("${rootDir}/../webview-ui/public/assets")
        into(serverBundleDir.map { it.dir("assets") })
    }

    val generateServerManifest by registering {
        dependsOn(copyServerFiles, copyClientFiles, copyAssetFiles)
        val bundleDir = serverBundleDir
        outputs.file(bundleDir.map { it.file("manifest.txt") })
        doLast {
            val dir = bundleDir.get().asFile
            val manifestFile = File(dir, "manifest.txt")
            val lines = mutableListOf<String>()
            dir.walkTopDown()
                .filter { it.isFile && it.name != "manifest.txt" }
                .forEach { file ->
                    lines.add(file.relativeTo(dir).path)
                }
            lines.sort()
            manifestFile.writeText(lines.joinToString("\n") + "\n")
            logger.lifecycle("Pixel Agents: generated manifest with ${lines.size} entries")
        }
    }

    processResources {
        dependsOn(generateServerManifest)
    }
}

kotlin {
    jvmToolchain(21)
}
