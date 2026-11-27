# Android UI／UX 驗收紀錄

此為首輪驗收紀錄，包含當時尚未修復的測試與 Lint 結果。最新整合、修復及驗證請見 [v1.1.1 驗收紀錄](V1.1.1-VERIFICATION.md)；[v1.0.9 紀錄](V1.0.9-VERIFICATION.md) 為中間版本歷史證據。

此紀錄對應 `codex/android-ui-polish`。驗證使用獨立套件 `app.zeroagent.debug`、API 35 x86_64 模擬器 `ZeroAgent_API35`，未操作正式套件或發布版本。

## 已完成的介面變更

- 保留品牌、Manrope 與主題選項，統一語意底色、字級行高、圓角、分隔線、48dp 操作區和設定列。
- 輸入框直接顯示模型、推理與權限；附件、辨識、通話、送出常駐。停止與下一則草稿可同時操作，保留長按送出與原佇列流程。
- 推理滑桿拖曳即時預覽，放開才提交，依實際能力呈現選項。
- 首頁快捷草稿自適應換行、真實任務摘要保留載入／空白／錯誤分支；側欄分區排列，840dp 起常駐。
- 設定六組分類與搜尋共用目的頁清單，路由保持相容；原生內容區限制閱讀寬度。
- 共用表單、大字體控制列、收藏／歷史空白狀態、思考文字與原生語音版面同步調整。
- 新增與調整文案涵蓋繁中、簡中、英文、日文、韓文、俄文、阿拉伯文。

## 建置環境

專案使用 JDK 21。系統 Android Studio JBR 是 25，因此改用暫存目錄的 portable Microsoft JDK 21.0.12；SDK、NDK 28.2 與固定版本原生子模組完成建置。Windows 的 `pnpm` 啟動改經 `cmd /c`，修正既有 Gradle 網頁相依安裝步驟；網頁產品介面未變更。

## 自動驗證

| 項目 | 結果與證據 |
| --- | --- |
| 原始 Debug 建置 | 成功，`build/ui-polish/baseline/build.log` |
| Compose 回歸 | 最終 APK 13 案例全部通過；涵蓋 272dp 可用寬、200% 字體、48dp 範圍不重疊、停止與草稿並存、長按送出、模型／推理回呼、拖曳提交時機，以及實際設定搜尋頁的別名、原路由和無結果狀態；`final-core-tests.log` |
| 設定搜尋 JVM 回歸 | 11 案例全部通過，最終建置已重跑 |
| 完整 JVM 測試 | 共 3263 案例，3192 通過、71 失敗；詳見下方限制 |
| Debug APK／測試 APK | ARM64、x86_64 皆建置成功並核對 Debug 簽章；x86_64 與測試 APK 安裝於 API 35 模擬器，ARM64 未進行真機安裝 |
| Lint | 完整重跑完成：1219 errors、807 warnings、10 hints，未通過；`build/ui-polish/lint-full.html` 與 `.xml` |
| 全設定目的頁巡覽 | 一般字體 40 頁與 320dp／200% 字體 40 頁全部通過；確認正確標題、查詢更新、捲動到目標、完整返回搜尋、單一新增操作與日期標籤幾何；`catalog-realtime-tests.log`、`catalog-large-font-tests.log` |

完整 JVM 測試中的 71 個失敗來自未修改的測試／工具程式：ArchiveTools 10、FileBatchTools 12、FileManager 34、PathSafetyGuard 9、TranscribeAudio validation 4、Shizuku runner 1、local-llm ModelInstall 1。多數涉及 Windows 路徑插入 JSON 未跳脫或 POSIX 路徑預期，Shizuku 個案為空值。沒有以修改工具授權／檔案防護行為來消除測試失敗。日誌：`verification-full.log`、`verification-artifacts.log`。這是本機未全綠的實際結果，尚未在 Linux 或實體裝置重新確認。

