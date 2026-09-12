package com.nutrix.app.data.repository

import com.nutrix.app.data.local.dao.IngredientCacheDao
import com.nutrix.app.data.local.entity.IngredientCacheEntity
import com.nutrix.app.data.remote.FoodDataCentralClient
import com.nutrix.app.data.remote.OfflineFoodTable
import com.nutrix.app.data.remote.OpenFoodFactsClient
import com.nutrix.app.data.remote.ProductResult
import com.nutrix.app.model.FoodAnalysis
import com.nutrix.app.model.Nutrients
import com.nutrix.app.model.SourceRef
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Where a search hit came from, which is also how trustworthy it is. */
enum class FoodSource(val label: String, val trustNote: String) {
    LABEL("Product label", "From the manufacturer's own nutrition panel"),
    LAB("USDA lab data", "Measured in a laboratory"),
    BUILT_IN("Built into Nutrix", "Approximate reference values"),
}

data class FoodSearchResult(
    val name: String,
    val detail: String,
    val per100g: Nutrients,
    val source: FoodSource,
    val sourceLabel: String,
    val servingGrams: Double? = null,
    val barcode: String? = null,
    val imageUrl: String? = null,
) {
    /** Turns a hit into something loggable at the portion the user chose. */
    fun toAnalysis(grams: Double): FoodAnalysis = FoodAnalysis(
        title = name,
        portionGrams = grams,
        portionLabel = "${grams.toInt()} g",
        nutrients = per100g * (grams / 100.0),
        summary = detail,
        sources = listOf(SourceRef(title = sourceLabel)),
    )
}

/**
 * Food lookup that costs nothing to run.
 *
 * Three free sources, ordered by how much they can be trusted: a scanned product's own label
 * beats laboratory reference data for that product, laboratory data beats an approximation,
 * and the bundled table works with no network at all. None of them needs a paid account —
 * Open Food Facts needs no key whatsoever, and the USDA key is free.
 */
class FoodSearchRepository(
    private val openFoodFacts: OpenFoodFactsClient,
    private val foodDataCentral: FoodDataCentralClient,
    private val ingredientCache: IngredientCacheDao,
) {

    /** A scanned barcode. The most accurate lookup in the app, and entirely free. */
    suspend fun byBarcode(barcode: String): Result<FoodSearchResult> = runCatching {
        val product = openFoodFacts.byBarcode(barcode)
            ?: throw NoSuchElementException(
                "That barcode isn't in Open Food Facts yet. Search by name instead, or add the " +
                    "product to the database at openfoodfacts.org.",
            )
        product.toResult().also { cache(barcode, it) }
    }

    /**
     * Searches every free source at once and returns them best-first. Products and lab entries
     * are fetched concurrently because the user is staring at a spinner while it happens.
     */
    suspend fun search(query: String): List<FoodSearchResult> = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@coroutineScope emptyList()

        val products = async { runCatching { openFoodFacts.search(trimmed) }.getOrDefault(emptyList()) }
        val lab = async { runCatching { foodDataCentral.search(trimmed) }.getOrNull() }

        val results = mutableListOf<FoodSearchResult>()

        lab.await()?.let { hit ->
            results += FoodSearchResult(
                name = hit.displayName,
                detail = FoodSource.LAB.trustNote,
                per100g = hit.per100g,
                source = FoodSource.LAB,
                sourceLabel = hit.sourceLabel,
            )
        }
        results += products.await().map { it.toResult() }

        OfflineFoodTable.find(trimmed)?.let { offline ->
            if (results.none { it.name.equals(offline.displayName, ignoreCase = true) }) {
                results += FoodSearchResult(
                    name = offline.displayName,
                    detail = FoodSource.BUILT_IN.trustNote,
                    per100g = offline.per100g,
                    source = FoodSource.BUILT_IN,
                    sourceLabel = offline.sourceLabel,
                )
            }
        }
        results
    }

    /** Names offered before the user has typed anything. */
    fun suggestions(): List<String> = OfflineFoodTable.suggestions()

    private fun ProductResult.toResult() = FoodSearchResult(
        name = displayName,
        detail = FoodSource.LABEL.trustNote,
        per100g = per100g,
        source = FoodSource.LABEL,
        sourceLabel = "Open Food Facts${if (barcode.isNotBlank()) " · $barcode" else ""}",
        servingGrams = servingGrams,
        barcode = barcode.ifBlank { null },
        imageUrl = imageUrl,
    )

    private suspend fun cache(key: String, result: FoodSearchResult) {
        ingredientCache.put(
            IngredientCacheEntity(
                query = key,
                displayName = result.name,
                per100g = result.per100g,
                sourceLabel = result.sourceLabel,
                cachedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }
}
