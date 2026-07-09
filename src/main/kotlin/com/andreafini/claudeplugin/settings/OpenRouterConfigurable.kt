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

/** Pagina di configurazione in Impostazioni > Tools > IdeaAIPlugin OpenRouter. */
class OpenRouterConfigurable : Configurable {

    private val apiKeyField = JBPasswordField()

    // Editabile: OpenRouter ha centinaia di modelli, l'utente può incollare qualsiasi id.
    private val modelCombo = ComboBox(OpenRouterSettings.AVAILABLE_MODELS.toTypedArray()).apply {
        isEditable = true
    }
    private val maxTokensSpinner = JBIntSpinner(OpenRouterSettings.DEFAULT_MAX_TOKENS, 256, 64000, 512)
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "OpenRouter"

    override fun createComponent(): JComponent {
        val builder = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("API key (OpenRouter):"), apiKeyField, 1, false)
            .addComponent(JBLabel("<html>Ottieni la key su <b>openrouter.ai/keys</b>. " +
                "Con un solo account accedi a centinaia di modelli.</html>"))
            .addLabeledComponent(JBLabel("Modello:"), modelCombo, 1, false)
            .addComponent(JBLabel("<html>Incolla qualsiasi id da <b>openrouter.ai/models</b>. " +
                "I modelli con suffisso <code>:free</code> <b>non consumano credito</b> " +
                "(hanno però un rate limit).</html>"))
            .addLabeledComponent(JBLabel("Max token risposta:"), maxTokensSpinner, 1, false)
            .addComponent(JBLabel("Limite di token della risposta (non la finestra di contesto). " +
                "Se le risposte arrivano vuote o troncate, aumentalo: i modelli con ragionamento " +
                "consumano parte di questo budget."))
        if (OpenRouterSettings.getInstance().isSecureStorageUnavailable()) {
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
            ?.ifBlank { OpenRouterSettings.DEFAULT_MODEL }
            ?: OpenRouterSettings.DEFAULT_MODEL

    override fun isModified(): Boolean {
        val settings = OpenRouterSettings.getInstance()
        return String(apiKeyField.password) != settings.apiKey ||
            selectedModel() != settings.model ||
            maxTokensSpinner.number != settings.maxTokens
    }

    override fun apply() {
        val settings = OpenRouterSettings.getInstance()
        settings.apiKey = String(apiKeyField.password)
        settings.model = selectedModel()
        settings.maxTokens = maxTokensSpinner.number
        // Mostra/nasconde subito il pulsante laterale, senza riavviare l'IDE.
        ToolWindowAvailability.refreshAll()
    }

    override fun reset() {
        val settings = OpenRouterSettings.getInstance()
        apiKeyField.text = settings.apiKey
        modelCombo.selectedItem = settings.model
        maxTokensSpinner.number = settings.maxTokens
    }

    override fun disposeUIResources() {
        panel = null
    }
}
