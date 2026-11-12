package me.rerere.workspace

/** Select the host-kernel ELF architecture, independently of translated APK/JNI ABIs. */
object RootfsDefaults {
    private const val RELEASE = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release"

    fun downloadUrl(kernelArch: String?, abis: List<String>): String? =
        archFor(kernelArch, abis)?.let { "$RELEASE/ubuntu-base-24.04.3-base-$it.tar.gz" }

    fun archFor(kernelArch: String?, abis: List<String>): String? {
        val hint = kernelArch?.trim()?.lowercase()
        // A known but unsupported kernel must never fall through to a translated arm64 ABI.
        if (!hint.isNullOrBlank() && hint != "unknown") return token(hint)
        return abis.firstOrNull()?.let { token(it.trim().lowercase()) }
    }

    private fun token(arch: String): String? = when (arch) {
        "aarch64", "arm64", "arm64-v8a" -> "arm64"
        "x86_64", "amd64" -> "amd64"
        "armv7l", "armv8l", "arm", "armhf", "armeabi-v7a" -> "armhf"
        else -> null
    }
}
