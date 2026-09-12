package com.nutrix.app.model

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val id: Long = 0L,
    val role: ChatRole,
    val text: String,
    val sentAtEpochMs: Long = 0L,
    val sources: List<SourceRef> = emptyList(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
)
