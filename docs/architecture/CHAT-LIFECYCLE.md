# 聊天狀態與生命週期

## 一回合資料流

1. UI／Web／背景入口透過 ChatService 送訊息；佇列判斷生成中、待核准、暫停及編輯狀態。
2. 會話提供目前分支的訊息，載入 request-only 壓縮 view，工廠按此次入口組裝工具。
3. GenerationLoop 發出模型請求，套輸入及輸出 transformers，透過同一 Flow 發送訊息更新。
4. 工具提案通過核准閘門；待人工核准就結束本次生成工作，保存訊息，等待服務入口續跑。
5. 可執行工具在執行前重查政策、標記開始並發送持久化更新，再執行及回傳結果。
6. 工具結果可觸發壓縮，再發下一個模型請求；無工具時完成，保存及啟動標題／建議等輔助工作。

## 狀態擁有者

| 狀態 | 擁有者 | 範圍／清理 |
| --- | --- | --- |
| 會話 StateFlow、生成 Job、引用與訊息佇列 | ConversationSession，由 ChatService 管理 | 依會話；仍被使用時不閒置移除 |
| 會話操作鎖、壓縮鎖、工具 catalog activation | ChatService | 依會話；沿用既有 idle／drop／cleanup 路徑 |
| 串流 dirty marker、最後寫入時間、持久化鎖 | StreamingConversationPersistence | 每個 ChatService 一份；不是每次生成新建 |
| Loop 當前訊息、回合預算、核准收據 | 本次 generateText／工具批次 | 不從訊息 metadata 重建執行權限 |
| 模型串流續傳／重試旗標 | 本次模型請求 | 不共享到下一回合 |
| Overlay owner、暫停／取消 | 同一 AgentRunControl | 原 Flow 的 onStart／onCompletion 清理 |
| 前景服務使用權 | ForegroundWorkTracker | 背景工作 acquire／release，避免其他工作尚在跑時停止服務 |

## 鎖與持久化

會話操作鎖、壓縮鎖與持久化鎖用途不同。不要把三者合成覆蓋網路及工具執行的大鎖，也不要在抽出元件後另建第二份持久化鎖。

串流發送可以很頻繁，Room 快照最多每 500ms 寫入一次；工具開始、核准狀態及完成等必要邊界仍走立即寫入。串流快照在取得持久化鎖後才讀 StateFlow，避免較早捕捉的 chunk 蓋掉使用者核准或編輯。

每次 markDirty 取得單調序號。寫入成功只移除自己觀察到的 marker；寫入期間出現的新 marker 必須留下。寫入失敗或快照被保護規則拒絕時，不更新成功時間或清除 dirty。背景 lifecycle flush 略過節流。

閒置移除保留未完成的 dirty 與鎖；明確 drop 沿用原語義丟棄該會話的記憶體追蹤；cleanup 清空所有追蹤。這些清理沿用服務的呼叫時機，不新增全域背景 writer。

Room 寫入仍由 ChatService 的 snapshot 邊界檢查：新且空白對話不保存、空快照不得覆寫已有內容的資料列。重啟後工具核准／停止入口須先 hydrate，避免把尚未載入的空會話當成真實內容。

## 核准、取消與恢復

ASSISTED 每次呼叫使用乾淨、無工具的模型請求審查。收據綁定對話、call ID、名稱、完整參數與模式 revision；撤銷或黑名單改變後，後續工具執行前仍要重查。訊息 metadata 用來顯示，不能視為執行授權。

工具批次保持原順序。若執行前重新變成 Pending，保留其餘兄弟工具等待續跑；已標記 executionStartedAt 但沒有結果的工具視為結果不明，不能重啟後默默重跑。

使用者停止會取消並等待工作結束，再沿既有路徑收尾及保存。工具層的 CancellationException 必須繼續傳遞；非取消錯誤才變成受長度限制的工具錯誤結果。串流／平台清理仍在原本 Flow 的上下文與邊界執行，抽出函式不能偷偷改用另一個 scope 或 launch。

## 壓縮

觸發與鎖由服務保留，ConversationCompactionCoordinator 使用服務提供的既有鎖處理 view、來源／tail 選擇及 prefix 失效；ConversationCompactionGenerator 處理模型請求、分組 map／reduce、工具執行摘要、超時及保存前來源邊界校驗。自動壓縮由 provider usage／實際 context error 驅動，不因拆分改成每次送出前估算觸發。

摘要不改原始歷史；來源 prefix 有變才清除。建立摘要時與保存前比對 node／selected message 邊界，若使用者改了來源就拒絕保存該摘要。手動、工具觸發與自動壓縮保留各自尾端保留及展示語義。
