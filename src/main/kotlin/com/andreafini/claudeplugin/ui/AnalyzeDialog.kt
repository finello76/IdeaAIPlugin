package com.andreafini.claudeplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.ButtonGroup
import javax.swing.JComponent

/**
 * Tipo di analisi selezionabile. Ogni valore porta con sé l'etichetta mostrata
 * all'utente e l'istruzione da inserire nel prompt inviato al modello.
 *
 * @param requiresText se true, la richiesta libera è obbligatoria (es. "Domanda specifica").
 */
enum class AnalysisFocus(
    val label: String,
    val instruction: String,
    val requiresText: Boolean = false,
) {
    COMPLETA(
        "Analisi completa (errori, bug, miglioramenti)",
        "Esegui un'analisi completa: individua errori, bug potenziali, code smell e possibili miglioramenti.",
    ),
    BUG(
        "Bug e correttezza",
        "Concentrati esclusivamente su bug ed errori di correttezza: logica errata, casi limite, " +
            "valori null, race condition, eccezioni non gestite, off-by-one.",
    ),
    MIGLIORAMENTI(
        "Miglioramenti / refactoring",
        "Concentrati su miglioramenti e refactoring: leggibilità, struttura, naming, duplicazioni e " +
            "semplificazioni. Evidenzia i punti da migliorare senza riscrivere tutto il codice.",
    ),
    PERFORMANCE(
        "Performance",
        "Concentrati su problemi di performance ed efficienza: complessità algoritmica, allocazioni " +
            "inutili, I/O o query ripetute, cicli costosi, strutture dati inadeguate.",
    ),
    SICUREZZA(
        "Sicurezza",
        "Concentrati su vulnerabilità e problemi di sicurezza: injection, validazione degli input, " +
            "gestione di segreti/credenziali, autorizzazioni ed esposizione di dati sensibili.",
    ),
    DOMANDA(
        "Domanda specifica",
        "Rispondi alla domanda dell'utente riguardo al codice.",
        requiresText = true,
    ),
}

/**
 * Dialog dell'azione "Analizza codice": permette di scegliere il tipo di analisi
 * (preset) e, facoltativamente, di aggiungere una richiesta specifica. Con il preset
 * [AnalysisFocus.DOMANDA] la richiesta libera diventa obbligatoria.
 */
class AnalyzeDialog(project: Project) : DialogWrapper(project) {

    private val radios: Map<AnalysisFocus, JBRadioButton> = AnalysisFocus.values().associateWith {
        JBRadioButton(it.label)
    }

    private val requestArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 4
    }

    init {
        title = "Analizza codice"
        val group = ButtonGroup()
        radios.values.forEach { group.add(it) }
        radios[AnalysisFocus.COMPLETA]!!.isSelected = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val builder = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("Tipo di analisi:"))
        for (focus in AnalysisFocus.values()) {
            builder.addComponent(radios[focus]!!.apply { border = JBUI.Borders.emptyLeft(8) })
        }
        val scroll = JBScrollPane(requestArea)
        scroll.preferredSize = Dimension(520, 120)
        builder.addComponent(JBLabel("Richiesta specifica (facoltativa, obbligatoria per «Domanda specifica»):"))
        builder.addComponent(scroll)
        return builder.panel
    }

    override fun doValidate(): ValidationInfo? {
        if (focus.requiresText && specificRequest.isBlank()) {
            return ValidationInfo("Scrivi la tua domanda nel campo di testo.", requestArea)
        }
        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = requestArea

    val focus: AnalysisFocus
        get() = radios.entries.first { it.value.isSelected }.key

    val specificRequest: String
        get() = requestArea.text.trim()
}
