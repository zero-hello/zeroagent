package me.rerere.zeroagent.data.ai.tools

private val changesDataOrSharesContent = setOf(
    "set_chat_theme", "write_binary_file", "delete_file", "move_file", "copy_file",
    "create_directory", "batch_copy", "batch_move", "batch_delete", "zip_files", "unzip_file",
    "create_calendar_event", "create_contact", "send_email_intent", "send_sms_intent", "send_sms",
    "generate_image", "generate_video", "run_root_command",
    "memory_tool", "memory_write",
)

internal fun requiresExplicitSideEffectConfirmation(toolName: String): Boolean =
    toolName in changesDataOrSharesContent || toolName in ToolApprovalDefaults.DANGEROUS_TOOLS ||
        ToolApprovalDefaults.isHardFloor(toolName)
