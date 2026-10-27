# AI 模組

此模組提供共用模型／訊息型別、provider 介面及遠端協定實作。專案總覽見 [架構文件](../docs/architecture/OVERVIEW.md)，應用層生成編排見 [聊天鏈路](../docs/references/chat-generation-pipeline.md)。

## 閱讀入口

| 路徑（相對於 src/main/java/me/rerere/ai） | 責任 |
| --- | --- |
| `core/Tool.kt`、`core/Usage.kt` | 工具定義、輸入 schema 及用量基礎型別 |
| `provider/Provider.kt` | Provider 介面、文字／圖片／影片生成參數與結果 |
| `provider/ProviderSetting.kt`、`Model.kt` | 供應商與模型資料契約 |
| `provider/ProviderManager.kt` | 註冊、依型別取得 provider 及用量 decorator 接點 |
| `provider/providers/openai/` | OpenAI 相容、Chat Completions／Responses 及多模態適配 |
| `provider/providers/google/`、`claude/` | 對應 provider 協定與串流解碼 |
| `provider/stream/` | 共用 SSE／串流處理支援 |
| `ui/Message.kt`、`UIMessagePart.kt` | 跨 provider 的內部訊息與 parts；不是 Compose 畫面 |
| `ui/StreamChunk.kt`、`StreamChunkHandler.kt` | 統一增量事件及訊息合併 |
| `registry/` | 模型能力註冊與 DSL |

## 邊界

協定適配不負責手機工具授權、會話佇列、Room 對話保存或 Compose 設定畫面。新增 provider 能力時，先確認請求、串流／非串流結果及用量語義，再接 app 的設定及工具能力限制。

本地引擎由 `local-llm`／`llama-cpp` 與 app 的註冊接線提供；舊版 MNN 原生建置步驟已不適合作為這個模組的入門指南。原生子模組與 ABI 建置請依根目錄 README 及對應引擎模組。

## 測試

從 repository root 使用 JDK 21 執行：

```powershell
.\gradlew.bat :ai:testDebugUnitTest --no-daemon --max-workers=1
```

測試位於 `src/test/java`。串流 fixture 使用前先看 [trace 說明](src/test/resources/stream-traces/README.md)，只讀需要的單一案例；部分 trace 含圖片資料，不應整批輸出到 Agent 上下文。修復協定需以假回應／trace 覆蓋，不使用真實 API 憑證作為一般單元測試依賴。
