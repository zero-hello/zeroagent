# ZeroAgent v1.9.9 交付與驗收

版本 1.9.9／code 32／Android 12+，2026-11-19。

## 功能修改

| 項目 | 實際實作 | 驗證界線 |
| --- | --- | --- |
| 記憶入口 | 無效／已清除模型退回原有記憶範圍，獨立 ViewModel key | Kotlin 編譯通過；路由實機待驗收 |
| 目前供應商生成 | 頁面、工具、選擇器共用 ImageModelResolver；不跨帳戶隱性扣額；多候選需選擇 | 隔離、停用、刪除、模糊選擇等回歸已通過；實帳戶待驗收 |
| 終端機 | 真正 PTY、黑底、分頁、字體按鈕／雙指縮放與保存；簡潔 $/# 提示符；toybox 與 sysinfo | Android Shell 環境回歸；實機 PTY／鍵盤／縮放待驗收 |
| Root | 已保存且有效的同一授權套用終端與前景 Agent；移除獨立按鈕與舊對話框；保留 hardline、撤銷與執行前重查 | 授權政策回歸；外部 su 與已取得 Root 的裝置待驗收 |
| 動態背景 | 真正座標扭曲的流體色彩波帶，API33+ AGSL／API31–32 Canvas；偏好名稱同步 | 編譯與資源檢查；GPU／視覺／耗電待實機 |

基本工具使用 Android 已有可執行檔與 Shell 函式，沒有假冒安裝 fastfetch／neofetch，沒有重新加入產品 Web 伺服器。設備未提供的 curl 等指令不宣稱可用；terminal_help 明示依裝置安裝狀態而定。

## 十項動畫落點

| # | 實作與畫面 | 主要檔案 |
| --- | --- | --- |
| 1 容器變形 | 設定列表卡片到實際詳情頁；共享邊界採 scaleToBounds | ui/motion/NavigationMotion.kt、SettingPage.kt、ZeroAgentContainers.kt |
| 2 預測返回 | Navigation 3 原生 seekable transition 將系統手勢進度帶入共享轉場、縮放與位移；取消可回彈 | RouteActivity.kt、NavigationMotion.kt；Manifest 已啟用預測返回 |
| 3 彈簧微互動 | 共用開關、收藏／可點卡片；底部面板使用官方 M3E motion／可中斷的拖曳 | MotionTokens.kt、MotionModifiers.kt、Switch.kt、ChatMessageActions.kt、ZeroAgentContainers.kt |
| 4 共享元素 | 設定清單「UI 偏好設定」標題到詳情頁標題 | NavigationMotion.kt、SettingPage.kt、SettingPreferencesUIPage.kt |
| 5 按壓觸覺縮放 | 可點卡片、設定列、首頁四個操作；相同 interactionSource，.96 縮放／放開回彈 | MotionModifiers.kt、CardGroup.kt、WorkspaceWelcome.kt |
| 6 Lottie | 首頁空狀態、訊息確實加入對話後確認、供應商底部導航選取 | MotionIllustration.kt、res/raw/motion_*.json、ChatPage.kt、SettingProviderDetailPage.kt |
| 7 流體背景 | 對話／AI 等待畫面採同一背景，前景時鐘只在繪製階段讀取 | FluidBackground.kt、MeshGradientBackground.kt、Background.kt |
| 8 共享軸線 | 設定及各頁 push／pop 同向滑動與淡入淡出；支援 X／Y 軸 | NavigationMotion.kt、RouteActivity.kt |
| 9 合成層動畫 | 新增微互動只改 graphicsLayer lambda 的透明度／位移／縮放；共享內容不逐影格重新量測 | MotionModifiers.kt、ImageMotion.kt、NavigationMotion.kt |
| 10 圖片淡入 | 對話圖片／工具生成結果透過共用圖片元件；含記憶體快取的 onSuccess，200ms 純透明度 | ImageMotion.kt、ZoomableAsyncImage.kt |

全部新增動畫使用既有系統動畫偏好觀察；系統移除動畫時直接呈現靜態狀態。持續背景／Lottie 離開前景即停止。共用 MotionTokens 中文註解集中阻尼、剛性、縮放比例、淡入時間。

沒有新增展示頁：採用現有設定列表／詳情與真實操作。SDK／Compose／Navigation 3／MaterialExpressiveTheme 沿用現有可建置設定；只新增實際使用的 Lottie 依賴。

## GitHub 整理

- 已驗證原始 v1.9.7 x86_64 APK，替換三個分段附件為單一完整檔。完整 APK SHA-256：9a927f063963c92b2d5076956dc50826bb1e90b85e22050c1e306b980a8522f9。
- 移除重複 v1.9.5 的空草稿，保留正式版；標題統一，版本由新至舊。八個分支的來源均未發現分段檔。
- 本版 APK 必須各 ABI 為單一完整檔，雜湊與遠端 digest 核對一致後才完成正式發布。

