package com.andreafini.claudeplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Nodo padre in Impostazioni > Tools che raggruppa le pagine dei singoli motori
 * (Claude, ChatGPT, Gemini, OpenRouter). Non ha impostazioni proprie: le voci di
 * configurazione vere sono i figli, annidati sotto questo nodo tramite `parentId`
 * in plugin.xml. Qui mostra solo una breve introduzione.
 */
class IdeaAiPluginConfigurable : SearchableConfigurable {

    private var panel: JPanel? = null

    override fun getId(): String = ID

    override fun getDisplayName(): String = "IdeaAIPlugin"

    override fun createComponent(): JComponent {
        val built = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>IdeaAIPlugin</b> — assistenti AI per il coding in IntelliJ IDEA.</html>"))
            .addComponent(JBLabel("<html>Espandi questo nodo e scegli un motore per configurarlo:<br>" +
                "<b>Claude</b> (Anthropic), <b>ChatGPT</b> (OpenAI), <b>Gemini</b> (Google), " +
                "<b>OpenRouter</b> (aggregatore con modelli gratuiti).</html>"))
            .addComponent(JBLabel("<html>I pulsanti e la tool window di un motore compaiono solo quando " +
                "la relativa API key è configurata.</html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel
        panel = built
        return built
    }

    override fun isModified(): Boolean = false

    override fun apply() {
        // Nessuna impostazione propria: la configurazione avviene nei nodi figli.
    }

    override fun disposeUIResources() {
        panel = null
    }

    companion object {
        const val ID = "com.andreafini.claudeplugin.settings.IdeaAiPluginConfigurable"
    }
}
