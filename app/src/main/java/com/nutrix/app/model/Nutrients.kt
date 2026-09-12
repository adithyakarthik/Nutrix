package com.nutrix.app.model

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * A bag of nutrient amounts, each in its nutrient's base unit.
 *
 * Absent means "not known", not "zero" — a lab report that never measured iodine and a food
 * that genuinely contains none are different facts, and the UI says so.
 */
@Serializable
data class Nutrients(val values: Map<Nutrient, Double> = emptyMap()) {

    operator fun get(nutrient: Nutrient): Double? = values[nutrient]

    fun amountOr0(nutrient: Nutrient): Double = values[nutrient] ?: 0.0

    fun has(nutrient: Nutrient): Boolean = values.containsKey(nutrient)

    val isEmpty: Boolean get() = values.isEmpty()

    /** Sums two bags. A nutrient known in only one of them stays known. */
    operator fun plus(other: Nutrients): Nutrients {
        if (other.isEmpty) return this
        if (isEmpty) return other
        val merged = values.toMutableMap()
        for ((nutrient, amount) in other.values) {
            merged[nutrient] = (merged[nutrient] ?: 0.0) + amount
        }
        return Nutrients(merged)
    }

    /** Scales every amount, e.g. per-100g values times 2.35 for a 235 g serving. */
    operator fun times(factor: Double): Nutrients =
        if (factor == 1.0) this else Nutrients(values.mapValues { (_, amount) -> amount * factor })

    operator fun div(divisor: Double): Nutrients =
        if (divisor == 0.0) EMPTY else this * (1.0 / divisor)

    fun with(nutrient: Nutrient, amount: Double): Nutrients =
        Nutrients(values + (nutrient to amount))

    fun without(nutrient: Nutrient): Nutrients = Nutrients(values - nutrient)

    /** Only the nutrients in [group], in declaration order. */
    fun byGroup(group: NutrientGroup): List<Pair<Nutrient, Double>> =
        Nutrient.entries.filter { it.group == group && has(it) }.map { it to values.getValue(it) }

    /**
     * Calories implied by the macros, useful for sanity-checking a model's estimate
     * against its own numbers (Atwater factors: 4/4/9 kcal per gram).
     */
    fun energyFromMacros(): Double =
        amountOr0(Nutrient.PROTEIN) * 4 + amountOr0(Nutrient.CARBS) * 4 + amountOr0(Nutrient.FAT) * 9

    companion object {
        val EMPTY = Nutrients()

        fun of(vararg pairs: Pair<Nutrient, Double>) = Nutrients(pairs.toMap())

        fun sum(items: Iterable<Nutrients>): Nutrients =
            items.fold(EMPTY) { acc, next -> acc + next }
    }
}

/** Formats an amount the way a human reads it: no false precision, no "0.30000000004 g". */
fun formatAmount(amount: Double, unit: NutrientUnit): String {
    val rounded = when {
        unit == NutrientUnit.KCAL -> amount.roundToInt().toString()
        abs(amount) >= 100 -> amount.roundToInt().toString()
        abs(amount) >= 10 -> String.format("%.1f", amount)
        abs(amount) >= 1 -> String.format("%.1f", amount)
        else -> String.format("%.2f", amount)
    }
    return rounded.removeSuffix(".0").removeSuffix(".00")
}

fun Nutrient.format(amount: Double): String = "${formatAmount(amount, unit)} ${unit.label}"
