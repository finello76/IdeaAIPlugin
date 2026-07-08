package com.andreafini.claudeplugin.toolwindow

import com.andreafini.claudeplugin.settings.GeminiSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/** Crea la tool window laterale "Gemini" con la cronologia delle interazioni. */
class GeminiToolWindowFactory : ToolWindowFactory {

    /**
     * La tool window compare solo se la API key di Gemini è configurata.
     * Valutata all'apertura del progetto: se la key viene aggiunta dopo, riapri il progetto.
     */
    override fun isApplicable(project: Project): Boolean =
        GeminiSettings.getInstance().apiKey.isNotBlank()

    override fun shouldBeAvailable(project: Project): Boolean =
        GeminiSettings.getInstance().apiKey.isNotBlank()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = GeminiHistoryPanel(project)
        val content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
