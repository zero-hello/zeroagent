# 聊天 UI、工具組裝、供應商設定與平台邊界重構

日期：2026-10-18。PR #4，基底為 `main` 的 `65c3be1`。保留前兩個聊天佈局／指令提交，再以四個提交統一工具組裝、抽離供應商設定規則、隔離 Android 操作，以及固定維護／驗證入口。保留 Compose、產品行為與資料格式；沒有新增相依套件、模組或工具權限。

完整修改導航與狀態歸屬見 [CHAT-ARCHITECTURE.md](CHAT-ARCHITECTURE.md)。本文件保留前兩個提交的驗證歷史，以下延伸範圍的結果取代舊的全量數字。

## 延伸範圍的分步驗證

| 提交 | 結果與覆蓋 |
| --- | --- |
| `667b19e` 工具組裝 | 109 項相關 JVM 測試通過（含 9 項新 factory 測試），Kotlin 編譯成功。生成／重跑順序、模型搜尋差異、MCP 名稱／schema／呼叫／核准、同名伺服器、無效名稱、catalog 狀態沿用與移除、子代理及無頭限制、來源缺席及壓縮回呼 |
| `c797be8` 供應商設定 | 12 項 JVM 測試、4 項裝置測試通過；ARM64／x86_64 Debug 與測試 APK 建置成功。網址轉換、預設與重設、欄位保留、OAuth／本地 no-op、trim、密碼遮罩切換、Google／Vertex／服務帳戶顯示 |
| `3c67ce0` 平台適配 | 29 項相關 JVM 測試通過（含 9 項新適配契約測試）；雙架構 Debug 建置成功。操作順序、相同 owner、暫停先於取消、成功／失敗／真實 Job 取消、使用者切換、前景缺失、啟動 Exception、JVM Error 傳遞、截圖保留與清理 |
| 維護與腳本 | PowerShell parser 通過；三種模式 DryRun 通過，缺少／實體序號拒絕；實際 Focused 執行 162 項測試及編譯通過。Full／Device 最終結果記錄於下節 |

新契約測試均先在沒有新介面／函式的情況下編譯失敗，再接上正式實作。供應商首次裝置回歸有一個測試誤用 Compose 保留的 Password 語意旗標；改成讀取 TextLayoutResult，驗證實際遮罩／明文及切換圖示，四項重跑通過，未為測試更改產品行為。

## 延伸範圍的最終驗證

- `Focused`：162 項測試，0 failures、0 errors、0 skipped；Kotlin 編譯成功。
- 全模組 JVM：13 模組共 **3,552 項**，0 failures、0 errors、0 skipped；app 2,729 項。未受影響模組重用既有測試輸出。
- ARM64／x86_64 Debug APK 與 AndroidTest APK 建置成功；兩種產品 APK 各含 25 個原生函式庫。
- `Full` 腳本完整執行成功，耗時 4m 47s；包含 `:app:lintDebug`，0 Fatal、0 Error，保留 1,148 項 Warning 與 9 項 Hint。使用單 worker 與單次 3 GB heap，未更改專案預設值或停用檢查。
- `Device` 腳本在專用 `ZeroAgent_API35`、API 35 x86_64 的 `emulator-5558` 成功：抽屜 3 項、輸入控制 6 項、供應商欄位 4 項，共 **13 項通過**。使用完整檢查產生的最終 Debug APK；裝置建置階段 23s，測試執行 17.638s。
- `git diff --check` 與程式邊界檢視通過。原工作區／冷記憶與供應商轉換函式比對既有行為；沒有更動聊天排程、鎖、串流持久化、壓縮流程或 Android Intent flags。

## 統一重現入口

設定 JDK 21、`ANDROID_HOME`（或 `ANDROID_SDK_ROOT`）以及 checkout 既有的 `ZEROAGENT_PNPM_STORE`；腳本不修改專案預設值：

```powershell
.\scripts\verify_chat_architecture.ps1 -Mode Focused
.\scripts\verify_chat_architecture.ps1 -Mode Full
.\scripts\verify_chat_architecture.ps1 -Mode Device -Serial emulator-5558
```

`Full` 單 worker，使用已驗證的單次 3 GB heap，包含所有模組 JVM、雙架構 Debug、測試 APK 和 Lint。`Device` 限在線模擬器與固定 Debug 套件，依 ABI 安裝，不清除資料；檢查 runner 的成功文字，避免 adb exit 0 掩蓋測試失敗。`-DryRun` 只顯示命令，仍驗證序號格式。

最終日誌在忽略的 `build/architecture-verification/`；分步日誌為 `build/architecture-tools-final.log`、`architecture-provider-final.log`、`architecture-provider-device-final.log`、`architecture-platform-final.log`。測試 XML 位於各模組 `build/test-results/testDebugUnitTest/`。未使用實機或呼叫真實供應商／付費模型。

## 前兩個提交：修改與驗證入口

