package com.nutrix.app.data.remote

import com.nutrix.app.model.AnalyzedIngredient
import com.nutrix.app.model.Confidence
import com.nutrix.app.model.FoodAnalysis
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientGroup
import com.nutrix.app.model.Nutrients
import com.nutrix.app.model.UserProfile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The prompts and tool schemas Nutrix uses to turn a photo — or a bare ingredient name — into
 * numbers.
 *
 * Everything comes back through a strict tool schema rather than free text. A tool call with a
 * declared schema is the difference between a nutrition app and a chatbot that sometimes emits
 * a table: the fields are typed, the units are fixed, and a malformed answer fails loudly
 * instead of quietly logging 400 g of protein.
 */
object NutritionAi {

    const val TOOL_RECORD_MEAL = "record_meal_nutrition"
    const val TOOL_RECORD_INGREDIENT = "record_ingredient_nutrition"
    const val TOOL_REVIEW_GOALS = "record_goal_review"

    /** `{"anyOf": [{"type": "number"}, {"type": "null"}]}` — null means "genuinely not known". */
    private fun nullableNumber(description: String): JsonObject = buildJsonObject {
        put("description", description)
        putJsonArray("anyOf") {
            addJsonObject { put("type", "number") }
            addJsonObject { put("type", "null") }
        }
    }

