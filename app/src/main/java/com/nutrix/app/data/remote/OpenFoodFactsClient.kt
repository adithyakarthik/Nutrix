package com.nutrix.app.data.remote

import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientUnit
import com.nutrix.app.model.Nutrients
import com.nutrix.app.util.NutrixJson
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** A packaged product, with the nutrition from its own label. */
data class ProductResult(
    val barcode: String,
    val name: String,
    val brand: String,
    val per100g: Nutrients,
    val servingGrams: Double?,
    val imageUrl: String?,
) {
    val displayName: String
        get() = listOf(brand, name).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unknown product" }
}

/**
 * Open Food Facts — a free, open, crowd-sourced database of packaged food.
 *
 * This is the most accurate source in the whole app and it costs nothing: a barcode returns the
 * manufacturer's own nutrition panel, which beats any estimate from a photograph. No API key,
 * no account, no billing. The project asks only that clients identify themselves in the
 * User-Agent, which is what [USER_AGENT] is for.
 */
class OpenFoodFactsClient(
    private val httpClient: OkHttpClient = ClaudeClient.defaultHttpClient(),
) {

    /** Looks up a scanned barcode. Returns null when the product is not in the database. */
    suspend fun byBarcode(barcode: String): ProductResult? = withContext(Dispatchers.IO) {
        val clean = barcode.trim().filter { it.isDigit() }
        if (clean.length < 6) return@withContext null

        val url = "$BASE/api/v2/product/$clean.json?fields=$FIELDS"
        val root = get(url) ?: return@withContext null
        if (root["status"]?.jsonPrimitive?.doubleOrNull?.toInt() != 1) return@withContext null
        parseProduct(root["product"]?.jsonObject ?: return@withContext null, clean)
    }

    /** Free-text search, for when the barcode is missing or the packet is long gone. */
    suspend fun search(query: String, limit: Int = 12): List<ProductResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val url = buildString {
            append(BASE).append("/cgi/search.pl?search_simple=1&action=process&json=1")
            append("&page_size=").append(limit)
            append("&fields=").append(FIELDS)
            append("&search_terms=").append(URLEncoder.encode(trimmed, "UTF-8"))
        }
        val root = get(url) ?: return@withContext emptyList()
        val products = root["products"]?.jsonArray ?: return@withContext emptyList()
        products.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            parseProduct(obj, obj["code"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }.filter { !it.per100g.isEmpty }
    }

    private fun get(url: String): JsonObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val raw = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: IOException) {
            return null
        } ?: return null
        return runCatching { NutrixJson.parseObject(raw) }.getOrNull()
    }

    private fun parseProduct(product: JsonObject, barcode: String): ProductResult? {
        val nutriments = product["nutriments"]?.jsonObject ?: return null
        val values = mutableMapOf<Nutrient, Double>()

        for ((field, nutrient) in FIELD_MAP) {
            val amount = nutriments["${field}_100g"]?.jsonPrimitive?.doubleOrNull ?: continue
            // Open Food Facts normalises the _100g fields to a base unit per nutrient:
            // grams for macros, and for micronutrients the SI base the panel used.
            val converted = when (nutrient.unit) {
                NutrientUnit.KCAL, NutrientUnit.GRAM -> amount
                NutrientUnit.MILLIGRAM -> amount * 1_000      // stored in grams
                NutrientUnit.MICROGRAM -> amount * 1_000_000  // stored in grams
            }
            if (converted.isFinite() && converted >= 0) values[nutrient] = converted
        }

        // Salt is reported far more often than sodium on European labels; 1 g salt = 400 mg sodium.
        if (!values.containsKey(Nutrient.SODIUM)) {
            nutriments["salt_100g"]?.jsonPrimitive?.doubleOrNull?.let { saltGrams ->
                values[Nutrient.SODIUM] = saltGrams * 400.0
            }
        }
        if (values.isEmpty()) return null

        return ProductResult(
            barcode = barcode,
            name = product["product_name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
            brand = product["brands"]?.jsonPrimitive?.contentOrNull.orEmpty().substringBefore(",").trim(),
            per100g = Nutrients(values),
            servingGrams = product["serving_quantity"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 },
            imageUrl = product["image_front_small_url"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private companion object {
        const val BASE = "https://world.openfoodfacts.org"
        const val USER_AGENT = "Nutrix/1.0 (Android; open source nutrition tracker)"

        val FIELDS = listOf(
            "code", "product_name", "brands", "nutriments",
            "serving_quantity", "image_front_small_url",
        ).joinToString(",")

        /** Open Food Facts nutriment keys, without the `_100g` suffix. */
        val FIELD_MAP: List<Pair<String, Nutrient>> = listOf(
            "energy-kcal" to Nutrient.ENERGY,
            "proteins" to Nutrient.PROTEIN,
            "carbohydrates" to Nutrient.CARBS,
            "fat" to Nutrient.FAT,
            "saturated-fat" to Nutrient.SATURATED_FAT,
            "fiber" to Nutrient.FIBER,
            "sugars" to Nutrient.SUGAR,
            "sodium" to Nutrient.SODIUM,
            "calcium" to Nutrient.CALCIUM,
            "iron" to Nutrient.IRON,
            "magnesium" to Nutrient.MAGNESIUM,
            "zinc" to Nutrient.ZINC,
            "potassium" to Nutrient.POTASSIUM,
            "phosphorus" to Nutrient.PHOSPHORUS,
            "copper" to Nutrient.COPPER,
            "manganese" to Nutrient.MANGANESE,
            "selenium" to Nutrient.SELENIUM,
            "iodine" to Nutrient.IODINE,
            "vitamin-a" to Nutrient.VITAMIN_A,
            "vitamin-c" to Nutrient.VITAMIN_C,
            "vitamin-d" to Nutrient.VITAMIN_D,
            "vitamin-e" to Nutrient.VITAMIN_E,
            "vitamin-k" to Nutrient.VITAMIN_K,
            "vitamin-b1" to Nutrient.VITAMIN_B1,
            "vitamin-b2" to Nutrient.VITAMIN_B2,
            "vitamin-pp" to Nutrient.VITAMIN_B3,
            "pantothenic-acid" to Nutrient.VITAMIN_B5,
            "vitamin-b6" to Nutrient.VITAMIN_B6,
            "biotin" to Nutrient.VITAMIN_B7,
            "vitamin-b9" to Nutrient.VITAMIN_B9,
            "vitamin-b12" to Nutrient.VITAMIN_B12,
        )
    }
}
