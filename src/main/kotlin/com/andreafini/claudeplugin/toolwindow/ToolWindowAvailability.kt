package com.andreafini.claudeplugin.toolwindow

import com.andreafini.claudeplugin.settings.ChatGptSettings
import com.andreafini.claudeplugin.settings.ClaudeSettings
import com.andreafini.claudeplugin.settings.GeminiSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Riallinea a runtime la disponibilità delle tool window dei tre provider in base alla presenza
 * della relativa API key, senza bisogno di riavviare l'IDE. Le tool window restano sempre
 * registrate (vedi le factory): qui si limita a mostrare/nascondere il pulsante laterale.
 *
 * Va chiamata quando la configurazione cambia — es. dopo aver salvato la key nelle impostazioni.
 */
object ToolWindowAvailability {

    // id della tool window (come dichiarato in plugin.xml) → predicato "key configurata".
    private val TOOL_WINDOWS: List<Pair<String, () -> Boolean>> = listOf(
        "Claude" to { ClaudeSettings.getInstance().apiKey.isNotBlank() },
        "ChatGPT" to { ChatGptSettings.getInstance().apiKey.isNotBlank() },
        "Gemini" to { GeminiSettings.getInstance().apiKey.isNotBlank() },
    )

    /** Riallinea le tool window in tutti i progetti aperti (sull'EDT). */
    fun refreshAll() {
        ApplicationManager.getApplication().invokeLater {
            for (project in ProjectManager.getInstance().openProjects) {
                refresh(project)
            }
        }
    }

    private fun refresh(project: Project) {
        if (project.isDisposed) return
        val manager = ToolWindowManager.getInstance(project)
        for ((id, hasKey) in TOOL_WINDOWS) {
            val toolWindow = manager.getToolWindow(id) ?: continue
            val available = hasKey()
            if (toolWindow.isAvailable != available) {
                toolWindow.setAvailable(available, null)
            }
        }
    }
}