## 驗證結果與剩餘條件

- 最終正式 `assembleRelease` 通過，耗時 27m 37s，包含必要的 `lintVitalRelease`。
- 96 項聚焦 Release JVM 回歸全數通過：0 失敗、0 錯誤、0 略過；涵蓋圖片模型／供應商隔離、對話工具上下文、Root 核准、終端環境及設定搜尋。不是全專案測試或裝置驗收。
- 2320 個建置輸入與 F 槽來源一致；1695 個主要輸入指紋：`b9b16ff16699ed214035863fd70992a7a3bf56baaa9926b3697893dbff44b47d`。
- 兩個 APK 均確認 1.9.9／code 32、minSdk 31、各自單一 ABI、不可偵錯、正式簽章、ZIP CRC、必要原生程式庫、三個 Lottie 資源、第三方授權及憑證掃描。
- 正式簽章憑證與前版一致：`74d1066485313400f3e429f949d6d911bb7860fb052673b1447df8fc01c2c026`。

| APK | 大小（bytes） | SHA-256 |
| --- | ---: | --- |
| ZeroAgent-v1.9.9-arm64-v8a.apk | 48795807 | `218959bac5533ae98d8a019bb47f6aa680a37f52ef101f24e449327955c4a466` |
| ZeroAgent-v1.9.9-x86_64.apk | 51476724 | `82bdd7645b172742da2cafe599b2b93dec7a0a570b5f04f45f9fad2f6d284aec` |

交付位置：`E:/ZeroAgent/deliverables/v1.9.9`，包含完整雙 ABI APK、校驗檔、測試結果、建置證據與本報告。APK 仍超過先前 40 MB 目標；每個架構皆是單一可安裝的完整檔案。

已正式發布到私人倉庫 v1.9.9，Release ID `450817845`；來源 commit `fdae8c27eb0b629078c7ddf015e8ec331b184ff0`。八個遠端附件的大小與 SHA-256 均與本機一致，`SHA256SUMS` 已逐筆核對。發布網址：https://github.com/zero-hello/zeroagent-private/releases/tag/v1.9.9。v1.9.9 標籤保留於 APK 對應來源；此後只更新發布狀態文件。


無真實供應商帳戶／Root 手機驗收。深淺色、小螢幕、橫向、大字體、系統手勢、AGSL GPU、終端機 IME／剪貼簿需在對應裝置驗收；編譯與單元測試不能代替視覺與外部服務測試。v1.9.8 的大型 Office／研究功能界線仍見該版報告，本版不將它們改列為已完成。

本機 Windows emulator 缺少 qemu/windows-x86_64/qemu-system-x86_64.exe；既有 AVD 未被新建、清除或覆寫，沒有安裝實體手機。

舊動畫整合：12 處尺寸動畫改為合成層內容淡入，27 處顯示／隱藏與 3 處內容切換共用 ContentMotion.kt；後者停用 SizeTransform，系統移除動畫時不執行進出效果。這批修改已納入下列最終建置與 APK 驗證。

## 主要修改與使用注意

- 共用動畫：`ui/motion/`、`RouteActivity.kt`、`ui/components/ui/`、`ui/theme/Theme.kt`；真實設定、首頁、收藏、底部面板、供應商導航與圖片共用這套互動。
- 記憶入口：`RouteActivity.kt`、`AssistantMemoryPage.kt`；已清除的模型不再導向模型設定。
- 圖片生成：`ImageModelResolver.kt`、`ImageGenerationLogic.kt`、`ImageGenerationTools.kt`、`ImgGenVM.kt`；工具呼叫沿用當次對話模型。曾指定另一家圖片模型的使用者，需在目前供應商內重新選擇支援圖片生成的模型；多候選不會暗自選取。
- 終端機：`AndroidShellEnvironment.kt`、`WorkspaceTerminalPage.kt`、`WorkspaceTerminalSession.kt`、`WorkspaceTerminalSessionManager.kt`、`EmbeddedTerminalTools.kt`；Root 變更對新工作階段生效，撤銷會停止受管理的 Root 工作階段。
- 移除獨立 Android Root 指令按鈕及 `RootTerminalCommandDialog.kt`；保留終端機入口。偏好名稱統一為「動態背景」，沿用既有保存值。
- 動畫參數集中於 `MotionTokens.kt`；系統「移除動畫」啟用時，畫面直接切換。12 處舊尺寸效果、27 處進出及 3 處內容切換統一使用 `ContentMotion.kt`，避免逐影格尺寸重排；尚未完成各裝置的效能量測。
- `app/build.gradle.kts` 與 `gradle/libs.versions.toml`：版本及實際使用的 Lottie 依賴；第三方授權位於 `assets/third-party/lottie/LICENSE`。
- 文件：`README.md`、`CHANGELOG.md`、`PROGRESS.md`、`ZEROAGENT_HANDOFF.md` 與本報告同步目前版本與真實驗證結果。
