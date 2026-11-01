package me.rerere.llamacpp

import java.io.File

/**
 * CPU GGUF BERT/XLM-R sentence encoder with mean pooling. Blocking: call on an IO dispatcher.
 * Pass E5's `query: ` / `passage: ` prefixes yourself. Inputs are never silently truncated.
 * One embedding model may be open process-wide; chat has its own model/context and may coexist.
 *
 * [dimension] is the model's actual output width, not a catalog constant. A different model
 * (even one with the same width) requires rebuilding the caller's stored vector index.
 * Always close with `use` or `finally`; close waits for an in-flight embedding to finish.
 */
class LlamaEmbeddingRuntime private constructor(
    private val native: EmbeddingNative,
    private var handle: Long,
    val dimension: Int,
    val maxTokens: Int,
    /** Tensor bytes, excluding tokenizer, context, compute buffers and the mapped GGUF file. */
    val modelBytes: Long,
) : AutoCloseable {
    /** Returns a fresh, finite, nonzero, L2-normalized vector of exactly [dimension] floats. */
    @Synchronized
    fun embed(text: String): FloatArray {
        check(handle != 0L) { "embedding runtime is closed" }
        require(text.isNotBlank()) { "embedding input is blank" }
        require(text.length <= MAX_INPUT_BYTES) { "embedding input exceeds $MAX_INPUT_BYTES UTF-8 bytes" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_INPUT_BYTES) { "embedding input exceeds $MAX_INPUT_BYTES UTF-8 bytes" }
        return normalizeEmbedding(native.embed(handle, bytes), dimension)
    }

    @Synchronized
    override fun close() {
        if (handle == 0L) return
        native.close(handle)
        handle = 0L
    }

    companion object {
        const val MAX_INPUT_BYTES = 32_768
        const val MAX_MODEL_BYTES = 512L * 1024 * 1024
        internal const val MAX_DIMENSION = 1024

        /**
         * Loads a single-file GGUF encoder, not a chat or reranker model. Token limit includes
         * model-added special tokens. Threads must be 1..4; token limit must be 8..512.
         *
         * Bundled llama.cpp reserves vocabSize * maxTokens * 4 bytes for logits even for
         * encoders. For multilingual-e5-small (~250k vocabulary), 512 tokens alone cost
         * ~488 MiB; use 128/256 on constrained devices. These limits bound allocations,
         * not an assurance that the device has enough free RAM alongside a chat model.
         */
        fun open(modelPath: String, maxTokens: Int = 512, nThreads: Int = 2): LlamaEmbeddingRuntime =
            open(modelPath, maxTokens, nThreads, JniEmbeddingNative)

        internal fun open(
            modelPath: String,
            maxTokens: Int,
            nThreads: Int,
            native: EmbeddingNative,
        ): LlamaEmbeddingRuntime {
            require(maxTokens in 8..512) { "embedding maxTokens must be in 8..512" }
            require(nThreads in 1..4) { "embedding nThreads must be in 1..4" }
            require(modelPath.isNotBlank() && '\u0000' !in modelPath) { "invalid GGUF path" }
            val file = File(modelPath)
            require(file.isFile && file.canRead()) { "GGUF is not a readable file" }
            require(file.length() in 1..MAX_MODEL_BYTES) { "GGUF exceeds 512 MiB or is empty" }
            val handle = native.open(file.absolutePath.toByteArray(Charsets.UTF_8), maxTokens, nThreads)
            check(handle != 0L) { "embedding model load returned no handle" }
            try {
                val dimension = native.dimension(handle)
                check(dimension in 1..MAX_DIMENSION) { "invalid embedding output dimension: $dimension" }
                val modelBytes = native.modelBytes(handle)
                check(modelBytes in 1..MAX_MODEL_BYTES) { "invalid embedding model size: $modelBytes" }
                return LlamaEmbeddingRuntime(native, handle, dimension, maxTokens, modelBytes)
            } catch (failure: Throwable) {
                try {
                    native.close(handle)
                } catch (cleanup: Throwable) {
                    failure.addSuppressed(cleanup)
                }
                throw failure
            }
        }
    }
}

/** Double accumulation avoids overflow/underflow for finite Float inputs, including subnormals. */
internal fun normalizeEmbedding(vector: FloatArray, dimension: Int): FloatArray {
    check(dimension in 1..LlamaEmbeddingRuntime.MAX_DIMENSION && vector.size == dimension) {
        "embedding output dimension mismatch: expected $dimension, got ${vector.size}"
    }
    var squaredNorm = 0.0
    for (value in vector) {
        check(value.isFinite()) { "embedding contains a non-finite value" }
        squaredNorm += value.toDouble() * value.toDouble()
    }
    check(squaredNorm > 0.0 && squaredNorm.isFinite()) { "embedding has zero or invalid norm" }
    val norm = kotlin.math.sqrt(squaredNorm)
    return FloatArray(dimension) { (vector[it].toDouble() / norm).toFloat() }
}

internal interface EmbeddingNative {
    fun open(path: ByteArray, maxTokens: Int, nThreads: Int): Long
    fun dimension(handle: Long): Int
    fun modelBytes(handle: Long): Long
    fun embed(handle: Long, text: ByteArray): FloatArray
    fun close(handle: Long)
}

internal object JniEmbeddingNative : EmbeddingNative {
    init { System.loadLibrary("llamajni") }
    external override fun open(path: ByteArray, maxTokens: Int, nThreads: Int): Long
    external override fun dimension(handle: Long): Int
    external override fun modelBytes(handle: Long): Long
    external override fun embed(handle: Long, text: ByteArray): FloatArray
    external override fun close(handle: Long)
}
