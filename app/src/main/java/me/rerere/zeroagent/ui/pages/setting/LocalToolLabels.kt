package me.rerere.zeroagent.ui.pages.setting

import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.ai.tools.LocalToolOption

internal fun localToolTitleRes(option: LocalToolOption): Int = when (option) {
    LocalToolOption.JavascriptEngine -> R.string.assistant_page_local_tools_javascript_engine_title
    LocalToolOption.TimeInfo -> R.string.assistant_page_local_tools_time_info_title
    LocalToolOption.Clipboard -> R.string.assistant_page_local_tools_clipboard_title
    LocalToolOption.Tts -> R.string.assistant_page_local_tools_tts_title
    LocalToolOption.AskUser -> R.string.assistant_page_local_tools_ask_user_title
    LocalToolOption.Battery -> R.string.assistant_page_local_tools_battery_title
    LocalToolOption.UsageLedger -> R.string.assistant_page_local_tools_usage_ledger_title
    LocalToolOption.AudioInfo -> R.string.assistant_page_local_tools_audio_info_title
    LocalToolOption.TelephonyInfo -> R.string.assistant_page_local_tools_telephony_title
    LocalToolOption.WifiInfo -> R.string.assistant_page_local_tools_wifi_title
    LocalToolOption.Sensors -> R.string.assistant_page_local_tools_sensors_title
    LocalToolOption.DeviceDiagnostics -> R.string.assistant_page_local_tools_device_diagnostics_title
    LocalToolOption.StorageInfo -> R.string.assistant_page_local_tools_storage_title
    LocalToolOption.Toast -> R.string.assistant_page_local_tools_toast_title
    LocalToolOption.Notification -> R.string.assistant_page_local_tools_notification_title
    LocalToolOption.Share -> R.string.assistant_page_local_tools_share_title
    LocalToolOption.Torch -> R.string.assistant_page_local_tools_torch_title
    LocalToolOption.Vibrate -> R.string.assistant_page_local_tools_vibrate_title
    LocalToolOption.Brightness -> R.string.assistant_page_local_tools_brightness_title
    LocalToolOption.Volume -> R.string.assistant_page_local_tools_volume_title
    LocalToolOption.Location -> R.string.assistant_page_local_tools_location_title
    LocalToolOption.Contacts -> R.string.assistant_page_local_tools_contacts_title
    LocalToolOption.CallLog -> R.string.assistant_page_local_tools_call_log_title
    LocalToolOption.SmsInbox -> R.string.assistant_page_local_tools_sms_inbox_title
    LocalToolOption.CameraPhoto -> R.string.assistant_page_local_tools_camera_photo_title
    LocalToolOption.ImageGeneration -> R.string.assistant_page_local_tools_image_generation_title
    LocalToolOption.VideoGeneration -> R.string.assistant_page_local_tools_video_generation_title
    LocalToolOption.MicRecorder -> R.string.assistant_page_local_tools_mic_recorder_title
    LocalToolOption.SpeechToText -> R.string.assistant_page_local_tools_speech_to_text_title
    LocalToolOption.Fingerprint -> R.string.assistant_page_local_tools_fingerprint_title
    LocalToolOption.NotificationListener -> R.string.assistant_page_local_tools_notifications_title
    LocalToolOption.MediaPlayer -> R.string.assistant_page_local_tools_media_player_title
    LocalToolOption.MediaScanner -> R.string.assistant_page_local_tools_media_scanner_title
    LocalToolOption.Download -> R.string.assistant_page_local_tools_download_title
    LocalToolOption.CronJobs -> R.string.assistant_page_local_tools_cron_jobs_title
    LocalToolOption.Files -> R.string.assistant_page_local_tools_files_title
    LocalToolOption.Ssh -> R.string.assistant_page_local_tools_ssh_title
    LocalToolOption.Shizuku -> R.string.assistant_page_local_tools_shizuku_title
    LocalToolOption.McpControl -> R.string.assistant_page_local_tools_mcp_control_title
    LocalToolOption.ExternalAutomation -> R.string.assistant_page_local_tools_external_automation_title
    LocalToolOption.Reliability -> R.string.assistant_page_local_tools_reliability_title
    LocalToolOption.SubAgents -> R.string.assistant_page_local_tools_sub_agents_title
    LocalToolOption.CostGuards -> R.string.assistant_page_local_tools_cost_guards_title
    LocalToolOption.Workflows -> R.string.assistant_page_local_tools_workflows_title
    LocalToolOption.SkillImport -> R.string.assistant_page_local_tools_skill_import_title
    LocalToolOption.JsSkills -> R.string.assistant_page_local_tools_js_skills_title
    LocalToolOption.SystemIntents -> R.string.assistant_page_local_tools_system_intents_title
    LocalToolOption.Browser -> R.string.assistant_page_local_tools_browser_title
    LocalToolOption.SmsSend -> R.string.assistant_page_local_tools_sms_send_title
    LocalToolOption.Wallpaper -> R.string.assistant_page_local_tools_wallpaper_title
    LocalToolOption.Keystore -> R.string.assistant_page_local_tools_keystore_title
    LocalToolOption.Nfc -> R.string.assistant_page_local_tools_nfc_title
    LocalToolOption.ExternalStorage -> R.string.assistant_page_local_tools_external_storage_title
    LocalToolOption.Archive -> R.string.assistant_page_local_tools_archive_title
    LocalToolOption.AppLauncher -> R.string.assistant_page_local_tools_app_launcher_title
    LocalToolOption.Termux -> R.string.embedded_terminal_title
    LocalToolOption.ScreenAutomation -> R.string.assistant_page_local_tools_screen_automation_title
    LocalToolOption.KeyboardControl -> R.string.assistant_page_local_tools_keyboard_title
    LocalToolOption.ModelControl -> R.string.assistant_page_local_tools_model_control_title
}
