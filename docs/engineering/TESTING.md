# 測試與驗證

## 工具鏈

從 repository root 使用 `gradlew.bat`。JDK 21 由 `gradle/gradle-daemon-jvm.properties` 指定；SDK 路徑透過 `ANDROID_HOME`／既有 local 設定提供。不要把本機絕對路徑寫進版本控制。

Windows 上若預設 Java 是其他版本，將當前 PowerShell 的 `JAVA_HOME` 指向既有 JDK 21，再執行 wrapper。不要為測試更改專案版本、停用檢查或提交 local.properties。

Gradle 需要寫入使用者快取；沙箱拒絕快取 lock 時，使用具授權的建置執行，不刪除別人的 lock 或啟動另一組平行 Gradle。等待當次建置完成，再更改它正在編譯的來源或啟動下一輪。

## 選擇驗證層級

| 修改 | 最小起點 | 需要擴大時 |
| --- | --- | --- |
| 串流儲存、session 操作 | `*StreamingConversationPersistenceTest`、`*ChatServiceTest`、`*ConversationSession*` | 完整 app JVM；Room 語義另跑裝置測試 |
| 模型請求、重試與續傳 | `*Generation*Test`、`*Stream*Test` | app 與 ai JVM |
| 工具批次、核准與重跑 | `*GenerationTool*Test`、`*ConversationToolApprovalTest`、`*ToolReviewAgentTest`、`*ToolExecutionRetry*`、`*LoopGuard*` | headless／subagent／catalog 測試及完整 app JVM |
| 壓縮 | `*Compaction*Test`、`*ChatServiceModelSelectionTest` | 完整 app JVM；資料庫改動另跑 migration／DAO 裝置測試 |
| 工具來源／平台效果／設定邊界 | `verify_chat_architecture.ps1 -Mode Focused` | Full；UI 改動再跑對應 Device |
| Compose 與資源 | JVM 編譯、資源測試及對應 androidTest | 窄版、大字體、RTL 等實際改動範圍 |

JUnit 類別名稱含 `GenerationHandler` 的既有測試仍測試目前的重試／續傳 helper，不因歷史名稱略過。

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*StreamingConversationPersistenceTest' --tests '*ConversationToolApprovalTest' --no-daemon --max-workers=1 --console=plain
.\scripts\verify_chat_architecture.ps1 -Mode Focused
.\scripts\verify_chat_architecture.ps1 -Mode Full
```

核心重構的完整 JVM 驗證使用根任務 `testDebugUnitTest`，涵蓋所有註冊了該任務的模組；Debug APK／測試 APK 及 Lint 可分階段跑，降低同時記憶體壓力。既有 Full 模式以單次 3GB heap 執行，不改專案預設值。

Web 改動再從 `web-ui` 使用既有 package manager 執行 typecheck／build；注意 `lint` 會 `--fix`，不是純唯讀檢查。不要為確認 Kotlin 重構而無關修改 Web 格式。

## 裝置與外部服務

Device 模式需明確提供在線、專用模擬器序號，安裝固定 Debug 套件；會改變該模擬器的應用程式狀態。先列出可用裝置並確定用途，不操作正在使用的正式套件或個人資料。`-DryRun` 可檢查指令。

```powershell
.\scripts\verify_chat_architecture.ps1 -Mode Device -Serial emulator-5558 -DryRun
```

adb／instrumentation 的 exit 0 不足以證明測試成功，必須檢查 runner 的 `OK (N tests)` 與失敗／崩潰文字。真實供應商、OEM 背景存活、喚醒聲學及耗電需要對應環境，JVM 測試不能證明它們。

## 證據與限制

本機日誌放忽略的 `build/`。JUnit XML 在各模組 `build/test-results/testDebugUnitTest/`；Lint 在對應 `build/reports/`。統計 tests／failures／errors／skipped 時讀 XML，區分當次執行與未受影響的快取結果。建置完成後執行 `git diff --check`。

重構前可先補目前行為的 characterization test，再執行抽出；測試新增元件時，先確認缺少實作的預期失敗，再接上正式入口。不要為產生紅燈改壞原本正確的行為。
