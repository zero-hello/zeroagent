package me.rerere.locallm

/** Device ABI lists can include translation ABIs; they do not identify this process. */
object AbiCompatibility {
    fun processAbi(osArch: String?, nativeLibraryDir: String?, is64Bit: Boolean): String? {
        val selected = nativeLibraryDir?.substringAfterLast('/')
        val hint = selected?.takeIf { it in setOf("arm64", "arm", "x86", "x86_64") } ?: osArch
        return when (hint?.trim()?.lowercase()) {
            "aarch64", "arm64", "arm64-v8a" -> if (is64Bit) "arm64-v8a" else "armeabi-v7a"
            "amd64", "x86_64" -> if (is64Bit) "x86_64" else "x86"
            "x86", "i386", "i686" -> if (!is64Bit) "x86" else null
            "arm", "armv7l", "armv8l", "armeabi-v7a" -> if (!is64Bit) "armeabi-v7a" else null
            else -> null
        }
    }
}
