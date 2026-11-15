package me.rerere.zeroagent.data.root

/** A failing user command is different from a revoked superuser grant. */
internal object RootCommandProtocol {
    private const val MARKER = "__ZEROAGENT_ROOT_VERIFIED__"
    fun wrap(command: String): String =
        "if [ \"\$(id -u)\" != 0 ]; then exit 126; fi\nprintf '$MARKER\\n'\n$command"
    fun isVerified(output: String): Boolean = output.lineSequence().any { it.trimEnd('\r') == MARKER }
    fun cleanOutput(output: String): String = output.lineSequence()
        .filterNot { it.trimEnd('\r') == MARKER }.joinToString("\n")
}
