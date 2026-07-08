package com.andreafini.claudeplugin.action

import com.andreafini.claudeplugin.settings.ClaudeSettings
import com.andreafini.claudeplugin.ui.AnalysisFocus
import com.andreafini.claudeplugin.ui.AnalyzeDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager

/**
 * Pulsante 3 (Claude): analizza il codice per individuare errori, bug e possibili
 * miglioramenti. Se c'è una selezione analizza solo quella, altrimenti l'intero file.
 * Un dialog permette di scegliere il tipo di analisi e/o una richiesta specifica.
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

        val dialog = AnalyzeDialog(project)
        if (!dialog.showAndGet()) return

        val prompt = AnalyzePrompt.build(dialog.focus, dialog.specificRequest, scope, fileName, code)
        ClaudeActionSupport.runRequest(
            project, editor, "Claude: Analizza codice",
            type = "Analizza",
            userRequest = AnalyzePrompt.historyLabel(dialog.focus, dialog.specificRequest, scope, fileName),
            prompt = prompt,
            stripFences = false,
            markdown = true,
        )
    }
}

/** Costruisce il prompt di analisi (e l'etichetta di cronologia), condiviso dai tre provider. */
object AnalyzePrompt {

    fun build(
        focus: AnalysisFocus,
        specificRequest: String,
        scope: String,
        fileName: String,
        code: String,
    ): String = buildString {
        val target = if (scope == "selezione") "porzione di codice selezionata" else "intero file"
        appendLine("Analizza il seguente codice ($target).")
        appendLine(focus.instruction)
        if (focus == AnalysisFocus.DOMANDA) {
            appendLine("Domanda dell'utente: $specificRequest")
            appendLine("Rispondi in italiano in modo chiaro e conciso; includi frammenti di codice solo se utili.")
        } else {
            if (specificRequest.isNotBlank()) {
                appendLine("Tieni conto anche di questa indicazione specifica dell'utente: $specificRequest")
            }
            appendLine("Rispondi in italiano con un elenco puntato chiaro; per ogni punto indica:")
            appendLine("- la gravità (Errore / Avviso / Suggerimento);")
            appendLine("- il problema e, se utile, come correggerlo (anche con un breve frammento di codice).")
            appendLine("Se non trovi problemi rilevanti, dillo esplicitamente.")
        }
        appendLine()
        appendLine("File ($fileName):")
        appendLine("```")
        appendLine(code)
        append("```")
    }

    /** Testo sintetico salvato nella cronologia come "richiesta" dell'interazione. */
    fun historyLabel(
        focus: AnalysisFocus,
        specificRequest: String,
        scope: String,
        fileName: String,
    ): String {
        val where = if (scope == "selezione") "selezione" else "file $fileName"
        return if (focus == AnalysisFocus.DOMANDA) {
            "Domanda su $where: $specificRequest"
        } else {
            val extra = if (specificRequest.isNotBlank()) " — $specificRequest" else ""
            "Analisi ($where) · ${focus.label}$extra"
        }
    }
}
