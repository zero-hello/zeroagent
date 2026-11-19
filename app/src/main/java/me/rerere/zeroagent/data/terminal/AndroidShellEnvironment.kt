package me.rerere.zeroagent.data.terminal

import java.io.File

/** Shared Android shell environment; no external app or downloaded executable required. */
internal object AndroidShellEnvironment {
    const val SHELL = "/system/bin/sh"
    const val SESSION_KEY = "android-shell"
    const val PATH = "/system/bin:/system/xbin:/vendor/bin:/product/bin:/apex/com.android.runtime/bin:/sbin:/su/bin:/debug_ramdisk"
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    fun interactiveArguments(): Array<String> = arrayOf("-i")

    fun environment(home: File, temporary: File): Map<String, String> = mapOf(
        "HOME" to home.absolutePath, "TMPDIR" to temporary.absolutePath, "PATH" to PATH,
        "ENV" to File(home, ".zeroagent-shellrc").absolutePath,
        "TERM" to "xterm-256color", "COLORTERM" to "truecolor", "LANG" to "C.UTF-8",
        "SHELL" to SHELL, "USER" to "ZeroAgent", "LOGNAME" to "ZeroAgent", "PS1" to "$ ",
    )

    /** mksh reads ENV after its own startup; setting only PS1 in the environment is insufficient. */
    fun prepare(home: File) {
        check(home.isDirectory || home.mkdirs()) { "Cannot prepare terminal home." }
        File(home, ".tmp").mkdirs()
        val rc = File(home, ".zeroagent-shellrc")
        val content = """
            export USER=ZeroAgent LOGNAME=ZeroAgent
            if [ "${'$'}(id -u)" = 0 ]; then PS1='# '; else PS1='${'$'} '; fi
            export PS1
            alias ll='ls -al'
            alias la='ls -a'
            alias cls='clear'
            sysinfo() {
                printf '\033[1;36mZeroAgent · Android\033[0m\n'
                printf 'Device: %s %s\n' "${'$'}(getprop ro.product.manufacturer)" "${'$'}(getprop ro.product.model)"
                printf 'Android: %s (API %s)\n' "${'$'}(getprop ro.build.version.release)" "${'$'}(getprop ro.build.version.sdk)"
                printf 'Kernel: '; uname -srmo
                printf 'Identity: '; id
                printf 'Memory: '; head -n 1 /proc/meminfo
                printf 'Uptime: '; uptime
                printf 'Storage:\n'; df -h "${'$'}HOME"
            }
            terminal_help() {
                printf 'Android shell: ls cp mv mkdir rm cat grep sed awk find curl(if installed)\n'
                printf 'Tools: toybox --help | sysinfo | ll | la | cls | terminal_help\n'
                printf 'Use Ctrl-C to interrupt; pinch or A-/A+ to resize text.\n'
            }
        """.trimIndent() + "\n"
        if (!rc.isFile || rc.readText() != content) rc.writeText(content)
    }

    fun command(home: File, directory: File, text: String): String =
        "export " + environment(home, File(home, ".tmp")).entries.joinToString(" ") { (k,v) -> "$k=${quote(v)}" } +
            "; cd ${quote(directory.absolutePath)} || exit; . ${quote(File(home, ".zeroagent-shellrc").absolutePath)};\n" + text

    fun rootInteractiveCommand(home: File): String = command(home, home,
        "if [ \"\$(id -u)\" != 0 ]; then printf 'Root grant unavailable\\n' >&2; exit 126; fi; exec $SHELL -i")

    fun workingDirectory(home: File, path: String): File {
        val canonicalHome = home.canonicalFile
        val directory = File(canonicalHome, path).canonicalFile
        require(directory == canonicalHome || directory.path.startsWith(canonicalHome.path + File.separator)) {
            "working_dir must stay inside the terminal home."
        }
        require(directory.isDirectory) { "working_dir does not exist or is not a directory." }
        return directory
    }
}
