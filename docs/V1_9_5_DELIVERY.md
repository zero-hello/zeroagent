# ZeroAgent v1.9.5 交付與驗收

版本：1.9.5／versionCode 28／minSdk 31。App 與 AI 模組的焦點單元測試均已重跑；未完成的裝置及真實服務驗收不列為已通過。

## 建置與測試結果

- Release App 編譯、雙 ABI APK 簽署封裝成功；本輪封裝耗時 7 分 48 秒；`lintVitalRelease` 通過。
- App 聚焦單元測試 126 項通過，18 個報告，失敗、錯誤及略過皆為 0；此結果只涵蓋交付列出的聚焦測試，不代表全部 App 測試。
- AI 模組聚焦單元測試 415 項通過，56 個報告，失敗、錯誤及略過皆為 0。
- 隔離建置副本包含 `document/consumer-rules.pro` 與 `app/compose_compiler_config.conf`；編譯、測試、lint 與封裝使用完整的 2,283 個建置輸入。
- APK 確認套件 `app.zeroagent`、版本 1.9.5／28、minSdk 31、不可偵錯、單一目標 ABI、ZIP CRC、既有正式簽章、必要 DEX 類別及終端授權資源。
- 兩個 APK 分別為 77,751,316 與 80,432,233 bytes。簽章憑證 SHA-256：`74d1066485313400f3e429f949d6d911bb7860fb052673b1447df8fc01c2c026`。
- 最新主程式來源指紋：`000d873f0d72b3991343b110663217527273e22073981731cb02466f4fd44dc9`；正式來源與獨立建置副本的 2,283 個建置輸入相符。
- code28 APK SHA-256：arm64-v8a `f552188f104c376eea3af2745e98329825ecbf9d704e02d361f3ca54a863f887`；x86_64 `d2b2f8c2e352c4323ce16381f09e05cafdd8e8a18dfdf49932a86010332e30e8`。
- code28 來源提交 `2625eb4872115800c62afcfaa64f03ad15dd913f` 已推送至 `main`，`v1.9.5` 標籤指向該提交；Release ID `444049371` 八個附件均已同步，GitHub 回傳大小與 SHA-256 已核對相符。
- APK 尚未安裝，實體裝置也未連接。已使用 E 槽隔離 AVD、Windows Emulator 37.3.9、獨立 ADB 連接埠與 TCG／SwiftShader 嘗試啟動；Emulator 載入系統映像及圖形後端後，在 Android 開機完成前退出，ADB 未列出裝置，因此模擬器／實體裝置測試仍未完成。

## 18 項需求

| # | 修改內容 | 完成狀態 | 驗證結果與剩餘事項 |
|---|---|---|---|
| 1 | 輸入區燈泡選單依供應商與模型能力提供推理程度；模型切換時檢查並保存各自選項。 | 程式完成；外部驗收待補 | AI 能力、請求映射與 App 編譯已驗證；未知能力不提供虛構選項。尚未用真實模型端點確認所有供應商設定，燈泡讀屏與操作需裝置驗收。 |
| 2 | 首頁四個快捷操作採一致的 Material 3 Expressive 色調按鈕與響應式欄數。 | 程式完成；畫面驗收待補 | 共用按鈕及 4／2／1 欄切換邏輯已實作並有測試；小螢幕、橫向、大字、鍵盤焦點及實際點擊狀態未經裝置檢查。 |
| 3 | 移除設定中的子代理設定檔入口、頁面及路由，保留其他 Agent 功能。 | 已完成 | 對應設定頁與路由已清理；Release APK DEX 檢查確認原入口類別不存在。 |
| 4 | 工具授權頁加入全選／取消全選、三態呈現及持久化。 | 程式完成；系統層驗收待補 | 僅修改可調整的 App 核准規則，不覆寫 Android／外部權限；保存邏輯與聚焦測試通過。尚未在裝置重啟後檢查完整流程。 |
| 5 | 對話內圖片生成依實際供應商與模型能力呼叫，提供預覽、儲存及狀態。 | 程式完成；真實服務驗收待補 | 能力判斷、請求及回傳處理已實作，保留對話生成流程；未使用付費帳戶實際生成，額度及各廠商回傳差異未用真實服務驗證。 |
| 6 | 移除設定內翻譯、圖片生成、影片生成獨立入口，保留對話圖片能力。 | 已完成 | 設定入口及相關失效路由已清理；對話內圖片能力仍保留。 |
| 7 | 提高清單與選單分隔線的可見度及一致性。 | 程式完成；視覺驗收待補 | 共用分隔線元件及縮排已套用主要設定、選單與側欄；深淺色對比及各頁視覺密度未經裝置檢查。 |
| 8 | 全專案採用 Compose／Material 3 Expressive 的形狀、色彩、排版與互動規則。 | 實作完成；裝置驗收待補 | 共用主題與元件已整理，主要玻璃材質與重複選項已移除；深淺色、各螢幕方向、大字、讀屏、減少動態與焦點仍待裝置驗收。 |
| 9 | 移除產品 Web 伺服器及其入口、服務、通知與設定，保留 API 網路功能。 | 已完成 | Web 模組、服務、入口及專用設定已清理；APK Manifest／DEX 靜態檢查未發現舊 Web 服務，API 與 OAuth 本機回呼保留。 |
| 10 | 同步版本資訊、README、變更、進度、交接及功能說明。 | 已完成 | Gradle、README、變更紀錄、進度、交接及功能說明同步至 1.9.5／28；各項驗證結果與限制如實記錄。 |
| 11 | 持久化存取模式；使用者已授權的 Root 操作不再重複 App 內確認，並可撤銷／重查。 | 程式完成；Root 裝置驗收待補 | 偏好保存、授權有效性檢查、取消與撤銷流程有程式及單元測試依據；未在 Root 裝置與外部管理器實際驗收，不繞過 Android 或外部授權。 |
| 12 | 無障礙頁分離說明內容與操作元件，支援長文字及明確狀態。 | 程式完成；無障礙驗收待補 | 共用卡片排版與操作狀態已實作；TalkBack、觸控範圍、橫向和大字實際呈現未經裝置檢查。 |
| 13 | 加入 DeepSeek 與 Cohere 的正式協定、模型、串流、工具及錯誤處理。 | 程式完成；真實帳戶驗收待補 | Cohere 原生 v2 傳輸及 DeepSeek 設定已整合；模擬請求／串流測試通過。未使用真實金鑰驗證帳戶、額度及上線模型能力。 |
| 14 | 只在 Pixel 8 系列及更新 Pixel 顯示 AICore，且另檢查服務與模型狀態。 | 程式完成；Pixel 驗收待補 | 廠牌／完整型號分類與服務就緒判斷有 App 測試；未在符合及不符合條件的實體 Pixel 裝置驗證。 |
| 15 | 自訂供應商以三種 API 協定格式設定，分組輸入欄位、預覽端點並保留舊設定。 | 程式完成；真實端點驗收待補 | Chat Completions、OpenAI Responses、Anthropic Messages 的請求解析及端點測試通過；未連線真實自訂端點或走完裝置上的遷移流程。 |
| 16 | Gemini 安全設定一致帶入請求、保存選擇並顯示阻擋原因。 | 程式完成；模型端點驗收待補 | REST／串流安全參數與阻擋回應邏輯有測試；OAuth、Live、圖片及真實模型的安全回應未完整實測，供應商固定限制仍適用。 |
| 17 | 對話頁提供可輸入、輸出、取消、工作目錄及共用權限的內建終端機。 | 原生建置完成；終端實機驗收待補 | PRoot 與 loader 以專案固定來源及 NDK r28c 重建 arm64-v8a、x86_64，並核對 APK 內雜湊；PTY／rootfs 安裝、手動命令、Agent 執行、中止及 Root 行為未在 Android 裝置執行。此為 App 內 PRoot Linux 工作區，不是完整 Termux 發行版。 |
| 18 | 統一側欄與主要頁面元件，完成錯誤、空白、載入、停用及跨尺寸互動檢查。 | 實作完成；裝置驗收待補 | 設定與聊天採共用側欄狀態及共用元件，程式編譯與 APK 靜態檢查通過；側欄 crash、深淺色、鍵盤遮擋、橫向與大字仍待裝置實測。 |