    /** The nutrient object shared by every tool: one key per tracked nutrient, in its base unit. */
    private fun nutrientSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("description", "Amounts in each nutrient's stated unit. Use null only when a value is genuinely unknowable.")
        putJsonObject("properties") {
            Nutrient.entries.forEach { nutrient ->
                put(nutrient.key, nullableNumber("${nutrient.label} in ${nutrient.unit.label}"))
            }
        }
        putJsonArray("required") { Nutrient.entries.forEach { add(it.key) } }
        put("additionalProperties", false)
    }

    fun mealTool(): JsonObject = buildJsonObject {
        put("name", TOOL_RECORD_MEAL)
        put(
            "description",
            "Record the complete nutrition breakdown of the food in the photo. Call this exactly " +
                "once, after you have identified the dish and estimated its portion size.",
        )
        put("strict", true)
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Short name of the dish, e.g. 'Chicken biryani' or 'Greek yoghurt with berries'.")
                }
                putJsonObject("portion_grams") {
                    put("type", "number")
                    put("description", "Total edible weight of the portion shown, in grams.")
                }
                putJsonObject("portion_label") {
                    put("type", "string")
                    put("description", "How a person would describe it, e.g. '1 medium bowl (~350 g)'.")
                }
                putJsonObject("confidence") {
                    put("type", "string")
                    putJsonArray("enum") { add("high"); add("medium"); add("low") }
                    put("description", "How sure you are, mostly driven by how readable the portion size is.")
                }
                putJsonObject("summary") {
                    put("type", "string")
                    put("description", "Two sentences at most: what this dish does for the user's goals.")
                }
                putJsonObject("ingredients") {
                    put("type", "array")
                    put("description", "The components you identified and their estimated cooked weights.")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("name") { put("type", "string") }
                            putJsonObject("grams") { put("type", "number") }
                            putJsonObject("note") {
                                put("type", "string")
                                put("description", "Optional detail, e.g. 'fried in oil' or 'skin removed'. Empty string if none.")
                            }
                        }
                        putJsonArray("required") { add("name"); add("grams"); add("note") }
                        put("additionalProperties", false)
                    }
                }
                putJsonObject("warnings") {
                    put("type", "array")
                    put("description", "Anything the user should know: hidden oil, guessed portion, allergens, clashes with a stated condition. Empty array if none.")
                    putJsonObject("items") { put("type", "string") }
                }
                put("nutrients", nutrientSchema())
            }
            putJsonArray("required") {
                add("title"); add("portion_grams"); add("portion_label"); add("confidence")
                add("summary"); add("ingredients"); add("warnings"); add("nutrients")
            }
            put("additionalProperties", false)
        }
    }

    fun ingredientTool(): JsonObject = buildJsonObject {
        put("name", TOOL_RECORD_INGREDIENT)
        put("description", "Record the nutrition of a single ingredient, per 100 g as purchased/prepared.")
        put("strict", true)
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "Canonical name of the ingredient you costed, e.g. 'Chicken breast, raw, skinless'.")
                }
                putJsonObject("confidence") {
                    put("type", "string")
                    putJsonArray("enum") { add("high"); add("medium"); add("low") }
                }
                putJsonObject("source_note") {
                    put("type", "string")
                    put("description", "Where the figures come from, e.g. 'USDA FoodData Central SR Legacy 05062'.")
                }
                put("per_100g", nutrientSchema())
            }
            putJsonArray("required") { add("name"); add("confidence"); add("source_note"); add("per_100g") }
            put("additionalProperties", false)
        }
    }

    fun goalReviewTool(): JsonObject = buildJsonObject {
        put("name", TOOL_REVIEW_GOALS)
        put("description", "Record your review of the calculated daily targets for this person.")
        put("strict", true)
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("summary") {
                    put("type", "string")
                    put("description", "Two or three sentences explaining the plan in plain language.")
                }
                putJsonObject("adjustments") {
                    put("type", "array")
                    put("description", "Only the targets you would change, with the reason. Empty array if the calculated numbers are right.")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("nutrient_key") {
                                put("type", "string")
                                put("description", "One of: " + Nutrient.entries.joinToString(", ") { it.key })
                            }
                            putJsonObject("target") { put("type", "number") }
                            putJsonObject("reason") { put("type", "string") }
                        }
                        putJsonArray("required") { add("nutrient_key"); add("target"); add("reason") }
                        put("additionalProperties", false)
                    }
                }
                putJsonObject("notes") {
                    put("type", "array")
                    put("description", "Practical guidance for this person: timing, food sources, condition-specific cautions.")
                    putJsonObject("items") { put("type", "string") }
                }
            }
            putJsonArray("required") { add("summary"); add("adjustments"); add("notes") }
            put("additionalProperties", false)
        }
    }

    /** The unit contract, restated for the model so nothing is ambiguous. */
    private fun unitContract(): String = NutrientGroup.entries.joinToString("\n") { group ->
        val line = Nutrient.entries.filter { it.group == group }
            .joinToString(", ") { "${it.key} (${it.unit.label})" }
        "- ${group.label}: $line"
    }

    fun mealSystemPrompt(profile: UserProfile?): String = buildString {
        appendLine("You are the nutrition analysis engine inside Nutrix, an Android app used by")
        appendLine("bodybuilders and health-conscious people to track what they eat.")
        appendLine()
        appendLine("A user has photographed food. Your job:")
        appendLine("1. Identify the dish and its visible components.")
        appendLine("2. Estimate the edible portion weight in grams, using the plate, cutlery, hands or")
        appendLine("   packaging in frame for scale. Say so in `warnings` when there is nothing to scale against.")
        appendLine("3. Search the web for authoritative composition data — USDA FoodData Central, the")
        appendLine("   McCance & Widdowson tables, national food composition databases, or peer-reviewed")
        appendLine("   analyses — and base the numbers on what you find rather than on recall.")
        appendLine("4. Call $TOOL_RECORD_MEAL exactly once with the totals FOR THE WHOLE PORTION SHOWN,")
        appendLine("   not per 100 g.")
        appendLine()
        appendLine("Units, exactly:")
        appendLine(unitContract())
        appendLine()
        appendLine("Rules:")
        appendLine("- Cooking changes weight and density. A cooked portion is not raw weights added up.")
        appendLine("- Account for what you cannot see: oil absorbed in frying, butter in a sauce, sugar in a glaze.")
        appendLine("- Keep the macro numbers self-consistent: protein×4 + carbs×4 + fat×9 should land within")
        appendLine("  about 10% of energy_kcal. Check this before you call the tool.")
        appendLine("- Micronutrients are part of the job, not an afterthought — fill in every vitamin and")
        appendLine("  mineral you can support. Use null only where a figure would be invention.")
        appendLine("- If the photo shows no food at all, call the tool with portion_grams 0, confidence 'low',")
        appendLine("  and a warning saying what you actually see.")
        if (profile != null && profile.isComplete) {
            appendLine()
            appendLine("About this user (use it for `summary` and `warnings`, never to bend the numbers):")
            appendLine("- ${profile.ageYears}y ${profile.sex.label.lowercase()}, ${profile.weightKg} kg, ${profile.heightCm} cm")
            appendLine("- ${profile.activityLevel.label}, goal: ${profile.bodyGoal.label}")
            if (profile.healthConditions.isNotEmpty()) {
                appendLine("- Health conditions: ${profile.healthConditions.joinToString(", ")}")
                appendLine("  Flag anything in this meal that conflicts with them.")
            }
        }
    }

    fun ingredientSystemPrompt(): String = buildString {
        appendLine("You are the ingredient lookup inside Nutrix, a nutrition tracking app.")
        appendLine("Given an ingredient name, find its composition per 100 g from an authoritative food")
        appendLine("composition database — prefer USDA FoodData Central — and call $TOOL_RECORD_INGREDIENT once.")
        appendLine()
        appendLine("Units, exactly:")
        appendLine(unitContract())
        appendLine()
        appendLine("- Figures are per 100 g of the form stated. If the user did not say raw or cooked,")
        appendLine("  assume the form that ingredient is normally weighed in, and say which in source_note.")
        appendLine("- Fill in every vitamin and mineral the source reports. Use null where the source has no value.")
        appendLine("- Name the database and entry in source_note so the user can check it.")
    }

    fun goalReviewSystemPrompt(): String = buildString {
        appendLine("You are the goal reviewer inside Nutrix, a nutrition tracking app.")
        appendLine("The app has already calculated daily targets on-device using Mifflin-St Jeor or")
        appendLine("Katch-McArdle, standard activity multipliers, sports-nutrition protein ranges, and the")
        appendLine("DRI tables. Your job is to review that work for this specific person, not to redo it.")
        appendLine()
        appendLine("Call $TOOL_REVIEW_GOALS once. Change a target only where you can justify it from this")
        appendLine("person's stated situation, and say why in the reason field. Where the calculated number")
        appendLine("is sound, leave it alone — an empty adjustments array is a good answer.")
        appendLine()
        appendLine("You are not their doctor. For pregnancy, breastfeeding, kidney disease, an eating")
        appendLine("disorder, or any condition where the safe range is clinically set, do not set targets:")
        appendLine("say plainly in notes that these need to come from their clinician.")
        appendLine("Never propose a calorie target below 1200 kcal for a woman or 1500 kcal for a man.")
    }

    fun chatSystemPrompt(context: String): String = buildString {
        appendLine("You are Nutrix, the nutrition assistant inside an Android app of the same name.")
        appendLine("Your users are bodybuilders and health-conscious people tracking macros, micronutrients")
        appendLine("and water.")
        appendLine()
        appendLine("How to answer:")
        appendLine("- Be direct and specific. Give the number, then the reason. Skip the preamble.")
        appendLine("- Keep it short — a few sentences, or a tight list. This is a phone screen.")
        appendLine("- Use the user's own data below when it is relevant. You know what they ate today.")
        appendLine("- Search the web when the answer depends on current evidence, a specific food's")
        appendLine("  composition, or a claim you would otherwise be recalling rather than knowing.")
        appendLine("- Supplements, symptoms and medical conditions: give the general evidence, then say")
        appendLine("  plainly that their doctor decides their case. Do not diagnose and do not dose.")
        appendLine("- If they ask something you cannot know — how they slept, what their bloods say —")
        appendLine("  say so and ask.")
        appendLine()
        appendLine("Their current situation:")
        appendLine(context)
    }

    // ---------------- parsing ----------------

    fun parseNutrients(obj: JsonObject?): Nutrients {
        if (obj == null) return Nutrients.EMPTY
        val values = mutableMapOf<Nutrient, Double>()
        for ((key, element) in obj) {
            if (element is JsonNull) continue
            val nutrient = Nutrient.fromKey(key) ?: continue
            val amount = (element as? JsonPrimitive)?.doubleOrNull ?: continue
            if (amount.isNaN() || amount.isInfinite() || amount < 0) continue
            values[nutrient] = amount
        }
        return Nutrients(values)
    }

    fun parseMealAnalysis(input: JsonObject): FoodAnalysis {
        val portion = input["portion_grams"]?.jsonPrimitive?.doubleOrNull?.coerceAtLeast(0.0) ?: 0.0
        return FoodAnalysis(
            title = input["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Unidentified food",
            portionGrams = portion,
            portionLabel = input["portion_label"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            nutrients = parseNutrients(input["nutrients"]?.jsonObject),
            ingredients = (input["ingredients"] as? JsonArray).orEmptyList().mapNotNull { element ->
                val item = element.jsonObject
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                AnalyzedIngredient(
                    name = name,
                    grams = item["grams"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    note = item["note"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            },
            confidence = when (input["confidence"]?.jsonPrimitive?.contentOrNull) {
                "high" -> Confidence.HIGH
                "low" -> Confidence.LOW
                else -> Confidence.MEDIUM
            },
            summary = input["summary"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            warnings = (input["warnings"] as? JsonArray).orEmptyList()
                .mapNotNull { it.jsonPrimitive.contentOrNull }
                .filter { it.isNotBlank() },
        )
    }

    private fun JsonArray?.orEmptyList(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

    /** Builds the `content` array for a user turn carrying an image plus a question. */
    fun imageMessage(base64Image: String, mediaType: String, text: String): JsonObject = buildJsonObject {
        put("role", "user")
        putJsonArray("content") {
            addJsonObject {
                put("type", "image")
                putJsonObject("source") {
                    put("type", "base64")
                    put("media_type", mediaType)
                    put("data", base64Image)
                }
            }
            addJsonObject {
                put("type", "text")
                put("text", text)
            }
        }
    }

    fun textMessage(role: String, text: String): JsonObject = buildJsonObject {
        put("role", role)
        put("content", buildJsonArray { addJsonObject { put("type", "text"); put("text", text) } })
    }
}
