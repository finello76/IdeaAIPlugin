package com.andreafini.claudeplugin.action

import com.andreafini.claudeplugin.settings.ClaudeSettings
import com.andreafini.claudeplugin.ui.PromptDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

/** Pulsante 1: rifattorizza il codice selezionato, con indicazioni facoltative. */
class RefactorAction : AnAction() {

    override fun update(e: AnActionEvent) {
        // Visibile solo se c'è un editor E la API key di Anthropic è configurata:
        // il controllo sulla selezione è fatto in actionPerformed.
        e.presentation.isEnabledAndVisible =
            e.getData(CommonDataKeys.EDITOR) != null &&
            ClaudeSettings.getInstance().apiKey.isNotBlank()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selected = editor.selectionModel.selectedText
        if (selected.isNullOrBlank()) {
            ClaudeActionSupport.notifyError(project, "Seleziona prima un blocco di codice nell'editor.")
            return
        }

        val dialog = PromptDialog(
            project,
            dialogTitle = "Claude: Refactor / Migliora",
            labelText = "Che tipo di refactor vuoi? (lascia vuoto per un miglioramento generico)",
            placeholder = "es. rendi il codice più leggibile, estrai metodi, migliora le performance…",
        )
        if (!dialog.showAndGet()) return
        val instructions = dialog.prompt

        val prompt = buildString {
            if (instructions.isBlank()) {
                appendLine("Rifattorizza e migliora il seguente codice.")
            } else {
                appendLine("Rifattorizza il seguente codice seguendo questa indicazione: $instructions")
            }
            appendLine("Mantieni lo stesso comportamento e lo stesso linguaggio.")
            appendLine("Rispondi solo con il codice, senza spiegazioni e senza recinti markdown.")
            appendLine()
            append(selected)
        }

        val userRequest = if (instructions.isBlank()) "Refactor generico" else instructions
        ClaudeActionSupport.runRequest(
            project, editor, "Claude: Refactor / Migliora",
            type = "Refactor", userRequest = userRequest, prompt = prompt,
        )
    }
}
