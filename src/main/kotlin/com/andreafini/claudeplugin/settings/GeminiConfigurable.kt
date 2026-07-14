package com.andreafini.claudeplugin.settings

import com.andreafini.claudeplugin.toolwindow.ToolWindowAvailability
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/** Pagina di configurazione in Impostazioni > Tools > Gemini Assistant. */
class GeminiConfigurable : Configurable {

    private val apiKeyField = JBPasswordField()

    // Editabile: puoi scegliere un modello dall'elenco o digitarne uno nuovo
    // (es. un modello appena rilasciato non ancora presente nella lista).
    private val modelCombo = ComboBox(GeminiSettings.AVAILABLE_MODELS.toTypedArray()).apply {
        isEditable = true
    }
    private val maxTokensSpinner = JBIntSpinner(GeminiSettings.DEFAULT_MAX_TOKENS, 256, 64000, 512)
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Gemini"

    override fun createComponent(): JComponent {
        val builder = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("API key (Google AI Studio):"), apiKeyField, 1, false)
            .addLabeledComponent(JBLabel("Modello:"), modelCombo, 1, false)
            .addComponent(JBLabel("<html>Scegli un modello dall'elenco oppure <b>digita</b> " +
                "l'id di un modello non ancora presente (es. un modello appena rilasciato).</html>"))
            .addLabeledComponent(JBLabel("Max token risposta:"), maxTokensSpinner, 1, false)
            .addComponent(JBLabel("Limite di token della risposta (non la finestra di contesto). " +
                "Se le risposte arrivano vuote o troncate, aumentalo: i modelli con ragionamento " +
                "consumano parte di questo budget."))
        if (GeminiSettings.getInstance().isSecureStorageUnavailable()) {
            builder.addComponent(JBLabel("<html>⚠ L'archiviazione sicura delle password dell'IDE è " +
                "disattivata (Settings &gt; Appearance &amp; Behavior &gt; System Settings &gt; Passwords). " +
                "La API key verrà salvata <b>in chiaro</b> nel file di configurazione del plugin così da " +
                "sopravvivere al riavvio.</html>"))
        }
        val builtPanel = builder
            .addComponentFillVertically(JPanel(), 0)
            .panel
        panel = builtPanel
        reset()
        return builtPanel
    }

    private fun selectedModel(): String =
        (modelCombo.editor.item as? String ?: modelCombo.selectedItem as? String)
            ?.trim()
            ?.ifBlank { GeminiSettings.DEFAULT_MODEL }
            ?: GeminiSettings.DEFAULT_MODEL

    override fun isModified(): Boolean {
        val settings = GeminiSettings.getInstance()
        return String(apiKeyField.password) != settings.apiKey ||
            selectedModel() != settings.model ||
            maxTokensSpinner.number != settings.maxTokens
    }

    override fun apply() {
        val settings = GeminiSettings.getInstance()
        settings.apiKey = String(apiKeyField.password)
        settings.model = selectedModel()
        settings.maxTokens = maxTokensSpinner.number
        // Mostra/nasconde subito il pulsante laterale, senza riavviare l'IDE.
        ToolWindowAvailability.refreshAll()
    }

    override fun reset() {
        val settings = GeminiSettings.getInstance()
        apiKeyField.text = settings.apiKey
        modelCombo.selectedItem = settings.model
        maxTokensSpinner.number = settings.maxTokens
    }

    override fun disposeUIResources() {
        panel = null
    }
}
