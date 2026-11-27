# 開發文件導航

這裡以「要理解／修改哪個行為」組織文件。正式版本、安裝及產品能力從 [專案 README](../README.md) 開始；工程文件描述目前程式結構，歷史交付報告只代表當時版本。

## 建議閱讀順序

1. [專案架構](architecture/OVERVIEW.md)：模組邊界、Android／模型／工作區與儲存的關係。
2. [聊天架構修改導航](engineering/CHAT-ARCHITECTURE.md)：入口檔案與最小驗證。
3. [聊天狀態與生命週期](architecture/CHAT-LIFECYCLE.md)：佇列、Flow、鎖、儲存、取消與壓縮。
4. [Agent 工作指南](engineering/AGENT-GUIDE.md)：搜尋範圍、修改規則與情境載入。
5. [測試與驗證](engineering/TESTING.md)：聚焦、全模組、Lint 與裝置驗證。

## 專題與證據

| 主題 | 文件 |
| --- | --- |
| 權限模式、Review Agent 與收據 | [PERMISSION-REVIEW](engineering/PERMISSION-REVIEW.md) |
| 共用介面、分隔與無障礙版型 | [UI 元件導航](engineering/UI-UX-POLISH.md) |
| 聊天生成資料流 | [生成鏈路](references/chat-generation-pipeline.md) |
| 自動壓縮 | [壓縮指南](references/context-auto-compaction-guide.md) |
| 一般原生語音與已移除助理的相容說明 | [語音指南](VOICE_ASSISTANT.md)、[裝置驗收清單](VOICE_ASSISTANT_QA.md) |
| 影片生成 | [影片協定](VIDEO-GENERATION.md) |
| Workspace 主機／裝置測試 | [Workspace TESTING](../workspace/TESTING.md) |
| 前一輪聊天邊界重構 | [來源驗證紀錄](engineering/CHAT-REFACTOR-VERIFICATION.md) |
| 本輪重構範圍 | [計畫與不變條件](engineering/CHAT-CORE-PLAN.md) |
| 本輪整合、測試與限制 | [聊天核心驗證](engineering/CHAT-CORE-VERIFICATION.md) |
| v1.9.5 逐項修改、驗證與待驗收項目 | [v1.9.5 交付報告](V1_9_5_DELIVERY.md) |
| v1.9.7 本輪修正、APK 驗證與剩餘裝置驗收 | [v1.9.7 交付報告](V1_9_7_DELIVERY.md) |
| v1.9.8 專案、效率工具、模型目錄與逐項驗收 | [v1.9.8 交付報告](V1_9_8_DELIVERY.md) |
| Agent 與授權裝置驗收 | [現行驗收清單](engineering/QA-PHASE2.md) |
| 結構拆分決策 | [ADR-001](decisions/001-chat-core-boundaries.md) |

修改行為時同步更新對應專題，不把最新規格只寫在交付報告裡。量測、建置與測試結果須區分本輪重跑、快取重用及歷史紀錄。

本版：[v1.9.9 交付與動畫落點](V1_9_9_DELIVERY.md)。
