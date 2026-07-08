package com.andreafini.claudeplugin.action

import com.andreafini.claudeplugin.api.ClaudeClient
import com.andreafini.claudeplugin.api.ClaudeException
import com.andreafini.claudeplugin.api.ClaudeMessage
import com.andreafini.claudeplugin.api.ClaudePricing
import com.andreafini.claudeplugin.history.ClaudeHistoryService
import com.andreafini.claudeplugin.settings.ClaudeSettings
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

/** Utility condivise dalle azioni del plugin. */
object ClaudeActionSupport {

    /**
     * Esegue la chiamata a Claude in background, registra l'interazione nella cronologia
     * e mostra il risultato in un popup (o un dialog di errore), rientrando sull'EDT.
     *
     * @param type tipo di azione ("Refactor" / "Genera")
     * @param userRequest istruzione dell'utente (per la cronologia e il contesto)
     * @param prompt prompt completo da inviare a Claude (con eventuale file allegato)
     */
    fun runRequest(
        project: Project,
        editor: Editor?,
        popupTitle: String,
        type: String,
        userRequest: String,
        prompt: String,
        stripFences: Boolean = true,
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Interrogazione di Claude…", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Attendo la risposta di Claude…"
                try {
                    val history = ClaudeHistoryService.getInstance(project)
                    val messages = buildMessages(history, prompt)
                    val raw = ClaudeClient.sendMessages(messages)
                    val result = if (stripFences) stripCodeFences(raw.text) else raw.text.trim()
                    val model = ClaudeSettings.getInstance().model
                    val info = ClaudePricing.infoLine(model, raw.inputTokens, raw.outputTokens)

                    // Registra l'interazione nella cronologia.
                    history.add(
                        ClaudeHistoryService.Interaction(
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
                        ResultPopup(project, editor, popupTitle, result, info).show()
                    }
                } catch (e: ClaudeException) {
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
    private fun buildMessages(history: ClaudeHistoryService, prompt: String): List<ClaudeMessage> {
        if (!history.useContext) {
            return listOf(ClaudeMessage("user", prompt))
        }
        val messages = mutableListOf<ClaudeMessage>()
        // Solo le interazioni della conversazione attiva: prendo le ultime N e le riordino.
        val recent = history.conversation(history.activeConversationId)
            .take(ClaudeHistoryService.CONTEXT_WINDOW)
            .reversed()
        for (it in recent) {
            if (it.request.isNotBlank()) messages.add(ClaudeMessage("user", it.request))
            if (it.response.isNotBlank()) messages.add(ClaudeMessage("assistant", it.response))
        }
        messages.add(ClaudeMessage("user", prompt))
        return messages
    }

    /** Mostra un dialog di errore modale (compare sempre, non dipende da gruppi di notifica). */
    fun notifyError(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(project, message, "Claude Assistant")
        }
    }

    /**
     * Inserisce [text] nell'editor: se [editor] è null usa l'editor attivo del progetto.
     * Se c'è una selezione la sostituisce, altrimenti inserisce al cursore.
     * Restituisce true se l'inserimento è avvenuto.
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
     * Rimuove gli eventuali recinti markdown (```lang … ```) che Claude
     * potrebbe aggiungere attorno al codice, restituendo solo il contenuto.
     */
    fun stripCodeFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed

        val lines = trimmed.lines().toMutableList()
        // Rimuove la prima riga di apertura (```  oppure ```java, ```kotlin, ...)
        lines.removeAt(0)
        // Rimuove l'ultima riga se è la chiusura ```
        if (lines.isNotEmpty() && lines.last().trim() == "```") {
            lines.removeAt(lines.size - 1)
        }
        return lines.joinToString("\n").trim()
    }
}
