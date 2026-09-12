package com.nutrix.app.data.remote

import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.Nutrients

/**
 * A small bundled table of staple ingredients, per 100 g.
 *
 * It exists so the recipe builder works on a plane, on a dead connection, and before the user
 * has entered any API key at all. It is deliberately shallow — roughly forty foods with their
 * macros and the micronutrients each is actually known for — and every result it returns is
 * labelled approximate. USDA FoodData Central and Claude both take precedence when reachable.
 *
 * Figures are rounded from USDA SR Legacy / Foundation Foods entries.
 */
object OfflineFoodTable {

    data class Entry(
        val name: String,
        val keywords: List<String>,
        val per100g: Nutrients,
    )

    fun find(query: String): IngredientNutrition? {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return null
        val match = entries.firstOrNull { entry ->
            entry.keywords.any { it == needle }
        } ?: entries.firstOrNull { entry ->
            entry.keywords.any { needle.contains(it) || it.contains(needle) }
        } ?: return null
        return IngredientNutrition(
            displayName = match.name,
            per100g = match.per100g,
            sourceLabel = "Nutrix offline table (approximate)",
        )
    }

    fun suggestions(): List<String> = entries.map { it.name }

    private fun entry(
        name: String,
        keywords: List<String>,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        fiber: Double = 0.0,
        sugar: Double = 0.0,
        saturated: Double = 0.0,
        extra: Map<Nutrient, Double> = emptyMap(),
    ): Entry {
        val values = mutableMapOf(
            Nutrient.ENERGY to kcal,
            Nutrient.PROTEIN to protein,
            Nutrient.CARBS to carbs,
            Nutrient.FAT to fat,
            Nutrient.FIBER to fiber,
            Nutrient.SUGAR to sugar,
            Nutrient.SATURATED_FAT to saturated,
        )
        values += extra
        return Entry(name, keywords + name.lowercase(), Nutrients(values))
    }

