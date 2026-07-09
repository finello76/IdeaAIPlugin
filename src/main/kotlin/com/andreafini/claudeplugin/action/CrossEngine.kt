package com.andreafini.claudeplugin.action

import com.andreafini.claudeplugin.settings.ChatGptSettings
import com.andreafini.claudeplugin.settings.ClaudeSettings
import com.andreafini.claudeplugin.settings.GeminiSettings
import com.andreafini.claudeplugin.settings.OpenRouterSettings
import com.andreafini.claudeplugin.ui.PromptReviewDialog
import com.intellij.openapi.project.Project

/**
 * Permette di rilanciare una richiesta già presente nella cronologia di un provider
 * su un altro motore AI, senza doverla riscrivere. Riusa il prompt completo salvato
 * nell'interazione (con eventuale codice/file), così la nuova richiesta è identica.
 */
object CrossEngine {

    enum class Engine(val displayName: String) {
        CLAUDE("Claude"),
        CHATGPT("ChatGPT"),
        GEMINI("Gemini"),
        OPENROUTER("OpenRouter");

        /** Vero se la API key del provider è configurata. */
        fun hasApiKey(): Boolean = when (this) {
            CLAUDE -> ClaudeSettings.getInstance().apiKey.isNotBlank()
            CHATGPT -> ChatGptSettings.getInstance().apiKey.isNotBlank()
            GEMINI -> GeminiSettings.getInstance().apiKey.isNotBlank()
            OPENROUTER -> OpenRouterSettings.getInstance().apiKey.isNotBlank()
        }
    }

    /** Motori configurati diversi da [exclude], verso cui si può rilanciare. */
    fun availableTargets(exclude: Engine): List<Engine> =
        Engine.values().filter { it != exclude && it.hasApiKey() }

    /**
     * Mostra un'anteprima editabile di [prompt], poi — se confermata — lo rilancia sul
     * motore [target] e lo registra nella sua cronologia. Il rendering resta coerente con
     * l'azione originale: le analisi ("Analizza") sono prosa in Markdown, il resto è codice.
     */
    fun resend(project: Project, target: Engine, type: String, userRequest: String, prompt: String) {
        val dialog = PromptReviewDialog(project, target.displayName, prompt)
        if (!dialog.showAndGet()) return
        val finalPrompt = dialog.prompt
        if (finalPrompt.isBlank()) return

        val markdown = type == "Analizza"
        val stripFences = !markdown
        val title = "${target.displayName}: $type"
        when (target) {
            Engine.CLAUDE -> ClaudeActionSupport.runRequest(
                project, null, title, type, userRequest, finalPrompt, stripFences, markdown,
            )
            Engine.CHATGPT -> ChatGptActionSupport.runRequest(
                project, null, title, type, userRequest, finalPrompt, stripFences, markdown,
            )
            Engine.GEMINI -> GeminiActionSupport.runRequest(
                project, null, title, type, userRequest, finalPrompt, stripFences, markdown,
            )
            Engine.OPENROUTER -> OpenRouterActionSupport.runRequest(
                project, null, title, type, userRequest, finalPrompt, stripFences, markdown,
            )
        }
    }
}