## 主要修改範圍

- `app/src/main/java/me/rerere/zeroagent/RouteActivity.kt`、`ui/pages/chat/AppDrawerHost.kt`、`ui/pages/chat/ChatPage.kt`：全域側欄與聊天導覽。
- `ui/pages/setting/`、`ui/components/ui/`：設定入口、工具授權、供應商編輯、無障礙版型與共用 Compose 元件。
- `ai/src/main/java/me/rerere/ai/provider/`：推理能力、圖片能力、DeepSeek／Cohere、自訂 API 協定及 Gemini 安全處理。
- `app/src/main/java/me/rerere/zeroagent/ui/pages/extensions/workspace/`、`workspace/`、`scripts/build-proot.py`：內建終端機與 PRoot 建置。
- `workspace/native/proot/SOURCES.json`、`docs/licenses/EMBEDDED_TERMINAL.md`：固定來源、授權、建置方法及實測界線。
- `app/build.gradle.kts`、`README.md`、`CHANGELOG.md`、`PROGRESS.md`、`ZEROAGENT_HANDOFF.md`：版本、建置與交接資訊。

## 已移除的功能與入口

- 獨立系統語音助理與背景喚醒功能；一般原生即時通話及 TTS 保留。
- 子代理設定檔編輯入口；既有 Agent 執行功能保留。
- 設定內翻譯、圖片生成、影片生成獨立入口；對話圖片生成保留。
- 產品 Web 伺服器、啟停入口、服務、通知及專用設定；AI API 網路與 OAuth 本機回呼保留。
- 高級材質重複選項及主要 Liquid Glass／毛玻璃介面效果。

## 安裝與驗收注意事項

- 正式 APK 分為 arm64-v8a 與 x86_64；下載後請核對 `SHA256SUMS`。APK 必須使用既有相容簽章才能直接升級。
- 新增供應商須由使用者自行設定金鑰；本輪未用真實金鑰或付費帳戶測試。
- Root 仍要求裝置已具備 Root，Android 與外部 Root 管理器可自行拒絕或撤銷授權。
- 內建終端首次使用需在 App 內取得並準備 rootfs；目前未完成裝置端 PTY 與 PRoot 執行驗收。
- AICore 需符合 Pixel 型號，且服務與模型已實際就緒；單元測試不能取代 Pixel 實機驗收。
- 深淺色、小螢幕、橫向、大字、讀屏、鍵盤遮擋與側欄 crash 回歸尚未裝置驗收。原 SDK 內 ADB／Emulator 是 Linux ELF，既有 AVD 也指向 Linux 路徑 `/run/media/zero/ZEROAGENT/...`；另以 E 槽隔離 AVD 及 Windows Emulator 實際嘗試，系統映像和 SwiftShader 可載入，但 Emulator 在 Android 開機完成前退出，ADB 未列出裝置。

APK、SHA-256、Manifest、簽章與建置／測試原始證據位於 `deliverables/v1.9.5`。本報告不把編譯或靜態檢查等同裝置端功能驗收。
