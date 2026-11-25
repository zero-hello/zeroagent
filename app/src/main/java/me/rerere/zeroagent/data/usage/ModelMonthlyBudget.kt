package me.rerere.zeroagent.data.usage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.zeroagent.data.ai.ContextBudgetPlanner
import java.time.YearMonth
import java.time.ZoneOffset

internal class ModelBudgetException(message: String) : IllegalStateException(message)

internal class ModelMonthlyBudget(context: Context) {
    private val records = context.getSharedPreferences("monthly_model_budget", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    internal data class Charge(val key: String, val maximum: Long, val estimate: Long, val tokens: Boolean = false)
    internal data class Reservation(val charges: List<Charge>, val rates: PriceRates?, val maxOutputTokens: Int)

    suspend fun reserve(model: Model, messages: List<UIMessage>, maxOutput: Int?, turnId: String? = null): Reservation? {
        val generation = model.generation
        val monthly = generation.monthlyBudgetUsd.positiveLimit()
        val task = if (turnId != null) generation.taskBudgetUsd.positiveLimit() else null
        val outputLimit = if (turnId != null) generation.taskOutputTokenLimit?.takeIf { it > 0 } else null
        if (monthly == null && task == null && outputLimit == null) return null
        val requestedOutput = maxOutput?.takeIf { it > 0 } ?: outputLimit
            ?: throw ModelBudgetException("請先設定這個模型每次請求的輸出上限。")

        val period = YearMonth.now(ZoneOffset.UTC).toString()
        val taskPrefix = "$period:task:${model.id}:$turnId"
        val rates = if (monthly != null || task != null) {
            val price = UsagePriceResolver.ratesAt(model.pricing, System.currentTimeMillis(), model.pricePromptPerToken, model.priceCompletionPerToken)
            val available = price?.rates
            if (available == null || price.currency !in setOf(null, "USD") ||
                listOf(available.inputPerMillion, available.outputPerMillion).any { it == null || !it.isFinite() || it < 0 }
            ) throw ModelBudgetException("模型費率不完整。請在模型設定確認 USD 費率，再重新送出。")
            available
        } else null
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val maxOutputTokens = if (outputLimit == null) requestedOutput else {
                    val spent = records.getLong("$taskPrefix:tokens", 0L).coerceAtLeast(0L)
                    val remaining = outputLimit.toLong() - spent
                    if (remaining <= 0L) throw ModelBudgetException("單次任務的輸出額度已用完。已保留目前結果。")
                    minOf(requestedOutput.toLong(), remaining).toInt()
                }
                val estimate = rates?.let {
                    val inputCeiling = maxOf(requireNotNull(it.inputPerMillion), it.cacheWritePerMillion ?: 0.0, it.cacheHitPerMillion ?: 0.0)
                    val input = ContextBudgetPlanner.estimateContextTokens(messages).toLong()
                    kotlin.math.ceil(input * inputCeiling + maxOutputTokens * requireNotNull(it.outputPerMillion))
                        .takeIf { value -> value.isFinite() && value >= 0 && value <= Long.MAX_VALUE / 2 }?.toLong()
                        ?: throw ModelBudgetException("模型費率或輸出上限超出可估算範圍。")
                } ?: 0L
                val charges = buildList {
                    monthly?.let { add(Charge("$period:${model.id}", it, estimate)) }
                    task?.let { add(Charge("$taskPrefix:usd", it, estimate)) }
                    outputLimit?.let { add(Charge("$taskPrefix:tokens", it.toLong(), maxOutputTokens.toLong(), tokens = true)) }
                }
                val transaction = records.edit()
                for (charge in charges) {
                    val spent = records.getLong(charge.key, 0L).coerceAtLeast(0L)
                    if (spent > charge.maximum || charge.estimate > charge.maximum - spent) {
                        throw ModelBudgetException("模型預算不足，這次請求已暫停。")
                    }
                    transaction.putLong(charge.key, spent + charge.estimate)
                }
                records.all.keys.filterNot { it.startsWith("$period:") }.forEach(transaction::remove)
                if (!transaction.commit()) throw ModelBudgetException("無法保存模型預算，這次請求已暫停。")
                Reservation(charges, rates, maxOutputTokens)
            }
        }
    }

    suspend fun settle(reservation: Reservation?, usage: TokenUsage?) {
        if (reservation == null || usage == null || usage.totalTokens <= 0) return
        val actualCost = reservation.rates?.let { rates ->
            usage.cost?.takeIf { it.isFinite() && it >= 0 }?.let { kotlin.math.ceil(it * 1_000_000).toLong() }
                ?: UsagePriceResolver.costMicros(usage, rates)
        }
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val transaction = records.edit()
                for (charge in reservation.charges) {
                    val actual = if (charge.tokens) usage.completionTokens.toLong().takeIf { it > 0 } else actualCost
                    val previous = records.getLong(charge.key, -1L)
                    if (actual == null || previous < 0) continue
                    val other = (previous - charge.estimate).coerceAtLeast(0L)
                    transaction.putLong(charge.key, other + actual.coerceIn(0L, Long.MAX_VALUE - other))
                }
                transaction.commit()
            }
        }
    }

    private fun Double?.positiveLimit(): Long? = this?.takeIf { it.isFinite() && it > 0 }
        ?.let { kotlin.math.floor(it * 1_000_000).toLong() }
}