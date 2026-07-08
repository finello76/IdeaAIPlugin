package com.andreafini.claudeplugin.ui

import com.andreafini.claudeplugin.action.ClaudeActionSupport
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Popup che mostra la risposta di Claude in un editor con evidenziazione della
 * sintassi (sola lettura), con i pulsanti "Copia" e "Inserisci nel file".
 */
class ResultPopup(
    private val project: Project,
    private val editor: Editor?,
    title: String,
    private val resultText: String,
    private val info: String = "",
) : DialogWrapper(project) {

    init {
        this.title = title
        // Non modale: permette di scorrere/leggere l'editor sottostante
        // mentre il popup con la risposta resta aperto.
        isModal = false
        init()
    }

    override fun createCenterPanel(): JComponent {
        // Determina il linguaggio dal file aperto, per l'evidenziazione della sintassi.
        val virtualFile = editor?.let { FileDocumentManager.getInstance().getFile(it.document) }
        val fileType: FileType = virtualFile?.fileType ?: PlainTextFileType.INSTANCE
        val viewer = CodeViewer.create(project, resultText, fileType)
        viewer.preferredSize = Dimension(720, 460)

        val panel = JPanel(BorderLayout())
        panel.add(viewer, BorderLayout.CENTER)
        if (info.isNotBlank()) {
            panel.add(JBLabel(info).apply { border = JBUI.Borders.empty(4) }, BorderLayout.SOUTH)
        }
        return panel
    }

    override fun createActions(): Array<Action> {
        val copyAction = object : DialogWrapperAction("Copia") {
            override fun doAction(e: ActionEvent?) {
                copyToClipboard()
            }
        }
        val insertAction = object : DialogWrapperAction("Inserisci nel file") {
            override fun doAction(e: ActionEvent?) {
                // Copia sempre negli appunti: se l'inserimento finisce nel punto
                // sbagliato, il codice resta comunque disponibile da incollare.
                copyToClipboard()
                ClaudeActionSupport.insertIntoEditor(project, editor, resultText)
                close(OK_EXIT_CODE)
            }
        }
        insertAction.isEnabled = editor != null
        return arrayOf(insertAction, copyAction, cancelAction)
    }

    private fun copyToClipboard() {
        CopyPasteManager.getInstance().setContents(StringSelection(resultText))
    }
}
