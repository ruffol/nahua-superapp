package com.nahuanotoca.superapp.core.model

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Dominio del bazar/trueque comunitario.
 *
 * Regla del producto: nada se truequea "a ojo". Cada objeto se valora con [BarterValuator] a partir
 * del precio de referencia (lo que cuesta nuevo), el estado, la categoría, los meses de uso y los
 * accesorios. Con dos valoraciones, [BarterValuator.compare] decide si el intercambio conviene, está
 * parejo o si hace falta compensar con efectivo antes de cerrar el trato.
 *
 * Todo el dinero se maneja en centavos ([Long]) y la aritmética vive aquí: ni la mini app ni la UI
 * inventan precios por su cuenta.
 */

/** Estado físico declarado por quien publica. El multiplicador se aplica al valor de reventa. */
enum class ItemCondition(val label: String, val multiplier: Double) {
    NEW("Nuevo, sin abrir", 1.28),
    LIKE_NEW("Como nuevo", 1.14),
    GOOD("Buen estado", 1.00),
    WORN("Usado, con marcas", 0.78),
    FOR_PARTS("Para refacción", 0.45),
    ;

    companion object {
        fun fromId(id: String?): ItemCondition {
            val key = id?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: GOOD
        }
    }
}

/**
 * Categoría del objeto. [resale] es la fracción del precio nuevo que conserva un artículo en buen
 * estado, [yearlyLoss] cuánto pierde por año de uso, [floor] el piso de reventa y [demand] el pulso
 * de la comunidad por ese tipo de objeto (herramientas y muebles se mueven rápido en Colima).
 */
enum class BarterCategory(
    val label: String,
    val emoji: String,
    val resale: Double,
    val yearlyLoss: Double,
    val floor: Double,
    val demand: Double,
) {
    PHONES("Celulares", "📱", 0.58, 0.34, 0.28, 1.04),
    COMPUTERS("Computadoras", "💻", 0.60, 0.32, 0.30, 1.02),
    ELECTRONICS("Electrónica", "🎧", 0.62, 0.30, 0.30, 1.00),
    APPLIANCES("Línea blanca", "🧺", 0.55, 0.22, 0.32, 1.02),
    FURNITURE("Muebles", "🪑", 0.50, 0.12, 0.34, 1.06),
    TOOLS("Herramientas", "🔧", 0.60, 0.10, 0.40, 1.08),
    BIKES("Bicis", "🚲", 0.55, 0.12, 0.34, 1.06),
    SPORTS("Deportes", "⚽", 0.50, 0.12, 0.32, 1.00),
    CLOTHING("Ropa", "👕", 0.40, 0.20, 0.25, 0.96),
    BOOKS("Libros", "📚", 0.36, 0.08, 0.28, 0.98),
    TOYS("Juguetes", "🧸", 0.45, 0.14, 0.28, 0.98),
    TEXTILES("Textiles y bordados", "🧶", 0.55, 0.04, 0.40, 1.06),
    CRAFTS("Artesanías", "🏺", 0.60, 0.03, 0.42, 1.08),
    FOOD("Comida y conservas", "🫙", 0.70, 0.08, 0.30, 1.05),
    PLANTS("Plantas y semillas", "🌱", 0.55, 0.10, 0.30, 1.02),
    ART("Arte y pintura", "🎨", 0.60, 0.02, 0.40, 1.05),
    SCHOOL("Escolar", "📓", 0.35, 0.10, 0.25, 1.00),
    MUSIC("Instrumentos", "🎸", 0.58, 0.09, 0.34, 1.02),
    COLLECTIBLES("Coleccionables", "🪙", 0.78, 0.03, 0.45, 1.10),
    OTHER("Otro", "📦", 0.50, 0.16, 0.30, 1.00),
    ;

    companion object {
        fun fromId(id: String?): BarterCategory {
            val key = id?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: OTHER
        }
    }
}

/** Datos que la persona captura al publicar. [referenceCents] es lo que cuesta nuevo. */
data class BarterInput(
    val referenceCents: Long,
    val category: BarterCategory = BarterCategory.OTHER,
    val condition: ItemCondition = ItemCondition.GOOD,
    val ageMonths: Int = 0,
    val complete: Boolean = true,
    val hasReceipt: Boolean = false,
)

/** Qué tanto se puede mover la aguja: los objetos de alta deprecación valen "más o menos". */
enum class ValuationConfidence(val label: String) {
    ALTA("alta"),
    MEDIA("media"),
    BAJA("baja"),
}

