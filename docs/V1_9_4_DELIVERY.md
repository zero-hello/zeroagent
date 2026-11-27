# ZeroAgent v1.9.4 逐項交付報告

版本：1.9.4／versionCode 24／minSdk 31。以下區分程式實作、已執行驗證與尚需裝置或帳戶確認的部分。

## 建置與驗證證據

- 主程式 Release Kotlin 與 Java 實際編譯通過；後續分階段打包只重用相同來源的編譯結果。
- App 單元測試程式編譯成功：compileReleaseUnitTestKotlin，166 個任務，54 執行、112 為最新狀態。
- AI 模組：405 項單元測試通過，無失敗、錯誤或跳過。
- App 聚焦測試：111 項通過，14 組，0 失敗、0 錯誤、跳過 0 項；這不是全部 App 測試。
- 2026-11-12 新建 arm64-v8a 與 x86_64 正式 APK，版本 1.9.4／24、minSdk 31、不可偵錯、既有簽章、ABI、ZIP CRC、授權資源及本輪 DEX 類別均通過驗證。
- 私人倉庫 main 與 v1.9.4 tag 指向同一對應來源；Release 已重新讀取並核對兩個 APK、校驗檔與報告的大小及 SHA-256。
- 正式 E 槽來源與 C 槽建置副本的 2189 個建置輸入已比對；文字僅正規化換行。主程式指紋固定，變更主程式時不得重用原編譯結果。
- 本輪未執行 lintVitalAnalyzeRelease、lintVitalReportRelease、lintVitalRelease。
- 尚未執行裝置 UI、Root、AICore、PRoot PTY、音訊或付費供應商實帳戶測試。

## 18 項需求

| # | 修改內容 | 完成狀態 | 實際驗證與剩餘事項 |
|---|---|---|---|
| 1 | 輸入區使用燈泡；依供應商／模型能力顯示推理程度，未知能力不編造；使用模型自己的保存值，送出前正規化 | 部分完成 | AI 能力與請求測試通過；尚需裝置確認模型切換、燈泡狀態、讀屏與真實端點 |
| 2 | 首頁四個快捷操作共用 M3 FilledTonalButton，依寬度與字級切為 4／2／1 欄 | 部分完成 | App 換欄邏輯測試通過；小螢幕、橫向、鍵盤焦點需裝置驗收 |
| 3 | 刪除子代理設定檔編輯頁、設定索引、路由及無效捷徑，保留 Agent 執行核心 | 已完成 | 來源入口已清理、App 編譯通過；裝置導航尚未驗收 |
| 4 | 工具授權規則新增全選、取消全選與三態計數，透過既有 DataStore 保存，只處理可調整項 | 部分完成 | 不修改 Android 或外部權限；頁面選取代表既有「需手動核准」規則。重新啟動後 UI 狀態需裝置確認 |
| 5 | 對話圖片生成依協定與模型輸出能力守門，串接既有圖片 API、預覽及匯出；改善參數、額度及空結果錯誤 | 部分完成 | 能力、URL 與圖片請求測試通過；未使用付費帳戶生成或實機儲存圖片 |
| 6 | 移除設定中的翻譯、圖片生成、影片生成獨立入口及路由，保留對話相關功能 | 已完成 | 來源索引與路由已清理；未重新建立相同設定入口 |
| 7 | 共用 1dp outline 分隔線與一致縮排，套用主要設定、選單、側欄 | 部分完成 | 共用來源已實作；明暗主題與各畫面的視覺對比尚需裝置確認 |
| 8 | 沿用 MaterialExpressiveTheme、官方 M3 元件、語意色彩與 28dp 主要圓角；移除玻璃材質與高級材質開關 | 部分完成 | 來源已清理；動畫減量、焦點、讀屏及所有尺寸尚未完整實機驗收 |
| 9 | 移除產品 Web 模組、服務、通知、連接埠設定、入口及專用依賴；保留 API 網路及 OAuth 回呼 | 已完成 | 已清理來源與 Manifest，ChatService 殘留引用已修正，主程式編譯通過 |
| 10 | README、當前變更、來源聲明、語音與文件索引同步 1.9.4，交接文件改為目前狀態 | 已完成 | 版本設定已確認 1.9.4／24／31；新 APK 與私人 Release 附件均已核對 |
| 11 | 私有持久化保存每對話及新對話預設存取模式；Root 有效且已授權時略過 App 重複確認，保留撤銷、uid 0 重查及背景限制 | 部分完成 | 新增保存失敗、Root 命令協定與授權測試；App 聚焦測試通過。外部 Root 管理工具與真實撤銷需實機 |
| 12 | 共用 SettingsActionCard 分開說明與操作區，保留足夠點擊範圍、狀態與忙碌提示 | 部分完成 | 來源已實作；長文字、TalkBack 與大字體需裝置確認 |
| 13 | 新增 DeepSeek 及 Cohere 正式設定檔；Cohere 使用原生 v2 對話、模型查詢、串流、工具及錯誤處理 | 部分完成 | AI 模擬回應／串流測試通過；未使用真實金鑰驗證帳戶、額度與實際模型 |
| 14 | Google 製造商與完整 Pixel 型號判斷，只接受第 8 代以上系列，符合後仍檢查 AICore 服務及模型狀態 | 部分完成 | 型號判斷 App 測試通過；未在符合或不符合的真實手機驗證服務 |
| 15 | 自訂供應商改為三種實際 API 格式，分組欄位、完整端點預覽與共用 URL 解析；保留網址、金鑰、模型及舊格式相容 | 部分完成 | AI endpoint 測試通過；設定遷移 App 測試通過，裝置操作仍待驗收 |
| 16 | Gemini 共用安全參數與安全阻擋解析，REST、串流、圖片及 Code Assist 顯示可理解的拒絕原因 | 部分完成 | AI 安全設定與阻擋回應測試通過；OAuth、Live 與真實模型端點仍需帳戶／裝置驗證 |
| 17 | 對話頂欄開啟真正 TerminalView／PRoot Linux 環境，支援手動命令、Agent 工作區、即時輸出、Ctrl+C、工作目錄與共享 Root 授權 | 部分完成 | 不要求外部 Termux App；舊外部 transport 已移除。App 相容與執行邊界測試通過；實機 PTY／rootfs 安裝未驗收；原生檔案的完整對應建置來源仍缺證據，見授權文件 |
| 18 | 設定與聊天共用唯一側欄狀態，整理側欄底部操作；共用按鈕、分隔線、開關與設定卡片 | 部分完成 | 來源已整合、App 編譯通過；側欄 crash 回歸、深淺色、小螢幕、橫向、大字及鍵盤遮擋需裝置驗收 |

