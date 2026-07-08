package com.andreafini.claudeplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/** Pagina di configurazione in Impostazioni > Tools > ChatGPT Assistant. */
class ChatGptConfigurable : Configurable {

    private val apiKeyField = JBPasswordField()
    private val modelCombo = ComboBox(ChatGptSettings.AVAILABLE_MODELS.toTypedArray())
    private val maxTokensSpinner = JBIntSpinner(ChatGptSettings.DEFAULT_MAX_TOKENS, 256, 64000, 512)
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "IdeaAIPlugin ChatGPT"

    override fun createComponent(): JComponent {
        val builtPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("API key (OpenAI):"), apiKeyField, 1, false)
            .addLabeledComponent(JBLabel("Modello:"), modelCombo, 1, false)
            .addLabeledComponent(JBLabel("Max token risposta:"), maxTokensSpinner, 1, false)
            .addComponent(JBLabel("Limite di token della risposta (non la finestra di contesto). " +
                "Se le risposte arrivano vuote o troncate, aumentalo: i modelli con ragionamento " +
                "consumano parte di questo budget."))
            .addComponentFillVertically(JPanel(), 0)
            .panel
        panel = builtPanel
        reset()
        return builtPanel
    }

    override fun isModified(): Boolean {
        val settings = ChatGptSettings.getInstance()
        return String(apiKeyField.password) != settings.apiKey ||
            (modelCombo.selectedItem as? String ?: ChatGptSettings.DEFAULT_MODEL) != settings.model ||
            maxTokensSpinner.number != settings.maxTokens
    }

    override fun apply() {
        val settings = ChatGptSettings.getInstance()
        settings.apiKey = String(apiKeyField.password)
        settings.model = modelCombo.selectedItem as? String ?: ChatGptSettings.DEFAULT_MODEL
        settings.maxTokens = maxTokensSpinner.number
    }

    override fun reset() {
        val settings = ChatGptSettings.getInstance()
        apiKeyField.text = settings.apiKey
        modelCombo.selectedItem = settings.model
        maxTokensSpinner.number = settings.maxTokens
    }

    override fun disposeUIResources() {
        panel = null
    }
}
