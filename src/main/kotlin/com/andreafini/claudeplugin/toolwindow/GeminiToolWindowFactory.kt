package com.andreafini.claudeplugin.toolwindow

import com.andreafini.claudeplugin.settings.GeminiSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/** Crea la tool window laterale "Gemini" con la cronologia delle interazioni. */
class GeminiToolWindowFactory : ToolWindowFactory {

    /**
     * Stato iniziale del pulsante laterale all'apertura del progetto: visibile solo se la API
     * key di Gemini è configurata. La tool window resta comunque sempre registrata, così
     * [ToolWindowAvailability] può mostrarla/nasconderla a runtime quando la key cambia,
     * senza richiedere un riavvio.
     */
    override fun shouldBeAvailable(project: Project): Boolean =
        GeminiSettings.getInstance().apiKey.isNotBlank()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = GeminiHistoryPanel(project)
        val content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
