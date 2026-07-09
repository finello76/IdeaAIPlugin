package com.andreafini.claudeplugin.action

import com.andreafini.claudeplugin.settings.OpenRouterSettings
import com.andreafini.claudeplugin.ui.PromptDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager

/** Pulsante 2 (OpenRouter): genera codice a partire da una richiesta e dal file aperto. */
class OpenRouterGenerateCodeAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.getData(CommonDataKeys.EDITOR) != null &&
            OpenRouterSettings.getInstance().apiKey.isNotBlank()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val dialog = PromptDialog(
            project,
            dialogTitle = "OpenRouter: Genera codice",
            labelText = "Descrivi il codice da generare (il file aperto è allegato come contesto):",
            placeholder = "es. aggiungi una funzione che calcola il fattoriale…",
        )
        if (!dialog.showAndGet()) return

        val request = dialog.prompt
        if (request.isBlank()) {
            OpenRouterActionSupport.notifyError(project, "Richiesta vuota.")
            return
        }

        val document = editor.document
        val fileName = FileDocumentManager.getInstance().getFile(document)?.name ?: "file"
        val fileContent = document.text

        val prompt = buildString {
            appendLine("Genera il codice richiesto tenendo conto del file attualmente aperto.")
            appendLine("Rispondi solo con il codice, senza spiegazioni e senza recinti markdown.")
            appendLine()
            appendLine("Richiesta:")
            appendLine(request)
            appendLine()
            appendLine("File aperto ($fileName):")
            appendLine("```")
            appendLine(fileContent)
            append("```")
        }

        OpenRouterActionSupport.runRequest(
            project, editor, "OpenRouter: Genera codice",
            type = "Genera", userRequest = request, prompt = prompt,
        )
    }
}