| 要修改的行為 | 負責位置 | 對應測試 |
| --- | --- | --- |
| 模態／常駐抽屜、返回與切換狀態 | `ui/pages/chat/ChatDrawerLayout.kt` | `ChatDrawerLayoutTest`（裝置） |
| 抽屜和聊天內容的資料接線 | `ui/pages/chat/ChatPage.kt` | 編譯、抽屜測試；輸入控制沿用 `ComposerControlsTest` |
| 指令識別及系統指令區塊更新 | `data/chat/ChatCommands.kt` | `ChatSlashCommandTest`（JVM） |
| 指令執行、權限重設與消耗草稿的順序 | `data/chat/ChatCommandHandler.kt` | `ChatCommandHandlerTest`（JVM） |
| 讀取對話、更新狀態、排程儲存與清空輸入 | `ui/pages/chat/ChatVM.kt` | 編譯；處理器以回呼隔離服務依賴 |
| 本地化描述、補全、彈窗、導航及提示 | `SlashCommandCompletionProvider.kt`、`ChatPage.kt` | 編譯；既有資源維持原樣 |

`ChatPage` 只供應一份聊天內容和一份抽屜內容；840dp 分界、玻璃效果、焦點／鍵盤處理維持原有接線。抽屜元件保留原本兩個返回處理器的註冊位置，讓模態抽屜優先於聊天內容的返回處理器關閉；切到常駐抽屜時清除模態開啟狀態。

指令處理器透過 ViewModel 提供的回呼存取實際狀態，不需要 Compose、Android Context 或啟動 App 就能單元測試。畫面效果同步交給本次操作的呼叫端處理，保持「執行動作／顯示提示後才清空草稿」的順序，不新增需要跨生命週期消費的事件佇列。

解析與指令區塊更新函式原樣搬移。`/plan` 先重設為 ASK 再更新及排程儲存；`/goal` 不重設存取模式；空白 `/goal` 顯示用法且保留草稿；`off` 保留使用者指令與另一個指令區塊。傳送按鈕對編輯／附件的指令判定條件及長按傳送流程維持原樣。

## 前兩個提交：驗證歷史

- 第一步：既有指令測試 2 項、兩種架構 Debug APK 與測試 APK 建置成功；抽屜裝置測試 3 項通過。返回測試另外註冊聊天內容的返回處理器，驗證抽屜關閉優先序。
- 第二步：新增測試先因處理器尚未存在而在測試編譯階段失敗；接上實作後，處理器 10 項與原有解析／區塊測試 2 項全部通過。涵蓋精確提示文字、空白參數、移除／保留區塊、一般文字、未知指令、多行草稿及同步操作失敗時不清空草稿。
- 全模組 JVM：13 模組共 **3,529 項**，0 failures、0 errors、0 skipped；其中 app 為 2,706 項。Gradle 正常重用未受影響模組的測試快取。
- 最終 Debug APK：ARM64／x86_64 與裝置測試 APK 建置成功；兩種產品 APK 各含 25 個原生函式庫。最終驗證包含既有 Web 建置及原生建置任務。
- 最終裝置回歸：API 35 x86_64 專用模擬器，抽屜 3 項與既有輸入控制 6 項，共 **9 項通過**。輸入控制包含停止／下一則草稿、長按傳送、模型／推理操作和窄版大字體排列。
- Lint：原完整命令已完成測試及 APK 任務，但 `:app:lintDebug` 的 2 GB heap 用滿，記錄到 199 次 Full GC、約 338 秒 GC 時間。終止本輪 Gradle daemon 後，以單次 3 GB heap 設定重跑 Lint，4m 36s 完成並通過，0 Fatal、0 Error；既有非阻擋警告仍保留。沒有更改專案 heap 設定或停用檢查。
- 靜態檢查：`git diff --check` 通過；原樣搬移的解析及區塊更新函式已與基底逐字比對。

這次新增的裝置測試直接驗證抽屜元件；指令處理以 JVM 測試驗證規則及回呼契約。未執行實機或真實供應商通話／模型請求。

## 前兩個提交：歷史命令

使用 JDK 21 和已設定 `ANDROID_HOME` 的 PowerShell。本輪 `ZEROAGENT_PNPM_STORE` 指向原 checkout 的共用快取；在此 worktree 重跑時先沿用相同設定，避免 pnpm store 不一致：

```powershell
$env:ZEROAGENT_PNPM_STORE = (Resolve-Path '..\zeroagent-private\.pnpm-store').Path
```

其他 checkout 可使用其既有 store 設定或專案預設值。只改指令規則時可先跑：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'me.rerere.zeroagent.data.chat.*' --no-daemon --max-workers=1
```

本輪測試與建置命令（其 Lint 階段因上述記憶體壓力終止）：

```powershell
.\gradlew.bat testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --continue --no-daemon --max-workers=1
```

本輪 Lint 獨立重跑命令；後續執行完整命令時也可沿用此 JVM 參數：

```powershell
.\gradlew.bat :app:lintDebug '-Dorg.gradle.jvmargs=-Xmx3072m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=256m -XX:NonNMethodCodeHeapSize=32m -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true' --no-daemon --max-workers=1
```

裝置回歸只對專用模擬器的 Debug 套件執行：

```powershell
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w -e class 'me.rerere.zeroagent.ui.pages.chat.ChatDrawerLayoutTest,me.rerere.zeroagent.ui.components.ai.ComposerControlsTest' app.zeroagent.debug.test/androidx.test.runner.AndroidJUnitRunner
```

原始本機日誌位於忽略的 `build/chat-refactor-baseline.log`、`build/chat-refactor-layout-isolated.log`、`build/chat-refactor-layout-device.log`、`build/chat-refactor-commands-red.log`、`build/chat-refactor-final.log`、`build/chat-refactor-lint.log` 與 `build/chat-refactor-final-device.log`；JUnit XML 位於各模組的 `build/test-results/testDebugUnitTest/`。