初次 CardGroup 裝置測試失敗已定位為 Material ListItem 合併語意樹造成子 testTag 無法查詢；改用 unmerged tree，保留可見性與幾何斷言，兩案例通過。座標、語意樹與截圖位於 `build/ui-polish/cardgroup-evidence`。

Lint 的錯誤分為 MissingTranslation 1110、LocalContextGetResourceValueCall 100、MissingPermission 3、NonObservableLocale 2、ContextCastToActivity 2、StringFormatMatches 2。本次將設定搜尋改為隨組態更新的資源讀取，消除該頁四個錯誤；沒有關閉檢查或建立遮蔽用 baseline。本次調整的 24 個基礎字串在七種語言都具備資源，沒有重複鍵（`localization-audit.json`）。完整 Lint 報告產生後，最後追加供應商卡片、子代理空白頁、統計日期選項與工作流程／排程空白提示的版面微調；已重新 Debug 建置與裝置回歸，報告未宣稱涵蓋這些最後微調後的另一次完整分析。

## 裝置矩陣與畫面

每張截圖附同名 XML 與 JSON，記錄實際尺寸、密度、字體。`scripts/android_ui_matrix.py` 可重現首頁矩陣，僅允許 emulator 序號，結束會還原系統尺寸與字體。

首頁版面修正後已完成 15 組尺寸／字體矩陣：320、360、412dp 各含 100%、130%、200%；840、1200dp 各含 100%、200%；840×412dp 橫向，以及 360×480dp 短視窗搭配 200%。840dp 直向確認出現常駐側欄；短視窗與大字體保留獨立捲動區與輸入控制。重拍後的畫面覆寫於 `matrix-*`。其後的供應商／子代理微調不涉及首頁，另以最終 APK 的整頁巡覽檢查。

另以實際 Debug UI 切換英文、阿拉伯文 RTL、深色、動態色、液態玻璃與漸層，並檢查不透明模式；360dp／130% 字體輸入長草稿，確認鍵盤開啟仍可編輯及操作控制列。發現的英文 3＋1 快捷入口與短視窗只露出品牌圖示問題已修正，語言／短視窗與最終 APK 鍵盤畫面已更新。這些是代表組合驗證，並非所有頁面與所有主題／語言的笛卡兒積。

最終整頁巡覽的 80 張截圖分別附有 `verified-titles.tsv`。圖像保存前等待像素穩定，早期過場截圖不納入交付。系統 UIAutomation 與 Compose 測試分開執行；連續巡覽前先 `adb shell am force-stop app.zeroagent.debug`，避免前一輪測試服務殘留，不同測試程序不共用模擬器控制權。

## 交付與範圍審查

`release-artifacts/ui-polish/` 內含兩種架構 APK、SHA-256 清單、三組前後對照、80 張目的頁畫面與標題清單、尺寸矩陣、可瀏覽的 `gallery.html` 及完整驗證報告。建置／裝置日誌與 Lint 報告放在 `verification/`。模擬器顯示尺寸、密度、字體與動畫設定已還原，本次專用模擬器已關閉。

變更審查確認：UI 沿用既有 ViewModel、資料模型、導航與工具授權規則；沒有新增相依套件、資料庫遷移或備份格式變更。建置基礎僅補上 Windows 的 pnpm 啟動方式。新互動以既有回呼組合，列表保持惰性捲動，程式碼與表格仍採局部橫向捲動。`git diff --check` 通過。

## 驗證界線

沒有連線真機與供應商憑證；未宣稱驗證真機麥克風品質、藍牙／聽筒切換、付費模型串流、真實核准後工具執行、模型下載完成或遠端服務失敗。短視窗以模擬器尺寸重設重現，尚未等同實體裝置的多視窗／分割畫面生命週期驗證。這些流程沿用原有 ViewModel／服務，元件狀態及回呼回歸與實際裝置連線驗證分別記錄。未發布、推送或使用正式簽章。
