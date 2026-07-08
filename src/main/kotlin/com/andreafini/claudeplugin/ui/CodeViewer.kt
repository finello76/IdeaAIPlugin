package com.andreafini.claudeplugin.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField

/** Crea editor in sola lettura con evidenziazione sintassi e font monospazio dell'IDE. */
object CodeViewer {

    fun create(
        project: Project,
        text: String,
        fileType: FileType = PlainTextFileType.INSTANCE,
        softWraps: Boolean = false,
    ): EditorTextField {
        val viewer = object : EditorTextField(text, project, fileType) {
            override fun createEditor(): EditorEx {
                val ed = super.createEditor()
                ed.isViewer = true
                ed.setVerticalScrollbarVisible(true)
                ed.setHorizontalScrollbarVisible(true)
                ed.settings.isLineNumbersShown = true
                // Con soft wrap attivo il testo lungo va a capo invece di espandersi
                // orizzontalmente: più leggibile nel pannello laterale.
                ed.settings.isUseSoftWraps = softWraps
                ed.colorsScheme = EditorColorsManager.getInstance().globalScheme
                return ed
            }
        }
        viewer.setOneLineMode(false)
        // Usa il font monospazio dell'editor invece del font dell'interfaccia.
        viewer.setFontInheritedFromLAF(false)
        return viewer
    }
}