## 主要修改檔案與用途

- `app/src/main/java/me/rerere/zeroagent/RouteActivity.kt`、`ui/pages/chat/AppDrawerHost.kt`、`ui/pages/chat/ChatPage.kt`：統一側欄擁有者與導航。
- `app/src/main/java/me/rerere/zeroagent/ui/pages/setting/SettingToolApprovalsPage.kt`：批次工具核准規則。
- `app/src/main/java/me/rerere/zeroagent/ui/pages/setting/components/ProviderApiFormatSection.kt` 與設定頁共用元件：API 格式、網址預覽與分組操作。
- `ai/src/main/java/me/rerere/ai/core/ReasoningCapabilities.kt`、`provider/providers/openai/DeepSeekCapabilities.kt`：實際推理能力守門。
- `ai/src/main/java/me/rerere/ai/provider/providers/cohere/`：原生 Cohere 請求、串流、模型與工具解析。
- `ai/src/main/java/me/rerere/ai/provider/ProviderEndpoint.kt`、`ImageCapabilities.kt`：端點與圖片能力共用邏輯。
- `ai/src/main/java/me/rerere/ai/provider/providers/google/GoogleSafetyResponse.kt`：共用安全阻擋提示。
- App 存取權持久化、RootAccessManager、RootCommandProtocol 與終端機頁：保存授權、撤銷重查與真實執行。
- `docs/licenses/EMBEDDED_TERMINAL.md`、`docs/licenses/proot-reference/`、APK 授權資源：終端元件來源、授權與證據缺口。
- `app/build.gradle.kts`、`README.md`、`PROGRESS.md`、`ZEROAGENT_HANDOFF.md`：版本、建置與目前進度。

## 已移除的功能與入口

- 高級材質、Liquid Glass／毛玻璃及重複材質選項。
- 設定頁獨立側欄，改用全域側欄；錯誤畫面的目前助理與切換助理。
- 獨立語音助理、Hey Zero 背景喚醒、喚醒範本及相關資源。一般通話與 TTS 保留。
- 子代理設定檔編輯入口、失效路由與捷徑。Agent 執行核心保留。
- 設定中的翻譯／圖片生成／影片生成獨立入口。
- 產品 Web 伺服器、啟停設定、服務、通知、連接埠與產品前端。
- 外部 Termux App 安裝、權限與傳輸入口；舊工具名稱只保留工作區相容處理。

## 升級與剩餘條件

- 既有供應商網址、金鑰及模型不清空；新供應商需自行填入有效金鑰。舊自訂 Gemini 設定保留原協定。
- Root 需裝置本來就具備 Root，並由 Android 或外部管理工具授權；App 不繞過系統限制。
- 終端機首次使用需下載並準備 rootfs，環境相容性需實機驗證；它是 PRoot Linux 環境，不是完整官方 Termux 發行版。
- AICore 需要符合型號的 Pixel、可用服務與模型；不符合裝置不顯示供應商。
- 目前 SDK 的 emulator／adb 是 Linux 執行檔，沒有可用 Windows 執行檔；尚未在本輪啟動既有 AVD。
- 要完成完整驗收，仍需可用 Windows 裝置環境、符合型號的 Pixel、Root 裝置及各供應商測試帳戶；不得把這些缺口標示為已通過。

## APK 校驗

| APK | SHA-256 |
|---|---|
| ZeroAgent-v1.9.4-arm64-v8a.apk | 82f64b43e4f6c0886b6414a382bd20b743902e9612b947de2105e9aa445fe6f4 |
| ZeroAgent-v1.9.4-x86_64.apk | e9d2474f08c97560010a931b4945fdde68d1739e151bbc5bad35b946f43a26ca |

完整 metadata、簽章與資源檢查見附件 APK_VERIFICATION.json；下載需私人倉庫存取權。

TEST_SUMMARY.json 提供測試數量，TEST_RESULTS.zip 保存 JUnit 原始結果，BUILD_EVIDENCE.zip 保存分階段建置紀錄與來源比對證據。

發布位置：https://github.com/zero-hello/zeroagent-private/releases/tag/v1.9.4
