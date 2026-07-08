package com.andreafini.claudeplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Anteprima (editabile) del prompt prima di rilanciarlo su un altro motore AI.
 * Precompilato con il prompt originale; l'utente può rivederlo o modificarlo,
 * poi confermare l'invio.
 */
class PromptReviewDialog(
    project: Project,
    engineName: String,
    initialPrompt: String,
) : DialogWrapper(project) {

    private val promptArea = JBTextArea(initialPrompt).apply {
        lineWrap = true
        wrapStyleWord = true
        caretPosition = 0
    }

    init {
        title = "Rilancia su $engineName — rivedi il prompt"
        setOKButtonText("Invia a $engineName")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val scroll = JBScrollPane(promptArea)
        scroll.preferredSize = Dimension(720, 460)
        return FormBuilder.createFormBuilder()
            .addComponent(JBLabel("Puoi rivedere o modificare il prompt prima di inviarlo:"))
            .addComponent(scroll)
            .panel
    }

    override fun getPreferredFocusedComponent(): JComponent = promptArea

    val prompt: String
        get() = promptArea.text.trim()
}
