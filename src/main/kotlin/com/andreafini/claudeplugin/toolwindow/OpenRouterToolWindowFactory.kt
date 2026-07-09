package com.andreafini.claudeplugin.toolwindow

import com.andreafini.claudeplugin.settings.OpenRouterSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/** Crea la tool window laterale "OpenRouter" con la cronologia delle interazioni. */
class OpenRouterToolWindowFactory : ToolWindowFactory {

    /**
     * Stato iniziale del pulsante laterale all'apertura del progetto: visibile solo se la API
     * key di OpenRouter è configurata. La tool window resta comunque sempre registrata, così
     * [ToolWindowAvailability] può mostrarla/nasconderla a runtime quando la key cambia,
     * senza richiedere un riavvio.
     */
    override fun shouldBeAvailable(project: Project): Boolean =
        OpenRouterSettings.getInstance().apiKey.isNotBlank()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = OpenRouterHistoryPanel(project)
        val content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
