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
 * Dialog di input riutilizzabile per le azioni del plugin.
 *
 * @param dialogTitle titolo della finestra
 * @param labelText etichetta sopra il campo di testo
 * @param placeholder testo di suggerimento nel campo (facoltativo)
 */
class PromptDialog(
    project: Project,
    private val dialogTitle: String,
    private val labelText: String,
    private val placeholder: String = "",
) : DialogWrapper(project) {

    private val promptArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 6
    }

    init {
        title = dialogTitle
        init()
    }

    override fun createCenterPanel(): JComponent {
        val scroll = JBScrollPane(promptArea)
        scroll.preferredSize = Dimension(500, 160)
        val builder = FormBuilder.createFormBuilder()
            .addComponent(JBLabel(labelText))
            .addComponent(scroll)
        if (placeholder.isNotBlank()) {
            builder.addComponent(JBLabel(placeholder))
        }
        return builder.panel
    }

    override fun getPreferredFocusedComponent(): JComponent = promptArea

    val prompt: String
        get() = promptArea.text.trim()
}
