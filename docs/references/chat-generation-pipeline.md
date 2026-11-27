# 聊天生成鏈路

本文描述目前實作。歷史類別名稱 `GenerationHandler` 已由 `GenerationLoop` 取代；部分舊測試及診斷 TAG 為相容排查仍保留原名。入口導航見 [CHAT-ARCHITECTURE](../engineering/CHAT-ARCHITECTURE.md)，鎖與取消契約見 [生命週期](../architecture/CHAT-LIFECYCLE.md)。

## 責任與呼叫路徑

```text
ChatVM / Web routes / 排程與背景入口
  → ChatService.sendMessage / 核准後續跑
  → ConversationSession：佇列、生成 Job、目前會話
  → ChatService.handleMessageComplete
      → ConversationCompactionCoordinator：request view
      → ChatToolFactory / ChatToolSources：本次工具
      → GenerationLoop.generateText：本回合 Flow
          → GenerationRequestExecutor.execute：模型請求與串流
              → GenerationStreamRecovery：retry / continue / restart 判斷
              → ai Provider：實際供應商協定與 StreamChunk
          → ConversationToolApproval：提案與執行前核准
          → GenerationToolExecutor.execute：依序執行一批工具
          → 工具結果 → 可選壓縮 → 下一次模型請求
      → ChatService collector：更新記憶體、立即／節流持久化、通知
      → 完成保存、標題與建議輔助工作
```

上面表示責任關係。所有 suspend 回呼都回到原有 Flow／工作上下文；拆分元件沒有新增自己的 CoroutineScope 或訊息 channel。

## 訊息與模型請求

送出前的使用者內容預處理留在服務；輸入 transformer 的實際順序由服務傳入。GenerationRequestExecutor 組裝 system prompt，分開穩定前綴及記憶／近期對話／runtime addendum 等易變內容，並保留原有 context 限制與舊工具圖片淘汰。

輸入 transformers 接收 context、model、assistant、settings、對話注入識別字與 workspace cwd。不要把 request-only 合成訊息寫回原始歷史。情境記憶開啟時仍使用其既有近期輪數及召回規則，與手動 AssistantMemory 分開。

Provider 在 `ai` 模組將 StreamChunk 回傳給 StreamChunkHandler。每次更新先走輸出 transforms，再發出 visualTransforms 結果；完成時再執行視覺處理與 onGenerationFinish，寫入 finishedAt。非串流請求同樣回到訊息更新回呼，不另走第二種持久化入口。

## 斷線恢復與用量

- 尚未收到有意義輸出時，依設定重試暫時性故障。取消、context overflow、確定的用戶端錯誤及額度耗盡不盲目重試。
- 已有純文字 partial 時，符合政策可發送 request-only 續傳訊息；不把續傳用合成使用者指令寫進對話。
- partial 含未完成工具呼叫時，不以文字續傳造成懸空呼叫；沿用一次從 pre-stream snapshot 重啟的既有政策。
- first-output／stall watchdog 與使用者狀態提示保留原本啟用條件，具體上限以 Settings／NetworkSetting 與 recovery 常數為準。
- UsageCallContext 仍區分 MAIN、TOOL_LOOP、COMPACTION、輔助工作及子代理 run attribution；重構不能讓 provider 呼叫掉出既有記帳上下文。

## 工具來源與核准

工具來源順序與生成／重跑差異以 ChatToolFactory 的具名入口為準。LocalTools、workspace readiness、冷記憶、skills、MCP progressive catalog 及子代理能力限制不由 executor 重新決定。

新提案與人工互動後續跑都經 ConversationToolApproval。執行前再次 recheck，避免前一個長工具執行期間，權限模式或黑名單已改變。收據不可從 metadata 還原。完整細節見 [權限與 Review Agent](../engineering/PERMISSION-REVIEW.md)。

工具狀態：

| 狀態 | 處理 |
| --- | --- |
| Pending | 等人工核准，保存並離開本次生成；不另開新模型回合掩蓋它 |
| Denied | 回傳既有拒絕結果，不執行工具 |
| Answered | 使用使用者回答作為結果 |
| Auto／Approved | 仍經執行前政策、hardline、headless 及預算檢查後才執行 |

工具批次保持呼叫順序。已完成呼叫不重跑；同批尚未執行的 Auto 兄弟工具可在核准續跑時處理。若執行前變為 Pending，保留後續兄弟工具等待下一次續跑。

## 執行、結果與儲存

執行前先設定 executionStartedAt，再以 suspend 回呼發送訊息，使服務在真正呼叫工具前保存開始標記。程序中斷後只有開始標記卻沒有輸出，代表副作用結果不明，恢復時不能自動重做。

單工具及整回合都有既有預算；唯讀重試由 ToolExecutionRetryPolicy 判斷，不能把寫入工具納入一般網路重試。GenerationLoopGuard 依回合內執行歷史、觀察工具 freshness TTL 與 intervening action 判斷是否重複失控。

輸出超過字元／可選 token 預算時，沿用截斷與 `/tool_outputs/` 保存邏輯；只有本回合真的有 workspace shell 才顯示 shell 讀取提示。非文字輸出保留。

工具結果內嵌在 assistant 訊息 parts 中，不另建立 TOOL 角色訊息。一般錯誤回傳有長度上限的 envelope；CancellationException 繼續傳遞，不轉成 tool_failed 或繼續執行兄弟工具。

ChatService collector 更新 session，StreamingConversationPersistence 管理 dirty marker、500ms 節流及明確保存共用的鎖；立即保存、背景 flush、空快照防護見生命週期文件。

## 壓縮與完成

ConversationCompactionCoordinator 在既有會話壓縮鎖下選擇來源與 tail，ConversationCompactionGenerator 做 map／reduce 請求、保留 deterministic 工具執行摘要並校驗來源邊界。壓縮摘要只替換模型 request view，原始訊息不刪除；自動壓縮展示只有真正新增摘要才附上。

Flow 的平台開始／完成 hook 及 `flowOn(IO + runControl)` 留在 GenerationLoop。ChatService 沿用完成、失敗與取消的保存與通知處理。標題與建議是輔助工作，不應把它們的失敗誤當主要模型回覆失敗。

## 修改後驗證

先選 generation／retry／continuation、tool executor／approval／loop guard、compaction 或 persistence 的相關 JVM 測試，再依 [TESTING](../engineering/TESTING.md) 擴大至全模組及 Android 驗證。圖與文字不取代測試；如入口或責任變動，同步更新本頁與修改導航。
