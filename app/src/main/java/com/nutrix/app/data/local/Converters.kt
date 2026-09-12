package com.nutrix.app.data.local

import androidx.room.TypeConverter
import com.nutrix.app.model.MealType
import com.nutrix.app.model.Nutrients
import com.nutrix.app.model.RecipeIngredient
import com.nutrix.app.model.SourceRef
import kotlinx.serialization.builtins.ListSerializer
import com.nutrix.app.util.NutrixJson

/**
 * Nutrient bags and ingredient lists are stored as JSON columns.
 *
 * A relational table per nutrient would mean a 30-row join for every diary entry and a schema
 * migration every time a nutrient is added; a JSON column costs one parse and stays flexible.
 */
class Converters {
    private val json = NutrixJson.instance

    @TypeConverter
    fun nutrientsToJson(value: Nutrients): String = json.encodeToString(Nutrients.serializer(), value)

    @TypeConverter
    fun nutrientsFromJson(value: String?): Nutrients = value
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString(Nutrients.serializer(), it) }.getOrNull() }
        ?: Nutrients.EMPTY

    @TypeConverter
    fun ingredientsToJson(value: List<RecipeIngredient>): String =
        json.encodeToString(ListSerializer(RecipeIngredient.serializer()), value)

    @TypeConverter
    fun ingredientsFromJson(value: String?): List<RecipeIngredient> = value
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString(ListSerializer(RecipeIngredient.serializer()), it) }.getOrNull() }
        ?: emptyList()

    @TypeConverter
    fun sourcesToJson(value: List<SourceRef>): String =
        json.encodeToString(ListSerializer(SourceRef.serializer()), value)

    @TypeConverter
    fun sourcesFromJson(value: String?): List<SourceRef> = value
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString(ListSerializer(SourceRef.serializer()), it) }.getOrNull() }
        ?: emptyList()

    @TypeConverter
    fun mealToString(value: MealType): String = value.name

    @TypeConverter
    fun mealFromString(value: String?): MealType =
        value?.let { runCatching { MealType.valueOf(it) }.getOrNull() } ?: MealType.SNACK
}
