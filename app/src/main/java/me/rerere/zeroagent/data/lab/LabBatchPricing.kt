package me.rerere.zeroagent.data.lab

import me.rerere.zeroagent.data.usage.PriceRates
import me.rerere.zeroagent.data.usage.UsageRecordEntity
import java.math.BigDecimal

internal data class BatchCostRange(
    val inputOnly: BigDecimal,
    val withOutput: BigDecimal,
    val assumedOutputTokens: Long,
)

internal fun estimateBatchCost(inputTokens: Long, outputTokens: Long, rates: PriceRates?): BatchCostRange? {
    val inputRate = rates?.inputPerMillion?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    val outputRate = rates.outputPerMillion?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    if (inputTokens <= 0 || outputTokens < 0) return null
    val inputCost = inputRate.toBigDecimal().multiply(inputTokens.toBigDecimal()).movePointLeft(6)
    val outputCost = outputRate.toBigDecimal().multiply(outputTokens.toBigDecimal()).movePointLeft(6)
    return BatchCostRange(inputCost, inputCost + outputCost, outputTokens)
}

internal data class BatchUsageSummary(
    val reports: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val providerCostUsd: BigDecimal?,
    val providerCostReports: Int,
    val estimates: Map<String, BigDecimal>,
    val estimateReports: Int,
    val priceUpdatesMs: Set<Long>,
    val estimatedPricesWithoutTimestamp: Boolean,
)

internal fun summarizeBatchUsage(records: List<UsageRecordEntity>): BatchUsageSummary {
    val providerCosts = records.mapNotNull { it.providerCostUsd?.takeIf { cost -> cost.isFinite() && cost >= 0 } }
    val estimated = records.filter { it.batchEstimateCurrency != null && (it.batchEstimateMicros ?: -1L) >= 0 }
    return BatchUsageSummary(
        reports = records.size,
        inputTokens = records.sumOf { it.inputTokens.toLong().coerceAtLeast(0L) },
        outputTokens = records.sumOf { it.outputTokens.toLong().coerceAtLeast(0L) },
        providerCostUsd = providerCosts.takeIf { it.isNotEmpty() }?.sumOf { it.toBigDecimal() },
        providerCostReports = providerCosts.size,
        estimates = estimated.groupBy { requireNotNull(it.batchEstimateCurrency) }.mapValues { (_, rows) ->
            rows.sumOf { requireNotNull(it.batchEstimateMicros).toBigDecimal() }.movePointLeft(6)
        },
        estimateReports = estimated.size,
        priceUpdatesMs = estimated.mapNotNull { it.modelPriceUpdatedAtMs }.toSet(),
        estimatedPricesWithoutTimestamp = estimated.any { it.modelPriceUpdatedAtMs == null },
    )
}
