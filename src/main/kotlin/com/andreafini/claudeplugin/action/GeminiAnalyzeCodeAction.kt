package com.andreafini.claudeplugin.action

import com.andreafini.claudeplugin.settings.GeminiSettings
import com.andreafini.claudeplugin.ui.AnalyzeDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager

/**
 * Pulsante 3 (Gemini): analizza il codice per individuare errori, bug e possibili
 * miglioramenti. Se c'è una selezione analizza solo quella, altrimenti l'intero file.
 * Un dialog permette di scegliere il tipo di analisi e/o una richiesta specifica.
 */
class GeminiAnalyzeCodeAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.getData(CommonDataKeys.EDITOR) != null &&
            GeminiSettings.getInstance().apiKey.isNotBlank()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val document = editor.document
        val selected = editor.selectionModel.selectedText
        val scope = if (!selected.isNullOrBlank()) "selezione" else "file"
        val code = if (!selected.isNullOrBlank()) selected else document.text
        if (code.isBlank()) {
            GeminiActionSupport.notifyError(project, "Non c'è codice da analizzare.")
            return
        }
        val fileName = FileDocumentManager.getInstance().getFile(document)?.name ?: "file"

        val dialog = AnalyzeDialog(project)
        if (!dialog.showAndGet()) return

        val prompt = AnalyzePrompt.build(dialog.focus, dialog.specificRequest, scope, fileName, code)
        GeminiActionSupport.runRequest(
            project, editor, "Gemini: Analizza codice",
            type = "Analizza",
            userRequest = AnalyzePrompt.historyLabel(dialog.focus, dialog.specificRequest, scope, fileName),
            prompt = prompt,
            stripFences = false,
            markdown = true,
        )
    }
}
