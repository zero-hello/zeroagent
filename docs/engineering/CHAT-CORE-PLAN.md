# 聊天核心重構範圍與驗收

日期：2026-10-18。工作分支：`refactor/chat-core-architecture`。

## 來源與順序

主 checkout 起點 `918ad83`；保留共同基底 `65c3be1` 的合併歷史，依序整合：

1. `refactor/chat-layout-and-commands`／`530f660`：抽屜與指令、工具來源、供應商編輯、平台副作用及驗證腳本。
2. `codex/permission-review-agent`／`e2c6edb`：獨立 Review Agent、核准收據與模式 revision、七語系與權限 UI。
3. 串流儲存協調器：集中 dirty marker、節流、持久化鎖及清理，先補並發回歸。
4. 聊天與生成核心：抽出壓縮生成、通知、模型請求、工具批次執行等有明確責任的元件。
5. 架構、狀態生命週期、測試與 Agent 導航；最後跑整合驗證。

兩條來源分支的歷史測試只當來源證據；本次整合測試另記錄。自動合併沒有文字衝突，編譯仍發現 GenerationLoop 缺少 `zeroAgentLocalizedContext` import，需補回。

## 不變條件

- 保留公開 ChatService 入口、Compose 呼叫端、資料庫及備份格式、工具名稱與順序。
- 保留 ASK／ASSISTED／FULL 模式、hardline、headless、Root／MCP 強制核准政策。
- 工具標記 executionStartedAt 必須先透過同一 Flow 傳至服務並持久化，才真正執行；取消不轉成成功或一般工具錯誤。
- 重試、續傳、重啟、串流背壓、工具輸出截斷及用量歸屬沿用現有規則。
- 壓縮仍只產生 request view，不刪除原始歷史；維持來源邊界校驗、工具執行摘要與超時。
- 狀態及鎖有單一擁有者；抽出元件不各自建立第二份會話或工具核准狀態。

## 驗證

聚焦 JVM 覆蓋串流持久化競態、工具核准與重跑、重試與續傳、壓縮 planner／view、工具來源及平台效果。完整驗證跑所有模組 JVM、Debug 編譯與 Lint。每項實際命令與結果寫入本次驗證報告；裝置、真實供應商與聲學行為沒有測試時明確標示。

這是保留行為的漸進拆分，不是新生成引擎或 Gradle 多模組改造。剩餘大型檔案及下一步應記錄，而不是為達行數目標增加轉接層。
