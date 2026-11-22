package me.rerere.llamacpp

/**
 * A curated GGUF the Settings -> Local · llama.cpp picker offers. Mirrors
 * `me.rerere.locallm.litert.LiteRtCatalogEntry`'s shape, minus its `config()`, which returns a
 * LiteRT-specific config type llama.cpp has no equivalent for.
 */
data class LlamaCppCatalogEntry(
    val displayName: String, // e.g. "Qwen3 4B"
    val repo: String,        // HuggingFace repo path, e.g. "Qwen/Qwen3-4B-GGUF"
    val file: String,        // File inside the repo, e.g. "Qwen3-4B-Q4_K_M.gguf"
    val sizeBytes: Long,
    val minMemGb: Int,
    val tags: List<String> = emptyList(),
    val sha256: String? = null,
) {
    /** Pre-built download URL on HuggingFace's `resolve` path. Same format `ModelInstall`
     *  already normalises and validates. */
    fun resolveUrl(): String = "https://huggingface.co/$repo/resolve/main/$file"
}

object LlamaCppCatalog {
    /** Public metadata verified 2026-10-17; existing entries retained. Small text-only
     * models do not gain tool support merely by appearing in this catalog. */
    val ENTRIES: List<LlamaCppCatalogEntry> = listOf(
        LlamaCppCatalogEntry(
            displayName = "Qwen3 0.6B",
            repo = "Qwen/Qwen3-0.6B-GGUF",
            file = "Qwen3-0.6B-Q8_0.gguf",
            sizeBytes = 639446688L,
            sha256 = "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031",
            minMemGb = 4,
            tags = listOf("thinking", "tools"),
        ),
        LlamaCppCatalogEntry(
            displayName = "Qwen3 1.7B",
            // Community requant (see class doc above for why), not the official Qwen repo.
            repo = "unsloth/Qwen3-1.7B-GGUF",
            file = "Qwen3-1.7B-Q4_K_M.gguf",
            sizeBytes = 1107409472L,
            minMemGb = 4,
            tags = listOf("thinking", "tools"),
        ),
        LlamaCppCatalogEntry(
            displayName = "Qwen3 4B",
            repo = "Qwen/Qwen3-4B-GGUF",
            file = "Qwen3-4B-Q4_K_M.gguf",
            sizeBytes = 2497280256L,
            minMemGb = 8,
            tags = listOf("thinking", "tools"),
        ),
        LlamaCppCatalogEntry(
            displayName = "Qwen2.5 0.5B Instruct",
            repo = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            file = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sizeBytes = 491400032L,
            minMemGb = 3,
            sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
        ),
        LlamaCppCatalogEntry(
            displayName = "SmolLM2 360M Instruct",
            repo = "HuggingFaceTB/SmolLM2-360M-Instruct-GGUF",
            file = "smollm2-360m-instruct-q8_0.gguf",
            sizeBytes = 386404992L,
            minMemGb = 2,
            sha256 = "48ab3034d0dd401fbc721eb1df3217902fee7dab9078992d66431f09b7750201",
        ),
        LlamaCppCatalogEntry(
            displayName = "SmolLM2 135M Instruct",
            repo = "bartowski/SmolLM2-135M-Instruct-GGUF",
            file = "SmolLM2-135M-Instruct-Q4_K_M.gguf",
            sizeBytes = 105454432L,
            minMemGb = 2,
            sha256 = "2e8040ceae7815abe0dcb3540b9995eaa1fa0d2ca9e797d0a635ae4433c68c2d",
        ),
        LlamaCppCatalogEntry(
            displayName = "SmolLM2 1.7B Instruct",
            repo = "HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF",
            file = "smollm2-1.7b-instruct-q4_k_m.gguf",
            sizeBytes = 1055609536L,
            minMemGb = 4,
            sha256 = "decd2598bc2c8ed08c19adc3c8fdd461ee19ed5708679d1c54ef54a5a30d4f33",
        ),
        LlamaCppCatalogEntry(
            displayName = "Qwen2.5 1.5B Instruct",
            repo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            file = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeBytes = 1117320736L,
            minMemGb = 4,
            sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
        ),
    ).sortedBy { it.sizeBytes }
}
