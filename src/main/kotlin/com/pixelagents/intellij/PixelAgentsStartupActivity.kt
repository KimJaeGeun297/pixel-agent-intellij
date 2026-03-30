package com.pixelagents.intellij

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class PixelAgentsStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Pre-start the server so it's ready when the tool window opens
        val serverManager = project.service<ServerManager>()
        serverManager.start()
    }
}