enum class FactorTone { POSITIVE, NEGATIVE, NEUTRAL }

/** Un renglón del desglose: "Uso: 2 años · −24 %" para que el número no sea una caja negra. */
data class ValuationFactor(
    val label: String,
    val percent: Int,
    val tone: FactorTone,
)

data class ItemValuation(
    val referenceCents: Long,
    val estimatedCents: Long,
    val lowCents: Long,
    val highCents: Long,
    /** Lo mínimo que conviene aceptar por trueque: 85 % del valor estimado. */
    val barterMinCents: Long,
    val confidence: ValuationConfidence,
    val factors: List<ValuationFactor> = emptyList(),
) {
    val hasValue: Boolean get() = estimatedCents > 0L

    companion object {
        val EMPTY = ItemValuation(
            referenceCents = 0L,
            estimatedCents = 0L,
            lowCents = 0L,
            highCents = 0L,
            barterMinCents = 0L,
            confidence = ValuationConfidence.BAJA,
        )
    }
}

enum class TradeVerdict(val label: String) {
    FAVORABLE("A tu favor"),
    JUSTO("Parejo"),
    DESFAVORABLE("Pide compensación"),
}

/** Resultado de comparar dos objetos antes de truequear. [deltaCents] es lo que recibo menos lo que doy. */
data class TradeBalance(
    val mineCents: Long,
    val theirsCents: Long,
    val deltaCents: Long,
    val ratioPercent: Int,
    val verdict: TradeVerdict,
    val cashTopUpCents: Long,
    val message: String,
)

object BarterValuator {

    /** Redondeo a $5: los precios de tianguis no llevan centavos raros. */
    private const val STEP_CENTS = 500L

    private const val MIN_ESTIMATE_CENTS = 2_000L

    /** Un objeto usado nunca vale "casi lo nuevo", ni aunque la categoría no deprecie. */
    private const val MAX_RETAINED = 0.92

    private const val RANGE_MARGIN = 0.12

    private const val BARTER_ACCEPT_RATIO = 0.85

    /** Cuánto puede diferir un trueque para seguir siendo parejo (±12 %). */
    private const val FAIR_BAND = 0.12

    private const val MIN_TOP_UP_CENTS = 2_000L

    private const val TOP_UP_STEP_CENTS = 5_000L

    fun valuate(input: BarterInput): ItemValuation {
        val reference = input.referenceCents
        if (reference <= 0L) return ItemValuation.EMPTY

        val category = input.category
        val months = input.ageMonths.coerceIn(0, 360)
        val years = months / 12.0
        val ageFactor = (1.0 - category.yearlyLoss * years).coerceAtLeast(category.floor)
        val completeFactor = if (input.complete) 1.0 else 0.92
        val receiptFactor = if (input.hasReceipt) 1.04 else 1.0
        val marketFactor = category.resale * category.demand

        val raw = reference.toDouble() *
            marketFactor *
            input.condition.multiplier *
            ageFactor *
            completeFactor *
            receiptFactor

        val cap = reference * MAX_RETAINED
        val estimated = roundTo(minOf(raw, cap), STEP_CENTS).coerceAtLeast(MIN_ESTIMATE_CENTS)
        val low = roundTo(estimated * (1.0 - RANGE_MARGIN), STEP_CENTS).coerceAtMost(estimated)
        val high = roundTo(estimated * (1.0 + RANGE_MARGIN), STEP_CENTS).coerceAtLeast(estimated)
        val barterMin = roundTo(estimated * BARTER_ACCEPT_RATIO, STEP_CENTS)

        val factors = buildList {
            add(
                ValuationFactor(
                    label = "Reventa de " + category.label.lowercase(),
                    percent = percentOf(marketFactor),
                    tone = if (marketFactor < 1.0) FactorTone.NEGATIVE else FactorTone.POSITIVE,
                ),
            )
            if (input.condition != ItemCondition.GOOD) {
                add(
                    ValuationFactor(
                        label = "Estado: " + input.condition.label.lowercase(),
                        percent = percentOf(input.condition.multiplier),
                        tone = if (input.condition.multiplier < 1.0) FactorTone.NEGATIVE else FactorTone.POSITIVE,
                    ),
                )
            }
            if (months > 0) {
                add(
                    ValuationFactor(
                        label = "Uso: " + ageLabel(months),
                        percent = percentOf(ageFactor),
                        tone = FactorTone.NEGATIVE,
                    ),
                )
            }
            if (!input.complete) {
                add(ValuationFactor("Sin caja ni todos los accesorios", -8, FactorTone.NEGATIVE))
            }
            if (input.hasReceipt) {
                add(ValuationFactor("Con factura o garantía", 4, FactorTone.POSITIVE))
            }
            if (raw > cap) {
                add(
                    ValuationFactor(
                        label = "Tope de reventa (92 % del precio nuevo)",
                        percent = percentOf(cap / raw),
                        tone = FactorTone.NEUTRAL,
                    ),
                )
            }
        }

        return ItemValuation(
            referenceCents = reference,
            estimatedCents = estimated,
            lowCents = low,
            highCents = high,
            barterMinCents = barterMin,
            confidence = confidenceOf(category, input.condition, reference),
            factors = factors,
        )
    }

