package com.nutrix.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutrix.app.AppContainer
import com.nutrix.app.data.remote.ClaudeException
import com.nutrix.app.data.remote.ClaudeStreamEvent
import com.nutrix.app.data.repository.ChatContext
import com.nutrix.app.model.ChatMessage
import com.nutrix.app.model.ChatRole
import com.nutrix.app.model.Nutrients
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(private val container: AppContainer) : ViewModel() {

    val history: StateFlow<List<ChatMessage>> = container.chatRepository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    /** The reply being streamed right now; it is not in [history] until it finishes. */
    private val _pending = MutableStateFlow<ChatMessage?>(null)
    val pending: StateFlow<ChatMessage?> = _pending.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    fun setInput(value: String) {
        _input.value = value
    }

    fun clearConversation() {
        viewModelScope.launch { container.chatRepository.clear() }
    }

    fun send(question: String = _input.value) {
        val text = question.trim()
        if (text.isEmpty() || _isSending.value) return

        _input.value = ""
        _isSending.value = true

        viewModelScope.launch {
            val previous = history.value
            container.chatRepository.persist(
                ChatMessage(role = ChatRole.USER, text = text, sentAtEpochMs = System.currentTimeMillis()),
            )
            _pending.value = ChatMessage(role = ChatRole.ASSISTANT, text = "", isStreaming = true)

            val context = buildContext()
            val builder = StringBuilder()

            container.chatRepository.ask(text, previous, context)
                .catch { error ->
                    val message = (error as? ClaudeException)?.userMessage
                        ?: error.message
                        ?: "Something went wrong."
                    _pending.value = null
                    container.chatRepository.persist(
                        ChatMessage(
                            role = ChatRole.ASSISTANT,
                            text = message,
                            sentAtEpochMs = System.currentTimeMillis(),
                            isError = true,
                        ),
                    )
                }
                .collect { event ->
                    when (event) {
                        is ClaudeStreamEvent.TextDelta -> {
                            builder.append(event.text)
                            _pending.update { it?.copy(text = builder.toString()) }
                        }
                        is ClaudeStreamEvent.Completed -> {
                            if (builder.isNotBlank()) {
                                container.chatRepository.persist(
                                    ChatMessage(
                                        role = ChatRole.ASSISTANT,
                                        text = builder.toString().trim(),
                                        sentAtEpochMs = System.currentTimeMillis(),
                                        sources = event.sources,
                                    ),
                                )
                            }
                            _pending.value = null
                        }
                        is ClaudeStreamEvent.Failed -> {
                            _pending.value = null
                            container.chatRepository.persist(
                                ChatMessage(
                                    role = ChatRole.ASSISTANT,
                                    text = event.error.userMessage,
                                    sentAtEpochMs = System.currentTimeMillis(),
                                    isError = true,
                                ),
                            )
                        }
                    }
                }

            _isSending.value = false
        }
    }

    private suspend fun buildContext(): ChatContext {
        val today = LocalDate.now()
        val profile = container.profileRepository.currentProfile()
        val goals = container.profileRepository.currentGoals()
        val entries = container.diaryRepository.observeDay(today).first()
        val waterSettings = container.waterRepository.currentSettings()
        return ChatContext(
            profile = profile,
            goals = goals,
            consumedToday = Nutrients.sum(entries.map { it.nutrients }),
            waterMl = container.waterRepository.dayTotal(today),
            waterGoalMl = waterSettings.dailyGoalMl,
        )
    }

    companion object {
        /** Shown on an empty conversation so the user has somewhere to start. */
        val starters = listOf(
            "What should I eat to hit my protein target today?",
            "Am I short on anything this week?",
            "Is creatine worth taking for my goal?",
            "Cheap high-protein meals I can prep on Sunday?",
        )

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(container) }
        }
    }
}
