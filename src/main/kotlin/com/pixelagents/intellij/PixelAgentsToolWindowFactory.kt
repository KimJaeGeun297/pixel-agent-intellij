package com.pixelagents.intellij

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.content.ContentFactory

class PixelAgentsToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val serverManager = project.service<ServerManager>()

        // Try to start server automatically; fall back to default port
        val port = if (!serverManager.isRunning) {
            val started = serverManager.start()
            if (started > 0) started else DEFAULT_PORT
        } else {
            serverManager.port
        }

        // Create JCEF browser panel
        val browser = JBCefBrowser("http://localhost:$port")

        val content = ContentFactory.getInstance().createContent(
            browser.component,
            "Office",
            false
        )
        content.setDisposer(browser)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    companion object {
        const val DEFAULT_PORT = 3000
    }
}
