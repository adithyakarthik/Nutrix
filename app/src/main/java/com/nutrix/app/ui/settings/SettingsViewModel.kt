package com.nutrix.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutrix.app.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val anthropicKeySet: Boolean = false,
    val usdaKeySet: Boolean = false,
    val proxyUrl: String = "",
    val aiEnabled: Boolean = true,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        container.secrets.anthropicApiKey,
        container.secrets.usdaApiKey,
        container.secrets.proxyBaseUrl,
        container.preferences.aiEnabled,
    ) { anthropic, usda, proxy, aiEnabled ->
        SettingsUiState(
            anthropicKeySet = !anthropic.isNullOrBlank(),
            usdaKeySet = !usda.isNullOrBlank(),
            proxyUrl = proxy.orEmpty(),
            aiEnabled = aiEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun saveAnthropicKey(key: String) {
        viewModelScope.launch {
            container.secrets.setAnthropicApiKey(key)
            _message.value = if (key.isBlank()) "Claude key removed." else "Claude key saved."
        }
    }

    fun saveUsdaKey(key: String) {
        viewModelScope.launch {
            container.secrets.setUsdaApiKey(key)
            _message.value = if (key.isBlank()) "USDA key removed." else "USDA key saved."
        }
    }

    fun saveProxyUrl(url: String) {
        viewModelScope.launch {
            container.secrets.setProxyBaseUrl(url)
            _message.value = if (url.isBlank()) "Proxy cleared." else "Requests will go through your proxy."
        }
    }

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setAiEnabled(enabled) }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            container.chatRepository.clear()
            _message.value = "Conversation history deleted."
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
