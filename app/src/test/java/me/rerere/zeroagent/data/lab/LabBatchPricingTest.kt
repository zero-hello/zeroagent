package me.rerere.zeroagent.data.lab

import me.rerere.ai.provider.ModelPricing
import me.rerere.ai.provider.OffPeakRates
import me.rerere.zeroagent.data.usage.PriceBranch
import me.rerere.zeroagent.data.usage.PriceRates
import me.rerere.zeroagent.data.usage.UsagePriceResolver
import me.rerere.zeroagent.data.usage.UsageRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class LabBatchPricingTest {
    @Test
    fun keepsDifferentCurrenciesAndUnavailableBillingSeparate() {
        val twd = record("one", "TWD", 200_000, timestamp = 1_000)
        val usd = record("two", "USD", 10_000, actualUsd = 0.023)
        val unknown = record("three")
        val summary = summarizeBatchUsage(listOf(twd, usd, unknown))
        assertEquals(3, summary.reports)
        assertEquals(300L, summary.inputTokens)
        assertEquals(150L, summary.outputTokens)
        assertEquals(BigDecimal("0.023"), summary.providerCostUsd)
        assertEquals(1, summary.providerCostReports)
        assertEquals(mapOf("TWD" to BigDecimal("0.200000"), "USD" to BigDecimal("0.010000")), summary.estimates)
        assertEquals(setOf(1_000L), summary.priceUpdatesMs)
        assertEquals(true, summary.estimatedPricesWithoutTimestamp)
        assertNull(summarizeBatchUsage(listOf(unknown)).providerCostUsd)
    }

    @Test
    fun localAndProviderRatesIdentifyTheirCurrencyAndSourceTime() {
        val local = UsagePriceResolver.ratesAt(ModelPricing(inputPerMillion = 1.0, currency = "twd", updatedAtMs = 2_000), 5_000)
        val catalog = UsagePriceResolver.ratesAt(null, 5_000, fallbackInputPerToken = 0.001)
        assertEquals("TWD", local?.currency)
        assertEquals(2_000L, local?.updatedAtMs)
        assertEquals("USD", catalog?.currency)
        assertNull(catalog?.updatedAtMs)
    }

    private fun record(id: String, currency: String? = null, micros: Long? = null,
        timestamp: Long? = null, actualUsd: Double? = null) = UsageRecordEntity(
        id = id, createdAtMs = 0, purpose = "LAB_BATCH", inputTokens = 100, outputTokens = 50,
        totalTokens = 150, cachedTokens = 0, cachedTokensReported = false,
        providerCostUsd = actualUsd, batchEstimateCurrency = currency, batchEstimateMicros = micros,
        modelPriceUpdatedAtMs = timestamp,
    )

    @Test
    fun usesCurrentLedgerRatesAndExplicitOutputScenario() {
        val pricing = ModelPricing(inputPerMillion = 2.0, outputPerMillion = 5.0,
            offPeak = OffPeakRates(outputPerMillion = 3.0))
        val resolved = checkNotNull(UsagePriceResolver.ratesAt(pricing, 0L))
        val estimate = checkNotNull(estimateBatchCost(2_000, 500, resolved.rates))
        assertEquals(PriceBranch.OFF_PEAK, resolved.branch)
        assertEquals(0, BigDecimal("0.004").compareTo(estimate.inputOnly))
        assertEquals(0, BigDecimal("0.0055").compareTo(estimate.withOutput))
    }

    @Test
    fun preservesUnknownPricesAndExplicitlyZeroPrices() {
        assertNull(estimateBatchCost(2_000, 500, PriceRates(2.0, null, null, null)))
        assertNull(estimateBatchCost(2_000, 500, PriceRates(Double.NaN, 2.0, null, null)))
        val zero = checkNotNull(estimateBatchCost(2_000, 500, PriceRates(0.0, 0.0, null, null)))
        assertEquals(0, zero.withOutput.signum())
    }
}
