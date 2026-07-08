package com.andreafini.claudeplugin.action

import com.andreafini.claudeplugin.settings.ClaudeSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager

/**
 * Pulsante 3 (Claude): analizza il codice per individuare errori, bug e possibili
 * miglioramenti. Se c'è una selezione analizza solo quella, altrimenti l'intero file.
 */
class AnalyzeCodeAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.getData(CommonDataKeys.EDITOR) != null &&
            ClaudeSettings.getInstance().apiKey.isNotBlank()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val document = editor.document
        val selected = editor.selectionModel.selectedText
        val scope = if (!selected.isNullOrBlank()) "selezione" else "file"
        val code = if (!selected.isNullOrBlank()) selected else document.text
        if (code.isBlank()) {
            ClaudeActionSupport.notifyError(project, "Non c'è codice da analizzare.")
            return
        }
        val fileName = FileDocumentManager.getInstance().getFile(document)?.name ?: "file"

        val prompt = AnalyzePrompt.build(scope, fileName, code)
        ClaudeActionSupport.runRequest(
            project, editor, "Claude: Analizza codice",
            type = "Analizza",
            userRequest = "Analisi ${if (scope == "selezione") "della selezione" else "del file $fileName"}",
            prompt = prompt,
            stripFences = false,
        )
    }
}

/** Costruisce il prompt di analisi, condiviso dai tre provider. */
object AnalyzePrompt {
    fun build(scope: String, fileName: String, code: String): String = buildString {
        appendLine("Analizza il seguente codice (${if (scope == "selezione") "porzione selezionata" else "intero file"}).")
        appendLine("Individua errori, bug potenziali, code smell e possibili miglioramenti.")
        appendLine("Rispondi in italiano con un elenco puntato chiaro; per ogni punto indica:")
        appendLine("- la gravità (Errore / Avviso / Suggerimento);")
        appendLine("- il problema e, se utile, come correggerlo (anche con un breve frammento di codice).")
        appendLine("Se non trovi problemi rilevanti, dillo esplicitamente.")
        appendLine()
        appendLine("File ($fileName):")
        appendLine("```")
        appendLine(code)
        append("```")
    }
}
