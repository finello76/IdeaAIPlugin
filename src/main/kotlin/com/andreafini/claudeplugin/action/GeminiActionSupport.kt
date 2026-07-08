package com.andreafini.claudeplugin.action

import com.andreafini.claudeplugin.api.GeminiClient
import com.andreafini.claudeplugin.api.GeminiException
import com.andreafini.claudeplugin.api.GeminiMessage
import com.andreafini.claudeplugin.api.GeminiPricing
import com.andreafini.claudeplugin.history.GeminiHistoryService
import com.andreafini.claudeplugin.settings.GeminiSettings
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

/** Utility condivise dalle azioni Gemini del plugin. */
object GeminiActionSupport {

    /**
     * Esegue la chiamata a Gemini in background, registra l'interazione nella cronologia
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
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Interrogazione di Gemini…", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Attendo la risposta di Gemini…"
                try {
                    val history = GeminiHistoryService.getInstance(project)
                    val messages = buildMessages(history, prompt)
                    val raw = GeminiClient.sendMessages(messages)
                    val result = if (stripFences) stripCodeFences(raw.text) else raw.text.trim()
                    val model = GeminiSettings.getInstance().model
                    val info = GeminiPricing.infoLine(model, raw.inputTokens, raw.outputTokens)

                    // Registra l'interazione nella cronologia.
                    history.add(
                        GeminiHistoryService.Interaction(
                            id = UUID.randomUUID().toString(),
                            timestampMillis = System.currentTimeMillis(),
                            type = type,
                            request = userRequest,
                            response = result,
                            model = model,
                            inputTokens = raw.inputTokens,
                            outputTokens = raw.outputTokens,
                        )
                    )

                    ApplicationManager.getApplication().invokeLater {
                        ResultPopup(project, editor, popupTitle, result, info, markdown).show()
                    }
                } catch (e: GeminiException) {
                    notifyError(project, e.message ?: "Errore sconosciuto.")
                } catch (e: Throwable) {
                    notifyError(project, "Errore imprevisto: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        })
    }

    /**
     * Costruisce la lista di messaggi da inviare. Se il contesto conversazionale è attivo,
     * antepone le ultime interazioni come coppie user/model.
     */
    private fun buildMessages(history: GeminiHistoryService, prompt: String): List<GeminiMessage> {
        if (!history.useContext) {
            return listOf(GeminiMessage("user", prompt))
        }
        val messages = mutableListOf<GeminiMessage>()
        val recent = history.conversation(history.activeConversationId)
            .take(GeminiHistoryService.CONTEXT_WINDOW)
            .reversed()
        for (it in recent) {
            if (it.request.isNotBlank()) messages.add(GeminiMessage("user", it.request))
            if (it.response.isNotBlank()) messages.add(GeminiMessage("model", it.response))
        }
        messages.add(GeminiMessage("user", prompt))
        return messages
    }

    /** Mostra un dialog di errore modale. */
    fun notifyError(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(project, message, "Gemini Assistant")
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
     * Rimuove gli eventuali recinti markdown (```lang … ```) che Gemini
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
