package com.nahuanotoca.superapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarterValuatorTest {

    private fun valora(
        referenceCents: Long = 100_000L,
        category: BarterCategory = BarterCategory.TOOLS,
        condition: ItemCondition = ItemCondition.GOOD,
        ageMonths: Int = 0,
        complete: Boolean = true,
        hasReceipt: Boolean = false,
    ): ItemValuation = BarterValuator.valuate(
        BarterInput(
            referenceCents = referenceCents,
            category = category,
            condition = condition,
            ageMonths = ageMonths,
            complete = complete,
            hasReceipt = hasReceipt,
        ),
    )

    @Test
    fun referencePriceIsRequired() {
        val empty = BarterValuator.valuate(BarterInput(referenceCents = 0L))
        assertFalse(empty.hasValue)
        assertEquals(0L, empty.estimatedCents)
        assertEquals(ValuationConfidence.BAJA, empty.confidence)
        assertTrue(empty.factors.isEmpty())
    }

    @Test
    fun calibrationForToolsInGoodCondition() {
        // $1,000 nuevos, herramientas, buen estado y sin uso: 64.8 % del precio nuevo, redondeado a $5.
        assertEquals(65_000L, valora().estimatedCents)
    }

    @Test
    fun betterConditionIsAlwaysWorthMore() {
        val nuevo = valora(condition = ItemCondition.NEW)
        val comoNuevo = valora(condition = ItemCondition.LIKE_NEW)
        val bueno = valora(condition = ItemCondition.GOOD)
        val usado = valora(condition = ItemCondition.WORN)
        val refaccion = valora(condition = ItemCondition.FOR_PARTS)

        assertTrue(nuevo.estimatedCents > comoNuevo.estimatedCents)
        assertTrue(comoNuevo.estimatedCents > bueno.estimatedCents)
        assertTrue(bueno.estimatedCents > usado.estimatedCents)
        assertTrue(usado.estimatedCents > refaccion.estimatedCents)
    }

    @Test
    fun olderObjectsLoseValue() {
        val nuevo = valora(ageMonths = 0)
        val unAnio = valora(ageMonths = 12)
        val tresAnios = valora(ageMonths = 36)

        assertTrue(nuevo.estimatedCents > unAnio.estimatedCents)
        assertTrue(unAnio.estimatedCents > tresAnios.estimatedCents)
    }

    @Test
    fun depreciationHasAFloor() {
        val diezAnios = valora(category = BarterCategory.BOOKS, referenceCents = 50_000L, ageMonths = 120)
        val treintaAnios = valora(category = BarterCategory.BOOKS, referenceCents = 50_000L, ageMonths = 360)

        assertEquals(diezAnios.estimatedCents, treintaAnios.estimatedCents)
        assertTrue(treintaAnios.estimatedCents > 0L)
    }

    @Test
    fun missingAccessoriesAndMissingReceiptMatter() {
        val completo = valora()
        val incompleto = valora(complete = false)
        val conFactura = valora(hasReceipt = true)

        assertTrue(incompleto.estimatedCents < completo.estimatedCents)
        assertTrue(conFactura.estimatedCents > completo.estimatedCents)
    }

    @Test
    fun estimateNeverReachesTheNewPrice() {
        val codiciado = BarterCategory.COLLECTIBLES
        val sellado = valora(category = codiciado, condition = ItemCondition.NEW, referenceCents = 500_000L)
        assertTrue(sellado.estimatedCents <= 500_000L * 0.92 + 500L)
    }

    @Test
    fun estimatedValueIsRoundedToFivePesos() {
        listOf(
            valora(referenceCents = 33_333L),
            valora(referenceCents = 87_650L, category = BarterCategory.PHONES, ageMonths = 7),
            valora(referenceCents = 12_345L, category = BarterCategory.CLOTHING),
        ).forEach { valuation ->
            assertEquals(0L, valuation.estimatedCents % 500L)
            assertEquals(0L, valuation.lowCents % 500L)
            assertEquals(0L, valuation.highCents % 500L)
        }
    }

    @Test
    fun cheapUnknownObjectsAreValuedWithLowConfidence() {
        assertEquals(ValuationConfidence.BAJA, valora(category = BarterCategory.OTHER).confidence)
        assertEquals(ValuationConfidence.BAJA, valora(referenceCents = 5_000L).confidence)
        assertEquals(ValuationConfidence.MEDIA, valora(category = BarterCategory.PHONES).confidence)
        assertEquals(ValuationConfidence.MEDIA, valora(condition = ItemCondition.FOR_PARTS).confidence)
        assertEquals(ValuationConfidence.ALTA, valora().confidence)
    }

    @Test
    fun factorsExplainTheNumber() {
        val valuation = valora(category = BarterCategory.PHONES, ageMonths = 24)
        val labels = valuation.factors.map { it.label }

        assertTrue(labels.any { it.contains("Reventa de celulares") })
        assertTrue(labels.any { it.contains("Uso: 2 años") })
        assertTrue(valuation.factors.all { it.percent in -100..100 })
    }

    @Test
    fun rangeWrapsTheEstimate() {
        val valuation = valora(ageMonths = 18)
        assertTrue(valuation.lowCents < valuation.estimatedCents)
        assertTrue(valuation.highCents > valuation.estimatedCents)
        assertTrue(valuation.barterMinCents < valuation.estimatedCents)
    }

    @Test
    fun sameInputGivesSameValuation() {
        val first = valora(category = BarterCategory.BIKES, ageMonths = 14, hasReceipt = true)
        val second = valora(category = BarterCategory.BIKES, ageMonths = 14, hasReceipt = true)
        assertEquals(first, second)
    }

    @Test
    fun favorableTradeWhenTheyOfferMoreValue() {
        val mine = valora(referenceCents = 100_000L)
        val theirs = valora(referenceCents = 140_000L)
        val balance = BarterValuator.compare(mine, theirs)

        assertEquals(TradeVerdict.FAVORABLE, balance.verdict)
        assertEquals(0L, balance.cashTopUpCents)
        assertTrue(balance.deltaCents > 0L)
        assertTrue(balance.message.contains("Te conviene truequear"))
    }

    @Test
    fun unfairTradeSuggestsCashCompensation() {
        val mine = valora(referenceCents = 200_000L)
        val theirs = valora(referenceCents = 100_000L)
        val balance = BarterValuator.compare(mine, theirs)

        assertEquals(TradeVerdict.DESFAVORABLE, balance.verdict)
        assertEquals(0L, balance.cashTopUpCents % 5_000L)
        assertTrue(balance.cashTopUpCents >= mine.estimatedCents - theirs.estimatedCents)
        assertTrue(balance.message.contains("Pide"))
    }

    @Test
    fun similarValuesCloseTheDealWithoutCash() {
        val mine = valora(referenceCents = 100_000L)
        val theirs = valora(referenceCents = 104_000L)
        val balance = BarterValuator.compare(mine, theirs)

        assertEquals(TradeVerdict.JUSTO, balance.verdict)
        assertEquals(0L, balance.cashTopUpCents)
        assertTrue(balance.message.contains("Valores parecidos"))
    }

    @Test
    fun comparingWithoutValuationsAsksForNumbers() {
        val balance = BarterValuator.compare(ItemValuation.EMPTY, valora())

        assertEquals(TradeVerdict.JUSTO, balance.verdict)
        assertEquals(0L, balance.cashTopUpCents)
        assertTrue(balance.message.contains("Falta valorar"))
    }

    @Test
    fun conditionAndCategoryParseFromClientIds() {
        assertEquals(ItemCondition.LIKE_NEW, ItemCondition.fromId("like_new"))
        assertEquals(ItemCondition.NEW, ItemCondition.fromId("new"))
        assertEquals(ItemCondition.GOOD, ItemCondition.fromId("no_existe"))
        assertEquals(ItemCondition.GOOD, ItemCondition.fromId(null))
        assertEquals(BarterCategory.PHONES, BarterCategory.fromId("phones"))
        assertEquals(BarterCategory.FURNITURE, BarterCategory.fromId("furniture"))
        assertEquals(BarterCategory.OTHER, BarterCategory.fromId(""))
    }
}
