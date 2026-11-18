package me.rerere.zeroagent.data.terminal

import java.io.File

/** The interactive terminal and Agent command tool share Android's shell and private home. */
internal object AndroidShellEnvironment {
    const val SHELL = "/system/bin/sh"
    const val SESSION_KEY = "android-shell"
    const val PATH = "/system/bin:/system/xbin:/vendor/bin:/product/bin:/apex/com.android.runtime/bin"

    // The bundled PTY bridge adds argv[0] itself. Passing a second shell name opens a script named sh.
    fun interactiveArguments(): Array<String> = arrayOf("-i")

    fun environment(home: File, temporary: File): Map<String, String> = mapOf(
        "HOME" to home.absolutePath,
        "TMPDIR" to temporary.absolutePath,
        "PATH" to PATH,
        "TERM" to "xterm-256color",
        "LANG" to "C.UTF-8",
        "SHELL" to SHELL,
        "USER" to "ZeroAgent",
        "LOGNAME" to "ZeroAgent",
        "PS1" to "ZeroAgent $ ",
    )

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
