package com.nutrix.app.data.repository

import com.nutrix.app.data.local.dao.ChatDao
import com.nutrix.app.data.local.entity.ChatMessageEntity
import com.nutrix.app.data.remote.ClaudeClient
import com.nutrix.app.data.remote.ClaudeStreamEvent
import com.nutrix.app.data.remote.NutritionAi
import com.nutrix.app.model.ChatMessage
import com.nutrix.app.model.ChatRole
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientGoals
import com.nutrix.app.model.Nutrients
import com.nutrix.app.model.UserProfile
import com.nutrix.app.model.format
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject

/** Everything the assistant is allowed to know about the user, assembled fresh for each turn. */
data class ChatContext(
    val profile: UserProfile,
    val goals: NutrientGoals,
    val consumedToday: Nutrients,
    val waterMl: Int,
    val waterGoalMl: Int,
)

class ChatRepository(
    private val dao: ChatDao,
    private val claude: ClaudeClient,
) {

    fun observeMessages(): Flow<List<ChatMessage>> = dao.observeAll().map { rows ->
        rows.map { row ->
            ChatMessage(
                id = row.id,
                role = if (row.role == ChatRole.USER.name) ChatRole.USER else ChatRole.ASSISTANT,
                text = row.text,
                sentAtEpochMs = row.sentAtEpochMs,
                sources = row.sources,
            )
        }
    }

    suspend fun persist(message: ChatMessage): Long = dao.insert(
        ChatMessageEntity(
            role = message.role.name,
            text = message.text,
            sentAtEpochMs = message.sentAtEpochMs.takeIf { it > 0 } ?: System.currentTimeMillis(),
            sources = message.sources,
        ),
    )

    suspend fun clear() = dao.clear()

    /**
     * Streams one assistant turn. History is trimmed to the last [HISTORY_TURNS] messages —
     * enough for the conversation to make sense, short enough that a long-running chat does
     * not quietly grow every request.
     */
    fun ask(question: String, history: List<ChatMessage>, context: ChatContext): Flow<ClaudeStreamEvent> {
        val messages = buildMessages(question, history)
        return claude.stream(
            system = NutritionAi.chatSystemPrompt(describe(context)),
            messages = messages,
            enableWebSearch = true,
        )
    }

    private fun buildMessages(question: String, history: List<ChatMessage>): List<JsonObject> {
        val trimmed = history.filter { it.text.isNotBlank() && !it.isError }.takeLast(HISTORY_TURNS)
        return trimmed.map { message ->
            NutritionAi.textMessage(
                role = if (message.role == ChatRole.USER) "user" else "assistant",
                text = message.text,
            )
        } + NutritionAi.textMessage("user", question)
    }

    private fun describe(context: ChatContext): String = buildString {
        val profile = context.profile
        if (profile.isComplete) {
            appendLine(
                "- ${profile.ageYears}y ${profile.sex.label.lowercase()}, ${profile.weightKg} kg, " +
                    "${profile.heightCm} cm, ${profile.activityLevel.label.lowercase()}, " +
                    "goal: ${profile.bodyGoal.label.lowercase()}",
            )
            if (profile.healthConditions.isNotEmpty()) {
                appendLine("- Health conditions: ${profile.healthConditions.joinToString(", ")}")
            }
        } else {
            appendLine("- They have not filled in their profile yet.")
        }

        appendLine("- Today so far, against target:")
        for (nutrient in Nutrient.headline + listOf(Nutrient.FIBER, Nutrient.SODIUM)) {
            val eaten = context.consumedToday[nutrient] ?: 0.0
            val target = context.goals.target(nutrient)
            val line = if (target != null) {
                "${nutrient.format(eaten)} of ${nutrient.format(target)}"
            } else {
                nutrient.format(eaten)
            }
            appendLine("  · ${nutrient.label}: $line")
        }

        val shortfalls = (Nutrient.vitamins + Nutrient.minerals).mapNotNull { nutrient ->
            val target = context.goals.target(nutrient) ?: return@mapNotNull null
            val eaten = context.consumedToday[nutrient] ?: 0.0
            if (target > 0 && eaten / target < 0.5) nutrient.label else null
        }
        if (shortfalls.isNotEmpty()) {
            appendLine("- Under half their target today: ${shortfalls.joinToString(", ")}")
        }
        appendLine("- Water: ${context.waterMl} ml of ${context.waterGoalMl} ml")
    }

    private companion object {
        const val HISTORY_TURNS = 20
    }
}
