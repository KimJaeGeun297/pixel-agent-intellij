package com.pixelagents.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.net.ServerSocket

@Service(Service.Level.PROJECT)
class ServerManager(private val project: Project) : Disposable {
    private val log = Logger.getInstance(ServerManager::class.java)
    private var process: Process? = null
    var port: Int = 0
        private set
    var isRunning: Boolean = false
        private set

    companion object {
        private const val PLUGIN_VERSION = "0.1.0"
        private const val RESOURCE_PREFIX = "/pixel-agents-server"
    }

    fun start(): Int {
        if (isRunning) return port

        port = findAvailablePort()
        val projectPath = project.basePath ?: System.getProperty("user.home")

        val serverFile = ensureServerExtracted()
        if (serverFile == null) {
            log.warn("Pixel Agents: server extraction failed")
            return 0
        }

        val serverRoot = serverFile.parentFile.absolutePath
        val nodePath = findNodePath()

        log.info("Pixel Agents: server=${serverFile.absolutePath}, root=$serverRoot, node=$nodePath, cwd=$projectPath")

        val processBuilder = ProcessBuilder(nodePath, serverFile.absolutePath)
        processBuilder.directory(serverFile.parentFile)
        processBuilder.environment().apply {
            put("PIXEL_AGENTS_PORT", port.toString())
            put("PIXEL_AGENTS_CWD", projectPath ?: "")
            put("PIXEL_AGENTS_SERVER_ROOT", serverRoot)
            put("PATH", System.getenv("PATH") ?: "/usr/local/bin:/usr/bin:/bin")
            put("HOME", System.getProperty("user.home"))
        }
        processBuilder.redirectErrorStream(true)

        try {
            process = processBuilder.start()
            isRunning = true
            log.info("Pixel Agents: server started on port $port (pid: ${process?.pid()})")

            // Log server output in background
            Thread {
                process?.inputStream?.bufferedReader()?.forEachLine { line ->
                    log.info("Pixel Agents Server: $line")
                }
            }.apply {
                isDaemon = true
                name = "pixel-agents-server-output"
                start()
            }

            // Wait for server to be ready
            Thread.sleep(3000)

        } catch (e: Exception) {
            log.error("Pixel Agents: failed to start server", e)
            isRunning = false
        }

        return port
    }

    override fun dispose() {
        stop()
    }

    fun stop() {
        process?.let {
            log.info("Pixel Agents: stopping server (pid: ${it.pid()})")
            it.destroy()
            if (it.isAlive) {
                Thread.sleep(1000)
                it.destroyForcibly()
            }
            isRunning = false
        }
        process = null
    }

    private fun ensureServerExtracted(): File? {
        val serverDir = File(System.getProperty("user.home"), ".pixel-agents/server")
        val serverFile = File(serverDir, "index.mjs")
        val versionMarker = File(serverDir, ".version")

        if (serverFile.exists() && versionMarker.exists() &&
            versionMarker.readText().trim() == PLUGIN_VERSION
        ) {
            log.info("Pixel Agents: server already extracted at ${serverDir.absolutePath}")
            return serverFile
        }

        log.info("Pixel Agents: extracting server files to ${serverDir.absolutePath}")

        // Read manifest listing all bundled files
        val manifestStream = javaClass.getResourceAsStream("$RESOURCE_PREFIX/manifest.txt")
        if (manifestStream == null) {
            log.warn("Pixel Agents: resource manifest not found at $RESOURCE_PREFIX/manifest.txt")
            return null
        }

        val paths = manifestStream.bufferedReader().readLines().filter { it.isNotBlank() }
        log.info("Pixel Agents: extracting ${paths.size} files")

        for (relativePath in paths) {
            val resourcePath = "$RESOURCE_PREFIX/$relativePath"
            val targetFile = File(serverDir, relativePath)
            extractResource(resourcePath, targetFile)
        }

        versionMarker.writeText(PLUGIN_VERSION)
        log.info("Pixel Agents: extraction complete")

        return if (serverFile.exists()) serverFile else null
    }

    private fun extractResource(resourcePath: String, target: File) {
        val stream = javaClass.getResourceAsStream(resourcePath)
        if (stream == null) {
            log.warn("Pixel Agents: resource not found: $resourcePath")
            return
        }
        target.parentFile?.mkdirs()
        stream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun findAvailablePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }

    private fun findNodePath(): String {
        val candidates = listOf(
            "/usr/local/bin/node",
            "/opt/homebrew/bin/node",
            "${System.getProperty("user.home")}/.nvm/current/bin/node",
            "${System.getProperty("user.home")}/.volta/bin/node",
            "${System.getProperty("user.home")}/.fnm/current/bin/node",
            "node", // fallback to PATH
        )
        return candidates.firstOrNull { path ->
            if (path == "node") true else File(path).exists()
        } ?: "node"
    }
}
