package com.andreafini.claudeplugin.ui

import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.text.html.HTMLEditorKit

/**
 * Visualizzatore di sola lettura per testo in **Markdown** (usato dall'azione "Analizza
 * codice", il cui output è prosa e non codice). Converte un sottoinsieme di Markdown in
 * HTML e lo mostra in un [JEditorPane] con stili derivati dal tema corrente dell'IDE,
 * così titoli, grassetto, elenchi e blocchi di codice appaiono formattati.
 *
 * Il convertitore è volutamente minimale e senza dipendenze esterne: copre i costrutti
 * che i modelli producono nelle analisi (titoli, elenchi puntati/numerati, grassetto,
 * corsivo, codice inline e blocchi di codice recintati).
 */
object MarkdownViewer {

    // Segnaposto (area a uso privato Unicode) per proteggere il codice inline: non
    // compaiono mai nel testo, così i caratteri speciali del codice non vengono alterati.
    private const val CODE_OPEN = ''
    private const val CODE_CLOSE = ''

    fun create(markdown: String): JComponent {
        val pane = JEditorPane()
        pane.editorKit = HTMLEditorKit()
        pane.isEditable = false
        pane.background = UIUtil.getPanelBackground()
        pane.text = toHtml(markdown)
        pane.caretPosition = 0
        return JBScrollPane(pane)
    }

    private fun toHtml(markdown: String): String {
        val fg = ColorUtil.toHex(UIUtil.getLabelForeground())
        val codeBg = ColorUtil.toHex(ColorUtil.mix(UIUtil.getPanelBackground(), UIUtil.getLabelForeground(), 0.12))
        val fs = UIUtil.getLabelFont().size
        val body = renderBody(markdown)
        return """
            <html><head><style>
              body { font-family: sans-serif; font-size: ${fs}pt; color: #$fg; margin: 6px; }
              h1 { font-size: ${fs + 4}pt; margin: 8px 0 4px; }
              h2 { font-size: ${fs + 3}pt; margin: 8px 0 4px; }
              h3 { font-size: ${fs + 2}pt; margin: 6px 0 3px; }
              h4, h5, h6 { font-size: ${fs + 1}pt; margin: 6px 0 3px; }
              code { font-family: monospace; background: #$codeBg; padding: 0 3px; }
              pre { font-family: monospace; background: #$codeBg; padding: 6px; }
              ul, ol { margin: 4px 0 4px 18px; }
              li { margin: 2px 0; }
              p { margin: 5px 0; }
            </style></head><body>$body</body></html>
        """.trimIndent()
    }

    /** Converte il Markdown in HTML, riga per riga, raggruppando gli elenchi. */
    private fun renderBody(md: String): String {
        val out = StringBuilder()
        val lines = md.replace("\r\n", "\n").split("\n")
        var listType: String? = null

        fun closeList() {
            if (listType != null) {
                out.append("</").append(listType).append(">\n")
                listType = null
            }
        }

        var i = 0
        while (i < lines.size) {
            val trimmed = lines[i].trim()

            // Blocco di codice recintato ```...```
            if (trimmed.startsWith("```")) {
                closeList()
                i++
                val code = StringBuilder()
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.append(escape(lines[i])).append("\n")
                    i++
                }
                if (i < lines.size) i++ // salta la riga di chiusura ```
                out.append("<pre><code>").append(code).append("</code></pre>\n")
                continue
            }

            if (trimmed.isEmpty()) {
                closeList()
                i++
                continue
            }

            val heading = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
            if (heading != null) {
                closeList()
                val level = heading.groupValues[1].length
                out.append("<h$level>").append(inline(escape(heading.groupValues[2]))).append("</h$level>\n")
                i++
                continue
            }

            val unordered = Regex("^[-*+]\\s+(.*)$").find(trimmed)
            if (unordered != null) {
                if (listType != "ul") { closeList(); out.append("<ul>\n"); listType = "ul" }
                out.append("<li>").append(inline(escape(unordered.groupValues[1]))).append("</li>\n")
                i++
                continue
            }

            val ordered = Regex("^\\d+[.)]\\s+(.*)$").find(trimmed)
            if (ordered != null) {
                if (listType != "ol") { closeList(); out.append("<ol>\n"); listType = "ol" }
                out.append("<li>").append(inline(escape(ordered.groupValues[1]))).append("</li>\n")
                i++
                continue
            }

            closeList()
            out.append("<p>").append(inline(escape(trimmed))).append("</p>\n")
            i++
        }
        closeList()
        return out.toString()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /**
     * Applica la formattazione inline (codice, grassetto, corsivo). Il codice inline è
     * protetto con segnaposto univoci così i suoi caratteri `*` non vengono interpretati.
     */
    private fun inline(escaped: String): String {
        val codeSpans = mutableListOf<String>()
        var s = Regex("`([^`]+)`").replace(escaped) {
            codeSpans.add(it.groupValues[1])
            "$CODE_OPEN${codeSpans.size - 1}$CODE_CLOSE"
        }
        s = Regex("\\*\\*(.+?)\\*\\*").replace(s) { "<b>${it.groupValues[1]}</b>" }
        s = Regex("(?<!\\*)\\*(?!\\*)([^*]+?)\\*").replace(s) { "<i>${it.groupValues[1]}</i>" }
        s = Regex("$CODE_OPEN(\\d+)$CODE_CLOSE").replace(s) {
            "<code>${codeSpans[it.groupValues[1].toInt()]}</code>"
        }
        return s
    }
}
