# 聊天架構修改導航

保留 Compose、既有資料格式及服務生命週期。以下路徑以 `app/src/main/java/me/rerere/zeroagent/` 為起點。

| 要修改的行為 | 從哪裡開始 | 最小驗證 |
| --- | --- | --- |
| 抽屜、寬窄版佈局、返回順序 | `ui/pages/chat/ChatDrawerLayout.kt`；接線在 `ChatPage.kt` | `Device`：`ChatDrawerLayoutTest`、`ComposerControlsTest` |
| 聊天指令解析、執行、何時清空草稿 | `data/chat/ChatCommands.kt`、`ChatCommandHandler.kt`；`ChatVM.kt` 提供狀態與回呼 | `Focused`：`ChatSlashCommandTest`、`ChatCommandHandlerTest` |
| 生成／重跑有哪些工具及其順序 | `data/ai/tools/ChatToolFactory.kt` 的 `createForGeneration`／`createForRerun` | `Focused`：`ChatToolFactoryTest` |
| 本地、工作區、冷記憶、技能、MCP 的實際來源 | `data/ai/tools/ChatToolSources.kt` 的 `DefaultChatToolSources` | `Focused`；冷記憶及工作區實作變動再跑 `Full` |
| 本地工具的模型能力、無頭限制 | `data/ai/ToolSurfaceResolver.kt`；子代理最後限制在 `subagent/SubAgentSurface.kt` | `Focused`：resolver、invocation context、子代理與 headless approval 測試 |
| MCP 名稱、搜尋／開啟 catalog、核准政策 | `data/ai/mcp/McpManager.kt` 的 `buildMcpToolName`、`data/ai/tools/ToolCatalog.kt` 及 factory | `Focused`：MCP name、catalog、factory、approval 測試 |
| 供應商型別轉換、網址、預設值、重設 | `data/provider/ProviderSettingEditor.kt` | `Focused`：`ProviderConfigureConvertToTest`、`ProviderSettingEditorTest` |
| 遠端供應商的共用名稱、金鑰、網址欄位 | `ui/pages/setting/components/ProviderConnectionFields.kt`；供應商條件仍在 `ProviderConfigure.kt` | `Device`：`ProviderConnectionFieldsTest` |
| Overlay、自動返回、提示、截圖清理 | `data/ai/GenerationPlatformEffects.kt`（契約／純判斷）、`AndroidGenerationPlatformEffects.kt`（適配）；Koin 在 `di/DataSourceModule.kt` | `Focused`：`GenerationPlatformEffectsTest`、`AgentRunControlTest`、`ScreenshotLedgerTest` |
| 會話、佇列、鎖與壓縮觸發 | `service/ChatService.kt`、`service/ConversationSession.kt` | session／queue／compaction JVM，核心修改再跑 `Full` |
| 待送、編輯與排程共用佇列 | `service/ChatOutgoingQueue.kt`，共用 `ConversationSession` 及 `ChatDraftStore` | `MessageQueueTest`、`CronJobWorkerLlmAwaitTest` |
| 訊息與附件前處理、回收及超限重試 | `service/ChatInputPreprocessing.kt`、`QueuedAttachmentCleanup.kt`、`ChatContextRetry.kt` | `QueuedAttachmentCleanupTest`、`ChatContextRetryTest` |
| 串流節流、dirty marker、持久化鎖與清理 | `service/StreamingConversationPersistence.kt`；快照保護及 Room 寫入仍在 ChatService | `StreamingConversationPersistenceTest`、`ChatServiceTest` |
| 壓縮 view、來源／tail 選擇、prefix 失效 | `service/ConversationCompactionCoordinator.kt`；會話鎖由 ChatService 提供 | compaction planner／view／模型選擇 JVM，`Full` |
| 壓縮模型請求、map／reduce、來源邊界校驗 | `service/ConversationCompactionGenerator.kt` | compaction planner／view／模型選擇 JVM，`Full` |
| 聊天通知、穩定 ID 與內容呈現 | `service/ChatNotifications.kt` | Kotlin 編譯／Lint；Android 通知另做裝置驗證 |
| 單回合模型／工具交替與訊息發送 | `data/ai/GenerationLoop.kt` | `Generation*Test`、核准／恢復測試 |
| 模型 request、transformer、串流及用量歸屬 | `data/ai/GenerationRequestExecutor.kt` | generation／stream JVM，`Full` |
| 斷線重試、續傳、重啟與 watchdog | `data/ai/GenerationStreamRecovery.kt` | `GenerationHandlerTransportRetryTest`、`GenerationHandlerContinuationTest`、stream JVM |
| 工具批次、執行前核准重查、開始標記與輸出截斷 | `data/ai/GenerationToolExecutor.kt` | `GenerationToolExecutorTest`、核准／retry JVM，`Full` |
| 觀察工具 TTL、重複呼叫保護 | `data/ai/GenerationLoopGuard.kt` | loop guard JVM |
| ASK／ASSISTED／FULL、審查及權限撤銷 | `data/ai/tools/ConversationToolApproval.kt`、`ToolReviewAgent.kt`、`ConversationAccess.kt` | approval／review JVM；詳見 PERMISSION-REVIEW |
| 真正的供應商請求／串流協定 | `ai` 模組的 provider 實作 | 該模組 JVM 測試及 `Full`；設定表單不負責網路協定 |

