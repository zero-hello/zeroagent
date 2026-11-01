package me.rerere.locallm

import android.content.Context

/** CPU/GPU preference and eligibility only; actual model loading records the active backend. */
object AcceleratorProbe {

    data class LiteRtCapabilities(
        val isQualcomm: Boolean,
        val qnnLibrarySupported: Boolean,
        val gpuDelegateSupported: Boolean,
        val nnapiSupported: Boolean,
    )

    fun pickLiteRt(caps: LiteRtCapabilities): String =
        if (caps.gpuDelegateSupported) "GPU" else "CPU"

    /** Conservative default for the device family with previously observed native GPU crashes. */
    fun defaultForceCpu(socManufacturer: String?, socModel: String?): Boolean {
        return socManufacturer?.equals("Google", ignoreCase = true) == true ||
            socModel?.contains("Tensor", ignoreCase = true) == true
    }

    fun probeLiteRt(context: Context, forceCpu: Boolean = false): String =
        if (!forceCpu && LocalRuntimeSupport.probe(context, LocalRuntime.LiteRT).gpuAvailable) "GPU" else "CPU"
}
