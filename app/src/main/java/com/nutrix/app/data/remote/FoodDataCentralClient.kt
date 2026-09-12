package com.nutrix.app.data.remote

import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientUnit
import com.nutrix.app.model.Nutrients
import com.nutrix.app.util.NutrixJson
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** One ingredient's composition per 100 g, and where it came from. */
data class IngredientNutrition(
    val displayName: String,
    val per100g: Nutrients,
    val sourceLabel: String,
)

/**
 * USDA FoodData Central — the reference database most national food labelling traces back to.
 *
 * This is the first place Nutrix looks for a named ingredient, because a lab-measured value
 * beats any model's estimate. It is free, needs only a key from fdc.nal.usda.gov, and when no
 * key is configured the repository falls back to the bundled table and then to Claude.
 */
class FoodDataCentralClient(
    private val apiKeyProvider: suspend () -> String?,
    private val httpClient: OkHttpClient = ClaudeClient.defaultHttpClient(),
) {

    suspend fun search(query: String): IngredientNutrition? = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return@withContext null
        val url = buildString {
            append(BASE_URL)
            append("?query=").append(URLEncoder.encode(query, "UTF-8"))
            append("&dataType=").append(URLEncoder.encode("Foundation,SR Legacy", "UTF-8"))
            append("&pageSize=3&sortBy=dataType.keyword&sortOrder=asc")
            append("&api_key=").append(URLEncoder.encode(apiKey, "UTF-8"))
        }
        val request = Request.Builder().url(url).get().build()
        val raw = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()
            }
        } catch (e: IOException) {
            return@withContext null
        } ?: return@withContext null

        val root = runCatching { NutrixJson.parseObject(raw) }.getOrNull() ?: return@withContext null
        val food = root["foods"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@withContext null
        val description = food["description"]?.jsonPrimitive?.contentOrNull ?: query
        val fdcId = food["fdcId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val nutrients = parseFoodNutrients(food)
        if (nutrients.isEmpty) return@withContext null

        IngredientNutrition(
            displayName = description.lowercase().replaceFirstChar { it.uppercase() },
            per100g = nutrients,
            sourceLabel = "USDA FoodData Central${if (fdcId.isNotBlank()) " #$fdcId" else ""}",
        )
    }

    private fun parseFoodNutrients(food: kotlinx.serialization.json.JsonObject): Nutrients {
        val list = food["foodNutrients"]?.jsonArray ?: return Nutrients.EMPTY
        val values = mutableMapOf<Nutrient, Double>()
        for (element in list) {
            val item = element.jsonObject
            val number = item["nutrientNumber"]?.jsonPrimitive?.contentOrNull
                ?: item["number"]?.jsonPrimitive?.contentOrNull
                ?: continue
            val nutrient = NUTRIENT_BY_USDA_NUMBER[number.trimStart('0').ifEmpty { number }] ?: continue
            val value = item["value"]?.jsonPrimitive?.doubleOrNull
                ?: item["amount"]?.jsonPrimitive?.doubleOrNull
                ?: continue
            val unit = item["unitName"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val converted = convert(value, unit, nutrient.unit) ?: continue
            // Duplicate rows happen; the first (canonical) one wins.
            values.putIfAbsent(nutrient, converted)
        }
        return Nutrients(values)
    }

    /** USDA reports in G/MG/UG/KCAL/IU; everything is normalised to the nutrient's base unit. */
    private fun convert(value: Double, usdaUnit: String, target: NutrientUnit): Double? {
        val from = usdaUnit.uppercase()
        val inMicrograms = when (from) {
            "G" -> value * 1_000_000
            "MG", "MG_ATE", "MG_GAE" -> value * 1_000
            "UG", "µG" -> value
            "KCAL" -> return if (target == NutrientUnit.KCAL) value else null
            "KJ" -> return if (target == NutrientUnit.KCAL) value / 4.184 else null
            // International Units are vitamin-specific and the search endpoint rarely uses them;
            // dropping the row is safer than applying the wrong conversion factor.
            else -> return null
        }
        return when (target) {
            NutrientUnit.GRAM -> inMicrograms / 1_000_000
            NutrientUnit.MILLIGRAM -> inMicrograms / 1_000
            NutrientUnit.MICROGRAM -> inMicrograms
            NutrientUnit.KCAL -> null
        }
    }

    private companion object {
        const val BASE_URL = "https://api.nal.usda.gov/fdc/v1/foods/search"

        /** USDA nutrient numbers (INFOODS tagnames) mapped to what Nutrix tracks. */
        val NUTRIENT_BY_USDA_NUMBER: Map<String, Nutrient> = mapOf(
            "208" to Nutrient.ENERGY,
            "203" to Nutrient.PROTEIN,
            "205" to Nutrient.CARBS,
            "204" to Nutrient.FAT,
            "606" to Nutrient.SATURATED_FAT,
            "291" to Nutrient.FIBER,
            "269" to Nutrient.SUGAR,
            "320" to Nutrient.VITAMIN_A,      // Vitamin A, RAE
            "404" to Nutrient.VITAMIN_B1,     // Thiamin
            "405" to Nutrient.VITAMIN_B2,     // Riboflavin
            "406" to Nutrient.VITAMIN_B3,     // Niacin
            "410" to Nutrient.VITAMIN_B5,     // Pantothenic acid
            "415" to Nutrient.VITAMIN_B6,
            "416" to Nutrient.VITAMIN_B7,     // Biotin
            "435" to Nutrient.VITAMIN_B9,     // Folate, DFE
            "418" to Nutrient.VITAMIN_B12,
            "401" to Nutrient.VITAMIN_C,
            "328" to Nutrient.VITAMIN_D,      // Vitamin D (D2 + D3)
            "323" to Nutrient.VITAMIN_E,      // Alpha-tocopherol
            "430" to Nutrient.VITAMIN_K,      // Phylloquinone
            "301" to Nutrient.CALCIUM,
            "303" to Nutrient.IRON,
            "304" to Nutrient.MAGNESIUM,
            "305" to Nutrient.PHOSPHORUS,
            "306" to Nutrient.POTASSIUM,
            "307" to Nutrient.SODIUM,
            "309" to Nutrient.ZINC,
            "312" to Nutrient.COPPER,
            "314" to Nutrient.IODINE,
            "315" to Nutrient.MANGANESE,
            "317" to Nutrient.SELENIUM,
        )
    }
}
