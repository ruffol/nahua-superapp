package com.nahuanotoca.superapp.core.miniapp

import com.nahuanotoca.superapp.core.model.BarterCategory
import com.nahuanotoca.superapp.core.model.BarterInput
import com.nahuanotoca.superapp.core.model.BarterValuator
import com.nahuanotoca.superapp.core.model.FactorTone
import com.nahuanotoca.superapp.core.model.ItemCondition
import com.nahuanotoca.superapp.core.model.ItemValuation
import com.nahuanotoca.superapp.core.model.TradeBalance
import org.json.JSONArray
import org.json.JSONObject

/**
 * Traduce entre el JSON del bazar y el dominio. La valoración vive en Kotlin
 * ([BarterValuator]); la mini app sólo pinta el resultado, así que un objeto nunca se truequea con
 * una cuenta hecha en JavaScript.
 */
object BarterCodec {

    fun input(payload: JSONObject?): BarterInput {
        val json = payload ?: JSONObject()
        return BarterInput(
            referenceCents = json.optLong("referenceCents", 0L),
            category = BarterCategory.fromId(json.optString("category")),
            condition = ItemCondition.fromId(json.optString("condition")),
            ageMonths = json.optInt("ageMonths", 0),
            complete = json.optBoolean("complete", true),
            hasReceipt = json.optBoolean("hasReceipt", false),
        )
    }

    fun valuationJson(valuation: ItemValuation): JSONObject {
        val factors = JSONArray()
        valuation.factors.forEach { factor ->
            factors.put(
                JSONObject()
                    .put("label", factor.label)
                    .put("percent", factor.percent)
                    .put("tone", toneId(factor.tone)),
            )
        }
        return JSONObject()
            .put("status", "ok")
            .put("hasValue", valuation.hasValue)
            .put("referenceCents", valuation.referenceCents)
            .put("estimatedCents", valuation.estimatedCents)
            .put("lowCents", valuation.lowCents)
            .put("highCents", valuation.highCents)
            .put("barterMinCents", valuation.barterMinCents)
            .put("confidence", valuation.confidence.name.lowercase())
            .put("confidenceLabel", valuation.confidence.label)
            .put("factors", factors)
    }

    fun balanceJson(balance: TradeBalance): JSONObject = JSONObject()
        .put("status", "ok")
        .put("mineCents", balance.mineCents)
        .put("theirsCents", balance.theirsCents)
        .put("deltaCents", balance.deltaCents)
        .put("ratioPercent", balance.ratioPercent)
        .put("verdict", balance.verdict.name.lowercase())
        .put("verdictLabel", balance.verdict.label)
        .put("cashTopUpCents", balance.cashTopUpCents)
        .put("message", balance.message)

    fun errorJson(code: String): JSONObject = JSONObject()
        .put("status", "error")
        .put("error", code)

    private fun toneId(tone: FactorTone): String = when (tone) {
        FactorTone.POSITIVE -> "positive"
        FactorTone.NEGATIVE -> "negative"
        FactorTone.NEUTRAL -> "neutral"
    }
}
