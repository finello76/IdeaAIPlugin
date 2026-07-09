package com.andreafini.claudeplugin.action

import com.andreafini.claudeplugin.api.OpenRouterClient
import com.andreafini.claudeplugin.api.OpenRouterException
import com.andreafini.claudeplugin.api.OpenRouterMessage
import com.andreafini.claudeplugin.api.OpenRouterPricing
import com.andreafini.claudeplugin.history.OpenRouterHistoryService
import com.andreafini.claudeplugin.settings.OpenRouterSettings
import com.andreafini.claudeplugin.ui.ResultPopup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.util.UUID

/** Utility condivise dalle azioni OpenRouter del plugin. */
object OpenRouterActionSupport {

    /**
     * Esegue la chiamata a OpenRouter in background, registra l'interazione nella cronologia
     * e mostra il risultato in un popup (o un dialog di errore), rientrando sull'EDT.
     */
    fun runRequest(
        project: Project,
        editor: Editor?,
        popupTitle: String,
        type: String,
        userRequest: String,
        prompt: String,
        stripFences: Boolean = true,
        markdown: Boolean = false,
    ) {
        // Snapshot della conversazione al momento dell'avvio (siamo sull'EDT): la
        // chiamata è asincrona e la conversazione attiva potrebbe cambiare nel frattempo.
        val history = OpenRouterHistoryService.getInstance(project)
        val conversationId = history.currentConversationId()
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Interrogazione di OpenRouter…", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Attendo la risposta di OpenRouter…"
                try {
                    val messages = buildMessages(history, conversationId, prompt)
                    val raw = OpenRouterClient.sendMessages(messages)
                    val result = if (stripFences) stripCodeFences(raw.text) else raw.text.trim()
                    val model = OpenRouterSettings.getInstance().model
                    val info = OpenRouterPricing.infoLine(model, raw.inputTokens, raw.outputTokens)

                    ApplicationManager.getApplication().invokeLater {
                        // Registra l'interazione sull'EDT: la cronologia è letta e
                        // modificata anche dalla tool window (niente accessi concorrenti).
                        history.add(
                            OpenRouterHistoryService.Interaction(
                                id = UUID.randomUUID().toString(),
                                timestampMillis = System.currentTimeMillis(),
                                type = type,
                                request = userRequest,
                                response = result,
                                model = model,
                                inputTokens = raw.inputTokens,
                                outputTokens = raw.outputTokens,
                                prompt = prompt,
                            ),
                            conversationId,
                        )
                        ResultPopup(project, editor, popupTitle, result, info, markdown).show()
                    }
                } catch (e: OpenRouterException) {
                    notifyError(project, e.message ?: "Errore sconosciuto.")
                } catch (e: Throwable) {
                    notifyError(project, "Errore imprevisto: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        })
    }

    /**
     * Costruisce la lista di messaggi da inviare. Se il contesto conversazionale è attivo,
     * antepone le ultime interazioni come coppie user/assistant.
     */
    private fun buildMessages(history: OpenRouterHistoryService, conversationId: String, prompt: String): List<OpenRouterMessage> {
        if (!history.useContext) {
            return listOf(OpenRouterMessage("user", prompt))
        }
        val messages = mutableListOf<OpenRouterMessage>()
        val recent = history.conversation(conversationId)
            .take(OpenRouterHistoryService.CONTEXT_WINDOW)
            .reversed()
        for (it in recent) {
            if (it.request.isNotBlank()) messages.add(OpenRouterMessage("user", it.request))
            if (it.response.isNotBlank()) messages.add(OpenRouterMessage("assistant", it.response))
        }
        messages.add(OpenRouterMessage("user", prompt))
        return messages
    }

    /** Mostra un dialog di errore modale. */
    fun notifyError(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(project, message, "OpenRouter Assistant")
        }
    }

    /**
     * Inserisce [text] nell'editor: se [editor] è null usa l'editor attivo del progetto.
     * Se c'è una selezione la sostituisce, altrimenti inserisce al cursore.
     */
    fun insertIntoEditor(project: Project, editor: Editor?, text: String): Boolean {
        val ed = editor ?: FileEditorManager.getInstance(project).selectedTextEditor ?: return false
        WriteCommandAction.runWriteCommandAction(project) {
            val document = ed.document
            val selection = ed.selectionModel
            if (selection.hasSelection()) {
                document.replaceString(selection.selectionStart, selection.selectionEnd, text)
            } else {
                document.insertString(ed.caretModel.offset, text)
            }
        }
        return true
    }

    /**
     * Rimuove gli eventuali recinti markdown (```lang … ```) che il modello
     * potrebbe aggiungere attorno al codice, restituendo solo il contenuto.
     */
    fun stripCodeFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed

        val lines = trimmed.lines().toMutableList()
        lines.removeAt(0)
        if (lines.isNotEmpty() && lines.last().trim() == "```") {
            lines.removeAt(lines.size - 1)
        }
        return lines.joinToString("\n").trim()
    }
}
