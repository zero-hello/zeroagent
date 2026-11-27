# 內建終端環境：元件來源與授權

## 封裝內容

- 終端畫面：`com.termux.termux-app:terminal-view:0.118.0`，以 AndroidView 嵌入 Compose 頁面，提供真實 PTY、鍵盤輸入及即時輸出；沒有啟動外部 Termux App。
- 上游：[Termux v0.118.0](https://github.com/termux/termux-app/tree/v0.118.0)。該版本 LICENSE.md 指定 GPL-3.0-only；部分 Android Terminal Emulator 程式碼保留 Apache-2.0 例外，應依個別檔案標頭保留原聲明。原始聲明副本為 `TERMUX-LICENSE.md`。
- Linux 執行：`libproot_exec.so`、`libproot_loader.so` 與工作區 PRoot runner 提供 arm64-v8a、x86_64 原生檔案。v1.9.5 兩 ABI 原生檔由固定來源使用 Android NDK r28c 重建，APK 內 SHA-256 與建置證據相符。上游來源、依賴、修改與建置方式見 [`SOURCES.json`](../../workspace/native/proot/SOURCES.json) 及 `scripts/build-proot.py`。上游 COPYING（包含 GPL v2 條文）副本為 `PROOT-COPYING.txt`。
- Linux 根檔案系統：工作區既有下載流程使用 Ubuntu Base 24.04.3；並非內附整套 Termux bootstrap。使用者仍需在 App 內下載、準備環境，但不需另外安裝 Termux App。Ubuntu 套件各自的著作權聲明保存在 rootfs 的 `/usr/share/doc`。

## 權限與相容性

終端 PTY 與 AI workspace 工具共用工作區的 files、linux、tmp。PRoot 顯示的 root 是使用者空間模擬，不會賦予 Android Root，也不是安全隔離沙箱。真正的 Android Root 單次命令共用 App 的 Root 偏好與實際 su 驗證；外部 Root 管理工具的系統授權仍然適用。

既有外部 Termux 的私有資料無法由 App 跨 Android sandbox 自動讀取。舊 `termux_run_command` 工具 ID 改在呼叫助理綁定的內建工作區執行；外部私有路徑、互動模式與套件必須明確移轉。外部 tmux 及 Whisper CLI transport 已退役，不會自動下載或啟動外部 App；工作區背景工作與一般語音功能仍保留。

## 尚未驗證

本輪 APK 原生檔有固定來源清單、依賴版本、修改說明、建置指令、NDK 版本與每 ABI 的來源／產物雜湊。尚未在 Android 裝置驗證 PTY、PRoot、rootfs 首次安裝、不同 Android 版本及 Root 管理工具行為；建置證據不等同執行期驗收。歷史 APK 仍須依其各自報告判斷來源對應情形。
