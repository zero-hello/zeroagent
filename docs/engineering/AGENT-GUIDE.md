# Agent 修改指南

## 先確定 checkout

從目前 Git 根目錄工作，先執行 `git status --short`、`git branch --show-current`、`git worktree list`。同一父資料夾可能同時有 `zeroagent-private`、`chat-refactor`、`permission-review`；它們是不同分支，不能從父目錄把同名搜尋結果混在一起。

未提交修改屬於當前工作狀態，先閱讀再決定如何延續。歷史 PR／驗證報告代表來源版本，不代表當前 checkout 已完成驗證。不要自動把這次本地重構視為已合併到遠端 main 或已發布。

## 以任務載入上下文

先讀 [架構概覽](../architecture/OVERVIEW.md) 與 [修改導航](CHAT-ARCHITECTURE.md) 的相關列，再讀入口、相關型別與最鄰近測試。避免一次讀入所有文件或完整大型核心檔案。

```powershell
rg -n 'persistNow|markDirty' app/src/main/java app/src/test/java
rg -n 'ConversationToolApproval|recheck' app/src/main/java/me/rerere/zeroagent/data/ai
rg --files app/src/test ai/src/test | rg 'Compaction|Continuation|Approval'
```

日常搜尋應限 `app/src/main/java`、相關模組 `src/main` 和測試。第三方子模組、`build`、`.gradle`、`node_modules`、native vendor、lockfile、字典、壓縮 JS、圖片型 trace 都不是預設閱讀範圍。需要協定 fixture 時先看其 README，再選單一 trace，避免輸出 base64 圖片。

## 改動原則

- 按責任拆分；呼叫者只需理解元件的輸入、結果與狀態擁有者。不要為每個 helper 再建立 interface／factory。
- ChatService 的公開入口、CoroutineScope、會話與鎖的擁有者，除非任務要求，保持原樣。
- 工具核准先讀 [權限說明](PERMISSION-REVIEW.md)；任何執行路徑都保留 hardline、headless 及執行前重查。
- 協定測試可用 fake provider，避免真實 API 費用及帳戶副作用；fixture 不放憑證。
- 重構不伴隨資料格式、版本、依賴升級、產品文案或工具名稱變更。
- `MarkdownNew` 是目前 HTML 渲染路徑，`Assistant` 仍有內部設定／資料角色；名字看似歷史遺留不代表可刪。
- `GenerationHandler` 是歷史類別名稱；目前實作為 `GenerationLoop`。舊測試類別或診斷 TAG 可能仍保留舊名，先確認用途再更動。

## 修改完成後

先跑對應聚焦測試，再依 [測試指南](TESTING.md) 擴大驗證。報告實際指令、結果、快取及未測範圍；不以文件敘述或 Git 無衝突代替編譯。維護本次涉及的架構／生命週期說明，讓下一位 Agent 不必重新從數千行推導規則。
