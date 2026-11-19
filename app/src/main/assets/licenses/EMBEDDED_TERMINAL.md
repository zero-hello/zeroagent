# 內建終端機：來源與授權

- 畫面與 PTY：Termux terminal-view／terminal-emulator 0.118.0，以 AndroidView 嵌入 Compose。上游 https://github.com/termux/termux-app/tree/v0.118.0 。GPL-3.0-only，部分程式碼的 Apache-2.0 原始聲明依各檔標頭保留。授權副本見 TERMUX-LICENSE.md。
- 執行環境：裝置 Android /system/bin/sh 與系統已安裝的 toybox 等工具。不要求外部 Termux、Linux rootfs 或套件管理器。工具是否存在取決於裝置，不承諾 apt／pip／curl 可用。
- sysinfo、terminal_help 與快捷別名為本專案 Shell 函式，直接讀取 getprop、uname、id、/proc/meminfo、uptime、df 等實際結果；不是 neofetch 或 fastfetch。
- 一般終端與 Agent 指令共用私有 HOME、PATH、暫存目錄及啟動設定；舊 termux_run_command 名稱只供既有工具相容。
- Root 使用已保存的 App 授權偏好、實際 su 檢查與外部管理工具授權。新的互動終端與前景 Agent 指令可使用 Root；#／$ 區分真實權限，沒有 PRoot 假 root。停用 Root 時結束受管理的 Root 工作階段，執行前仍檢查授權。這不代表能控制使用者自行啟動或脫離工作階段的外部程序。
- 歷史 PRoot 授權、來源證據與未解決的對應建置來源問題保留在 docs/licenses/proot-reference；保留授權文件不代表目前互動終端使用 PRoot。

實機 PTY／輸入法／剪貼簿／Root 與外部 su 管理工具相容性，請以本版交付報告的實際驗收結果為準。