    /**
     * ¿Me conviene el trueque? Compara lo que recibo con lo que doy. Si me ofrecen 12 % menos o más,
     * sugiere la compensación en efectivo (redondeada a $50) para que el trato siga parejo.
     */
    fun compare(mine: ItemValuation, theirs: ItemValuation): TradeBalance {
        val mineCents = mine.estimatedCents
        val theirsCents = theirs.estimatedCents
        if (mineCents <= 0L || theirsCents <= 0L) {
            return TradeBalance(
                mineCents = mineCents,
                theirsCents = theirsCents,
                deltaCents = theirsCents - mineCents,
                ratioPercent = 0,
                verdict = TradeVerdict.JUSTO,
                cashTopUpCents = 0L,
                message = "Falta valorar uno de los dos objetos: sin número no hay trato.",
            )
        }

        val delta = theirsCents - mineCents
        val ratio = theirsCents.toDouble() / mineCents.toDouble()
        val ratioPercent = ((ratio - 1.0) * 100.0).roundToInt()

        return when {
            ratio >= 1.0 + FAIR_BAND -> TradeBalance(
                mineCents = mineCents,
                theirsCents = theirsCents,
                deltaCents = delta,
                ratioPercent = ratioPercent,
                verdict = TradeVerdict.FAVORABLE,
                cashTopUpCents = 0L,
                message = "Recibes " + Money.format(delta) + " más de valor" +
                    (if (ratioPercent in -200..200) " (" + ratioPercent + " %)" else "") +
                    ". Te conviene truequear.",
            )

            ratio <= 1.0 - FAIR_BAND -> {
                val topUp = roundTo((mineCents - theirsCents).toDouble(), TOP_UP_STEP_CENTS)
                    .coerceAtLeast(MIN_TOP_UP_CENTS)
                TradeBalance(
                    mineCents = mineCents,
                    theirsCents = theirsCents,
                    deltaCents = delta,
                    ratioPercent = ratioPercent,
                    verdict = TradeVerdict.DESFAVORABLE,
                    cashTopUpCents = topUp,
                    message = "Te ofrecen " + Money.format(mineCents - theirsCents) +
                        " menos de lo que das. Pide " + Money.format(topUp) +
                        " en efectivo o un objeto extra.",
                )
            }

            else -> TradeBalance(
                mineCents = mineCents,
                theirsCents = theirsCents,
                deltaCents = delta,
                ratioPercent = ratioPercent,
                verdict = TradeVerdict.JUSTO,
                cashTopUpCents = 0L,
                message = "Valores parecidos (" + ratioPercent + " %): pueden cerrar el trato sin efectivo.",
            )
        }
    }

    private fun confidenceOf(
        category: BarterCategory,
        condition: ItemCondition,
        reference: Long,
    ): ValuationConfidence = when {
        category == BarterCategory.OTHER || reference < 10_000L -> ValuationConfidence.BAJA
        category.yearlyLoss >= 0.28 || condition == ItemCondition.FOR_PARTS -> ValuationConfidence.MEDIA
        else -> ValuationConfidence.ALTA
    }

    private fun percentOf(factor: Double): Int = ((factor - 1.0) * 100.0).roundToInt()

    private fun roundTo(value: Double, step: Long): Long {
        if (step <= 0L) return value.roundToLong()
        return (value / step).roundToLong() * step
    }

    private fun ageLabel(months: Int): String {
        if (months <= 0) return "sin uso"
        if (months < 12) return if (months == 1) "1 mes" else months.toString() + " meses"
        val years = months / 12
        val rest = months % 12
        val yearsText = if (years == 1) "1 año" else years.toString() + " años"
        return when (rest) {
            0 -> yearsText
            1 -> yearsText + " y 1 mes"
            else -> yearsText + " y " + rest + " meses"
        }
    }
}