## 依賴與狀態歸屬

`ChatService` 建立型別化 `ChatToolContext`，呼叫 factory 的具名入口。Factory 只透過一個 `ChatToolSources` 介面取得工具；正式來源接既有 Repository／Manager，測試用 fake，沒有為每個 Repository 再加一層介面。壓縮以回呼傳入，factory 不依賴聊天服務。

| 狀態 | 原持有者與生命週期 |
| --- | --- |
| 工作、佇列、會話／壓縮鎖與壓縮輸入 | `ChatService`；沿用既有對話／工作生命週期 |
| 持久化鎖、dirty marker、節流及清理 | `StreamingConversationPersistence`；ChatService 明確保存與串流寫入共用同一 owner |
| Progressive catalog 的 `ToolActivationState` | `ChatService` 的對話狀態；factory 延遲取得並移除已消失的 MCP 名稱，不自行快取或重設 |
| 本次執行的暫停／取消與 Overlay owner | `GenerationLoop` 的 `AgentRunControl`；適配層只轉交同一個 owner 與暫停回呼 |
| 導航、畫面操作觀察、暫存截圖 | 既有 `AgentTurnTracker`、`AutomationRecorder`、`ScreenshotLedger` 單例；適配層在原本的 Flow 起訖位置操作，沒有改成對話隔離狀態 |
| 冷記憶觀察的持久化 | `ChatService.flushAutomationPlaybook`；適配層只負責開始時 reset，不消費完成後的觀察 |
| 設定值與儲存 | 原設定呼叫端／ViewModel；共用欄位只接受值與回呼，密碼可見性留在欄位自己的 Compose remember |

`GenerationToolExecutor` 在原本的執行前取消檢查之後呼叫工具提示；開始標記以 suspend 回呼回到原 Flow。`GenerationLoop.withPlatformEffects` 保持原 `onStart → flow → onCompletion` 與 `flowOn(IO + runControl)` 的位置。完成、失敗及取消都沿同一個清理入口。適配層仍依序隱藏 Overlay、處理返回、清理截圖；不新增獨立 scope 或清理順序。

## 必須保留的行為差異

- 生成的外部搜尋由原 assistant／model 決定；重跑沿用 surface assistant 的開關。壓縮與 progressive catalog 只屬於生成。
- 生成開始前的 MCP 全域名稱校驗仍在服務原位置；組裝時再校驗實際來源。重跑原本沒有這個校驗，保持原樣。子代理以原本的 reference identity 決定 MCP 來源範圍；最後再套子代理工具限制。
- MCP 名稱沿用 UUID slug 規則；同名伺服器仍可區分，每個 MCP 工具仍要求核准。工具順序保持搜尋、本地、工作區、冷記憶、技能、生成專用工具、MCP。
- 工作區 shell 工具要求 READY；冷記憶只要求功能開啟、有效目錄和存在的工作區，並不要求 shell READY。缺少檔案維持空資料，取消仍傳遞。
- Google URL 的 `/v1beta` 尾碼檢查與一般 URL 檢查不同。Vertex／服務帳戶仍控制各家欄位顯示；OAuth 和本地模型的特殊欄位、限制及 trim 規則留在各自呼叫端。
- 單純開啟另一個 App 不自動返回；必須同時有導航與畫面操作。使用者切到第三個 App 時略過返回；缺少前景或目的 App 資訊時沿用原本允許返回的行為。啟動失敗只捕捉 Exception，保留原 flags、提示文字與主執行緒派送。

## 驗證入口

在設定好 JDK 21、Android SDK 與既有 pnpm store 的 PowerShell 執行：

```powershell
.\scripts\verify_chat_architecture.ps1 -Mode Focused
.\scripts\verify_chat_architecture.ps1 -Mode Full
.\scripts\verify_chat_architecture.ps1 -Mode Device -Serial emulator-5558
```

`Focused` 執行這些邊界的 JVM 測試與 Kotlin 編譯。`Full` 執行所有模組 JVM 測試、ARM64／x86_64 Debug APK、測試 APK 與 Lint，單 worker，僅本次命令使用 3 GB heap。`Device` 需要明確指定在線的專用模擬器序號，依 ABI 安裝固定 Debug APK，執行抽屜、輸入及供應商欄位測試；不清除使用者資料。先用 `-DryRun` 可檢查命令而不建置或操作裝置。

日誌在忽略的 `build/architecture-verification/`。裝置 runner 即使 exit 0，腳本仍要求 `OK (N tests)` 且無失敗／崩潰訊息。使用專用模擬器及測試值，不呼叫真實供應商。實際結果見 [驗證紀錄](CHAT-REFACTOR-VERIFICATION.md)。