    private val entries: List<Entry> = listOf(
        entry(
            "Chicken breast, raw, skinless", listOf("chicken", "chicken breast"),
            120.0, 22.5, 0.0, 2.6, saturated = 0.6,
            extra = mapOf(
                Nutrient.SODIUM to 45.0, Nutrient.POTASSIUM to 334.0, Nutrient.MAGNESIUM to 27.0,
                Nutrient.IRON to 0.7, Nutrient.ZINC to 0.8, Nutrient.SELENIUM to 22.0,
                Nutrient.VITAMIN_B3 to 9.9, Nutrient.VITAMIN_B6 to 0.6, Nutrient.PHOSPHORUS to 213.0,
            ),
        ),
        entry(
            "Egg, whole, raw", listOf("egg", "eggs"),
            143.0, 12.6, 0.7, 9.5, sugar = 0.4, saturated = 3.1,
            extra = mapOf(
                Nutrient.SODIUM to 142.0, Nutrient.POTASSIUM to 138.0, Nutrient.CALCIUM to 56.0,
                Nutrient.IRON to 1.75, Nutrient.ZINC to 1.3, Nutrient.SELENIUM to 30.0,
                Nutrient.VITAMIN_D to 2.0, Nutrient.VITAMIN_B12 to 0.89, Nutrient.VITAMIN_A to 160.0,
                Nutrient.VITAMIN_B2 to 0.46, Nutrient.PHOSPHORUS to 198.0, Nutrient.IODINE to 26.0,
            ),
        ),
        entry(
            "Milk, whole (3.25%)", listOf("milk", "whole milk"),
            61.0, 3.2, 4.8, 3.3, sugar = 5.1, saturated = 1.9,
            extra = mapOf(
                Nutrient.CALCIUM to 113.0, Nutrient.POTASSIUM to 132.0, Nutrient.SODIUM to 43.0,
                Nutrient.VITAMIN_B12 to 0.45, Nutrient.VITAMIN_B2 to 0.17, Nutrient.PHOSPHORUS to 84.0,
                Nutrient.IODINE to 30.0,
            ),
        ),
        entry(
            "Greek yoghurt, plain, non-fat", listOf("greek yogurt", "greek yoghurt", "yogurt", "yoghurt"),
            59.0, 10.2, 3.6, 0.4, sugar = 3.2, saturated = 0.1,
            extra = mapOf(
                Nutrient.CALCIUM to 110.0, Nutrient.POTASSIUM to 141.0, Nutrient.SODIUM to 36.0,
                Nutrient.VITAMIN_B12 to 0.75, Nutrient.PHOSPHORUS to 135.0,
            ),
        ),
        entry(
            "Rice, white, cooked", listOf("rice", "white rice", "boiled rice"),
            130.0, 2.7, 28.2, 0.3, fiber = 0.4, sugar = 0.1,
            extra = mapOf(Nutrient.SODIUM to 1.0, Nutrient.POTASSIUM to 35.0, Nutrient.MAGNESIUM to 12.0),
        ),
        entry(
            "Rice, brown, cooked", listOf("brown rice"),
            123.0, 2.7, 25.6, 1.0, fiber = 1.6,
            extra = mapOf(
                Nutrient.MAGNESIUM to 39.0, Nutrient.POTASSIUM to 86.0, Nutrient.SELENIUM to 5.8,
                Nutrient.MANGANESE to 1.1, Nutrient.PHOSPHORUS to 103.0,
            ),
        ),
        entry(
            "Roti / chapati, wholewheat", listOf("roti", "chapati", "chapatti", "phulka"),
            297.0, 9.6, 49.0, 7.4, fiber = 4.9,
            extra = mapOf(
                Nutrient.IRON to 2.5, Nutrient.MAGNESIUM to 78.0, Nutrient.SODIUM to 210.0,
                Nutrient.POTASSIUM to 180.0,
            ),
        ),
        entry(
            "Bread, white", listOf("bread", "white bread", "toast"),
            265.0, 9.0, 49.0, 3.2, fiber = 2.7, sugar = 5.0, saturated = 0.7,
            extra = mapOf(
                Nutrient.SODIUM to 491.0, Nutrient.IRON to 3.6, Nutrient.CALCIUM to 260.0,
                Nutrient.VITAMIN_B1 to 0.5, Nutrient.VITAMIN_B9 to 171.0,
            ),
        ),
        entry(
            "Oats, rolled, dry", listOf("oats", "oatmeal", "porridge oats"),
            389.0, 16.9, 66.3, 6.9, fiber = 10.6, sugar = 0.0, saturated = 1.2,
            extra = mapOf(
                Nutrient.MAGNESIUM to 177.0, Nutrient.IRON to 4.7, Nutrient.ZINC to 4.0,
                Nutrient.PHOSPHORUS to 523.0, Nutrient.MANGANESE to 4.9, Nutrient.VITAMIN_B1 to 0.76,
            ),
        ),
        entry(
            "Banana", listOf("banana", "bananas"),
            89.0, 1.1, 22.8, 0.3, fiber = 2.6, sugar = 12.2,
            extra = mapOf(
                Nutrient.POTASSIUM to 358.0, Nutrient.MAGNESIUM to 27.0, Nutrient.VITAMIN_C to 8.7,
                Nutrient.VITAMIN_B6 to 0.37,
            ),
        ),
        entry(
            "Apple, with skin", listOf("apple", "apples"),
            52.0, 0.3, 13.8, 0.2, fiber = 2.4, sugar = 10.4,
            extra = mapOf(Nutrient.POTASSIUM to 107.0, Nutrient.VITAMIN_C to 4.6),
        ),
        entry(
            "Potato, boiled", listOf("potato", "potatoes", "boiled potato"),
            87.0, 1.9, 20.1, 0.1, fiber = 1.8, sugar = 0.9,
            extra = mapOf(
                Nutrient.POTASSIUM to 379.0, Nutrient.VITAMIN_C to 13.0, Nutrient.VITAMIN_B6 to 0.3,
                Nutrient.MAGNESIUM to 22.0,
            ),
        ),
        entry(
            "Sweet potato, boiled", listOf("sweet potato", "sweet potatoes", "shakarkandi"),
            76.0, 1.4, 17.7, 0.1, fiber = 2.5, sugar = 5.7,
            extra = mapOf(
                Nutrient.VITAMIN_A to 961.0, Nutrient.POTASSIUM to 230.0, Nutrient.VITAMIN_C to 12.8,
                Nutrient.MANGANESE to 0.3,
            ),
        ),
        entry(
            "Broccoli, raw", listOf("broccoli"),
            34.0, 2.8, 6.6, 0.4, fiber = 2.6, sugar = 1.7,
            extra = mapOf(
                Nutrient.VITAMIN_C to 89.2, Nutrient.VITAMIN_K to 101.6, Nutrient.POTASSIUM to 316.0,
                Nutrient.CALCIUM to 47.0, Nutrient.VITAMIN_B9 to 63.0, Nutrient.VITAMIN_A to 31.0,
            ),
        ),
        entry(
            "Spinach, raw", listOf("spinach", "palak"),
            23.0, 2.9, 3.6, 0.4, fiber = 2.2, sugar = 0.4,
            extra = mapOf(
                Nutrient.IRON to 2.7, Nutrient.MAGNESIUM to 79.0, Nutrient.POTASSIUM to 558.0,
                Nutrient.VITAMIN_K to 482.9, Nutrient.VITAMIN_B9 to 194.0, Nutrient.VITAMIN_A to 469.0,
                Nutrient.VITAMIN_C to 28.1, Nutrient.CALCIUM to 99.0,
            ),
        ),
        entry(
            "Tomato, raw", listOf("tomato", "tomatoes"),
            18.0, 0.9, 3.9, 0.2, fiber = 1.2, sugar = 2.6,
            extra = mapOf(
                Nutrient.VITAMIN_C to 13.7, Nutrient.POTASSIUM to 237.0, Nutrient.VITAMIN_A to 42.0,
                Nutrient.VITAMIN_K to 7.9,
            ),
        ),
        entry(
            "Onion, raw", listOf("onion", "onions", "pyaz"),
            40.0, 1.1, 9.3, 0.1, fiber = 1.7, sugar = 4.2,
            extra = mapOf(Nutrient.VITAMIN_C to 7.4, Nutrient.POTASSIUM to 146.0),
        ),
        entry(
            "Carrot, raw", listOf("carrot", "carrots", "gajar"),
            41.0, 0.9, 9.6, 0.2, fiber = 2.8, sugar = 4.7,
            extra = mapOf(
                Nutrient.VITAMIN_A to 835.0, Nutrient.POTASSIUM to 320.0, Nutrient.VITAMIN_K to 13.2,
                Nutrient.VITAMIN_C to 5.9,
            ),
        ),
        entry(
            "Bell pepper, red, raw", listOf("bell pepper", "capsicum", "red pepper", "shimla mirch"),
            31.0, 1.0, 6.0, 0.3, fiber = 2.1, sugar = 4.2,
            extra = mapOf(
                Nutrient.VITAMIN_C to 127.7, Nutrient.VITAMIN_A to 157.0, Nutrient.VITAMIN_B6 to 0.29,
                Nutrient.POTASSIUM to 211.0,
            ),
        ),
        entry(
            "Cucumber, with peel", listOf("cucumber", "kheera"),
            15.0, 0.7, 3.6, 0.1, fiber = 0.5, sugar = 1.7,
            extra = mapOf(Nutrient.POTASSIUM to 147.0, Nutrient.VITAMIN_K to 16.4),
        ),
        entry(
            "Almonds", listOf("almond", "almonds", "badam"),
            579.0, 21.2, 21.6, 49.9, fiber = 12.5, sugar = 4.4, saturated = 3.8,
            extra = mapOf(
                Nutrient.MAGNESIUM to 270.0, Nutrient.CALCIUM to 269.0, Nutrient.VITAMIN_E to 25.6,
                Nutrient.IRON to 3.7, Nutrient.ZINC to 3.1, Nutrient.PHOSPHORUS to 481.0,
                Nutrient.MANGANESE to 2.2, Nutrient.VITAMIN_B2 to 1.1,
            ),
        ),
        entry(
            "Peanut butter, smooth", listOf("peanut butter"),
            588.0, 25.1, 20.0, 50.4, fiber = 6.0, sugar = 9.2, saturated = 10.3,
            extra = mapOf(
                Nutrient.MAGNESIUM to 154.0, Nutrient.SODIUM to 429.0, Nutrient.POTASSIUM to 649.0,
                Nutrient.VITAMIN_B3 to 13.1, Nutrient.VITAMIN_E to 9.1, Nutrient.ZINC to 2.5,
            ),
        ),
        entry(
            "Olive oil", listOf("olive oil", "oil"),
            884.0, 0.0, 0.0, 100.0, saturated = 13.8,
            extra = mapOf(Nutrient.VITAMIN_E to 14.4, Nutrient.VITAMIN_K to 60.2),
        ),
        entry(
            "Butter, unsalted", listOf("butter", "makhan"),
            717.0, 0.85, 0.06, 81.1, sugar = 0.06, saturated = 51.4,
            extra = mapOf(
                Nutrient.VITAMIN_A to 684.0, Nutrient.SODIUM to 11.0, Nutrient.VITAMIN_D to 1.5,
                Nutrient.VITAMIN_E to 2.3,
            ),
        ),
        entry(
            "Ghee", listOf("ghee", "clarified butter"),
            876.0, 0.3, 0.0, 99.5, saturated = 61.9,
            extra = mapOf(Nutrient.VITAMIN_A to 840.0, Nutrient.VITAMIN_E to 2.8),
        ),
        entry(
            "Salmon, Atlantic, raw", listOf("salmon"),
            208.0, 20.4, 0.0, 13.4, saturated = 3.1,
            extra = mapOf(
                Nutrient.VITAMIN_D to 11.0, Nutrient.VITAMIN_B12 to 3.2, Nutrient.SELENIUM to 36.5,
                Nutrient.POTASSIUM to 363.0, Nutrient.VITAMIN_B3 to 8.0, Nutrient.PHOSPHORUS to 240.0,
            ),
        ),
        entry(
            "Tuna, canned in water, drained", listOf("tuna", "canned tuna"),
            116.0, 25.5, 0.0, 0.8, saturated = 0.2,
            extra = mapOf(
                Nutrient.SODIUM to 247.0, Nutrient.SELENIUM to 65.7, Nutrient.VITAMIN_B12 to 2.2,
                Nutrient.VITAMIN_D to 1.7, Nutrient.PHOSPHORUS to 139.0,
            ),
        ),
        entry(
            "Beef mince, 90% lean, raw", listOf("beef", "minced beef", "ground beef", "mince"),
            176.0, 20.0, 0.0, 10.0, saturated = 3.9,
            extra = mapOf(
                Nutrient.IRON to 2.2, Nutrient.ZINC to 4.8, Nutrient.VITAMIN_B12 to 2.1,
                Nutrient.SELENIUM to 17.0, Nutrient.PHOSPHORUS to 183.0, Nutrient.SODIUM to 66.0,
            ),
        ),
        entry(
            "Pork loin, lean, raw", listOf("pork", "pork chop", "pork loin"),
            143.0, 21.0, 0.0, 6.0, saturated = 2.1,
            extra = mapOf(
                Nutrient.VITAMIN_B1 to 0.87, Nutrient.ZINC to 1.9, Nutrient.SELENIUM to 32.0,
                Nutrient.POTASSIUM to 372.0,
            ),
        ),
        entry(
            "Lentils, cooked", listOf("lentil", "lentils", "dal", "daal", "masoor"),
            116.0, 9.0, 20.1, 0.4, fiber = 7.9, sugar = 1.8,
            extra = mapOf(
                Nutrient.IRON to 3.3, Nutrient.VITAMIN_B9 to 181.0, Nutrient.POTASSIUM to 369.0,
                Nutrient.MAGNESIUM to 36.0, Nutrient.ZINC to 1.3, Nutrient.PHOSPHORUS to 180.0,
            ),
        ),
        entry(
            "Chickpeas, cooked", listOf("chickpea", "chickpeas", "chana", "garbanzo"),
            164.0, 8.9, 27.4, 2.6, fiber = 7.6, sugar = 4.8,
            extra = mapOf(
                Nutrient.IRON to 2.9, Nutrient.VITAMIN_B9 to 172.0, Nutrient.MAGNESIUM to 48.0,
                Nutrient.ZINC to 1.5, Nutrient.POTASSIUM to 291.0,
            ),
        ),
        entry(
            "Kidney beans, cooked", listOf("kidney beans", "rajma"),
            127.0, 8.7, 22.8, 0.5, fiber = 6.4, sugar = 0.3,
            extra = mapOf(
                Nutrient.IRON to 2.2, Nutrient.POTASSIUM to 405.0, Nutrient.VITAMIN_B9 to 130.0,
                Nutrient.MAGNESIUM to 45.0,
            ),
        ),
        entry(
            "Tofu, firm, calcium-set", listOf("tofu", "bean curd"),
            144.0, 17.3, 2.8, 8.7, fiber = 2.3, saturated = 1.3,
            extra = mapOf(
                Nutrient.CALCIUM to 683.0, Nutrient.IRON to 2.7, Nutrient.MAGNESIUM to 58.0,
                Nutrient.ZINC to 1.6, Nutrient.PHOSPHORUS to 190.0,
            ),
        ),
        entry(
            "Paneer", listOf("paneer", "cottage cheese, indian"),
            296.0, 18.3, 3.6, 22.8, saturated = 14.0,
            extra = mapOf(
                Nutrient.CALCIUM to 480.0, Nutrient.PHOSPHORUS to 310.0, Nutrient.SODIUM to 18.0,
                Nutrient.VITAMIN_A to 190.0,
            ),
        ),
        entry(
            "Cheddar cheese", listOf("cheddar", "cheese"),
            403.0, 24.9, 1.3, 33.1, sugar = 0.5, saturated = 21.0,
            extra = mapOf(
                Nutrient.CALCIUM to 721.0, Nutrient.SODIUM to 653.0, Nutrient.VITAMIN_A to 265.0,
                Nutrient.VITAMIN_B12 to 1.1, Nutrient.ZINC to 3.1, Nutrient.PHOSPHORUS to 512.0,
            ),
        ),
        entry(
            "Whey protein powder", listOf("whey", "whey protein", "protein powder"),
            373.0, 80.0, 7.0, 3.0, sugar = 4.0, saturated = 1.5,
            extra = mapOf(
                Nutrient.CALCIUM to 450.0, Nutrient.SODIUM to 300.0, Nutrient.POTASSIUM to 500.0,
                Nutrient.PHOSPHORUS to 300.0,
            ),
        ),
        entry(
            "Pasta, cooked", listOf("pasta", "spaghetti", "macaroni", "noodles"),
            158.0, 5.8, 30.9, 0.9, fiber = 1.8, sugar = 0.6,
            extra = mapOf(Nutrient.SELENIUM to 26.4, Nutrient.VITAMIN_B9 to 83.0, Nutrient.MAGNESIUM to 18.0),
        ),
        entry(
            "Quinoa, cooked", listOf("quinoa"),
            120.0, 4.4, 21.3, 1.9, fiber = 2.8, sugar = 0.9,
            extra = mapOf(
                Nutrient.MAGNESIUM to 64.0, Nutrient.IRON to 1.5, Nutrient.VITAMIN_B9 to 42.0,
                Nutrient.ZINC to 1.1, Nutrient.PHOSPHORUS to 152.0,
            ),
        ),
        entry(
            "Avocado", listOf("avocado"),
            160.0, 2.0, 8.5, 14.7, fiber = 6.7, sugar = 0.7, saturated = 2.1,
            extra = mapOf(
                Nutrient.POTASSIUM to 485.0, Nutrient.VITAMIN_B9 to 81.0, Nutrient.VITAMIN_E to 2.1,
                Nutrient.VITAMIN_K to 21.0, Nutrient.MAGNESIUM to 29.0,
            ),
        ),
        entry(
            "Orange", listOf("orange", "oranges"),
            47.0, 0.9, 11.8, 0.1, fiber = 2.4, sugar = 9.4,
            extra = mapOf(
                Nutrient.VITAMIN_C to 53.2, Nutrient.VITAMIN_B9 to 30.0, Nutrient.POTASSIUM to 181.0,
                Nutrient.CALCIUM to 40.0,
            ),
        ),
        entry(
            "Mango", listOf("mango", "aam"),
            60.0, 0.8, 15.0, 0.4, fiber = 1.6, sugar = 13.7,
            extra = mapOf(
                Nutrient.VITAMIN_C to 36.4, Nutrient.VITAMIN_A to 54.0, Nutrient.VITAMIN_B9 to 43.0,
                Nutrient.POTASSIUM to 168.0,
            ),
        ),
        entry(
            "Honey", listOf("honey", "shahad"),
            304.0, 0.3, 82.4, 0.0, sugar = 82.1,
            extra = mapOf(Nutrient.POTASSIUM to 52.0),
        ),
        entry(
            "Sugar, white", listOf("sugar", "granulated sugar", "cheeni"),
            387.0, 0.0, 100.0, 0.0, sugar = 100.0,
        ),
        entry(
            "Salt, table", listOf("salt", "namak"),
            0.0, 0.0, 0.0, 0.0,
            extra = mapOf(Nutrient.SODIUM to 38758.0, Nutrient.IODINE to 2500.0),
        ),
    )
}
