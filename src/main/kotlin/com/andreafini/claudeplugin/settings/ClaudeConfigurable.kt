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

/** Pagina di configurazione in Impostazioni > Tools > Claude Assistant. */
class ClaudeConfigurable : Configurable {

    private val apiKeyField = JBPasswordField()
    private val modelCombo = ComboBox(ClaudeSettings.AVAILABLE_MODELS.toTypedArray())
    private val maxTokensSpinner = JBIntSpinner(ClaudeSettings.DEFAULT_MAX_TOKENS, 256, 64000, 512)
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "IdeaAIPlugin Claude"

    override fun createComponent(): JComponent {
        val builder = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("API key (Anthropic):"), apiKeyField, 1, false)
            .addLabeledComponent(JBLabel("Modello:"), modelCombo, 1, false)
            .addLabeledComponent(JBLabel("Max token risposta:"), maxTokensSpinner, 1, false)
            .addComponent(JBLabel("Limite di token della risposta (non la finestra di contesto). " +
                "Se le risposte arrivano vuote o troncate, aumentalo: i modelli con ragionamento " +
                "consumano parte di questo budget."))
        if (ClaudeSettings.getInstance().isSecureStorageUnavailable()) {
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

    override fun isModified(): Boolean {
        val settings = ClaudeSettings.getInstance()
        return String(apiKeyField.password) != settings.apiKey ||
            (modelCombo.selectedItem as? String ?: ClaudeSettings.DEFAULT_MODEL) != settings.model ||
            maxTokensSpinner.number != settings.maxTokens
    }

    override fun apply() {
        val settings = ClaudeSettings.getInstance()
        settings.apiKey = String(apiKeyField.password)
        settings.model = modelCombo.selectedItem as? String ?: ClaudeSettings.DEFAULT_MODEL
        settings.maxTokens = maxTokensSpinner.number
        // Mostra/nasconde subito il pulsante laterale, senza riavviare l'IDE.
        ToolWindowAvailability.refreshAll()
    }

    override fun reset() {
        val settings = ClaudeSettings.getInstance()
        apiKeyField.text = settings.apiKey
        modelCombo.selectedItem = settings.model
        maxTokensSpinner.number = settings.maxTokens
    }

    override fun disposeUIResources() {
        panel = null
    }
}
