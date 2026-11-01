package me.rerere.locallm

import android.content.Context
import android.opengl.EGL14
import android.os.Build
import android.os.Process

/** Native loading and EGL are real probes; model-specific GPU initialization is still required. */
object LocalRuntimeSupport {
    enum class Reason { NATIVE_LIBRARY, CPU_ONLY_RUNTIME, GPU_DRIVER, READY }
    data class Capabilities(
        val processAbi: String?,
        val deviceAbis: List<String>,
        val cpuAvailable: Boolean,
        val gpuAvailable: Boolean,
        val reason: Reason,
        val nativeError: String? = null,
    )

    private val loaded = mutableMapOf<String, Result<Unit>>()
    @Synchronized private fun load(name: String) = loaded.getOrPut(name) { runCatching { System.loadLibrary(name) } }

    fun probe(context: Context, runtime: LocalRuntime): Capabilities {
        val abi = AbiCompatibility.processAbi(System.getProperty("os.arch"), context.applicationInfo.nativeLibraryDir, Process.is64Bit())
        val library = if (runtime == LocalRuntime.LiteRT) "litertlm_jni" else "llamajni"
        val native = load(library)
        val reason = when {
            native.isFailure -> Reason.NATIVE_LIBRARY
            // The JNI entry point explicitly sets n_gpu_layers=0, regardless of driver availability.
            runtime == LocalRuntime.LlamaCpp -> Reason.CPU_ONLY_RUNTIME
            load("LiteRtClGlAccelerator").isFailure || !hasEgl3() -> Reason.GPU_DRIVER
            else -> Reason.READY
        }
        return Capabilities(abi, Build.SUPPORTED_ABIS.toList(), native.isSuccess,
            reason == Reason.READY, reason, native.exceptionOrNull()?.javaClass?.simpleName)
    }

    private fun hasEgl3(): Boolean {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return false
        var context = EGL14.EGL_NO_CONTEXT
        try {
            if (!EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0)) return false
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x40,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE), 0,
                    configs, 0, 1, count, 0) || count[0] == 0) return false
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            return context != EGL14.EGL_NO_CONTEXT
        } catch (_: Exception) { return false
        } finally {
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
}
