# 聊天核心整合與拆分驗證

日期：2026-10-18。工作分支：`refactor/chat-core-architecture`。這是本地整合驗證，不代表已合併至遠端 main 或發布正式版。

## 來源與整合

- 起點：`918ad83`（主 checkout）。
- 合併聊天分支 `530f660`，保留抽屜、指令、工具來源、provider 設定及平台效果拆分。
- 合併權限分支 `e2c6edb`，保留 ASK／ASSISTED／FULL、Review Agent、收據 revision 及權限 UI。
- 共同基底為 `65c3be1`。Git 自動合併無文字衝突，但整合編譯找出 `zeroAgentLocalizedContext` import 遺漏，修復於 `2efe30a`。

## 變更邊界

| 責任 | 現在的位置 |
| --- | --- |
| 串流 dirty／節流／共用持久化鎖 | StreamingConversationPersistence |
| 自動與手動壓縮 view／tail／prefix 判斷 | ConversationCompactionCoordinator，鎖仍由 ChatService 提供 |
| 摘要模型請求、map／reduce、來源邊界校驗 | ConversationCompactionGenerator |
| 通知內容、穩定 ID 及 PendingIntent | ChatNotifications |
| 模型請求、transformer、串流及用量上下文 | GenerationRequestExecutor |
| 重試、續傳、重啟與 watchdog helper | GenerationStreamRecovery |
| 批次工具重查、執行開始發送、預算及結果處理 | GenerationToolExecutor |
| 觀察工具 TTL／重複呼叫決策 | GenerationLoopGuard |

公開 ChatService／GenerationLoop 入口、模型及工具協定、Room／備份格式、產品版本不變。沒有增加依賴、Gradle module、背景 scope 或第二份核准狀態。

## 檔案規模

比較初始主 checkout 與本輪實作，行數含空白與註解：

| 檔案 | 初始 | 本輪 |
| --- | ---: | ---: |
| ChatService.kt | 3,818 | 2,709 |
| GenerationLoop.kt | 1,982 | 505 |

新的元件約 73～589 行。ChatService 仍保留會話、佇列、訊息更新及工作協調，是後續按責任整理的空間；本輪沒有為達行數門檻改寫所有生命週期。GenerationRequestExecutor／GenerationToolExecutor 的主流程也仍可再細化，應以真實修改需求和行為測試為依據。

## 測試順序與結果

1. 新增 StreamingConversationPersistenceTest，先確認缺少協調器造成測試編譯失敗；主程式整合 import 問題先行修復，預期紅燈紀錄為 `build/chat-core-persistence-red-fixed.log`。
2. 接上協調器與服務，儲存、ChatService、工具來源、平台效果及核准聚焦驗證通過：`chat-core-persistence-green.log`。
3. 模型請求／工具批次／摘要生成拆分後，generation、loop guard、compaction、approval、review、retry、persistence 與 service 聚焦回歸通過：`chat-core-extraction.log`。
4. 壓縮協調拆分後，根任務 `testDebugUnitTest` 通過：13 模組 **3,628 tests、0 failures、0 errors、0 skipped**。app 為 2,805 項；本輪新增 10 項。未受影響模組依 Gradle 任務圖重用既有測試結果，沒有宣稱全部重新執行。日誌 `chat-core-full-jvm.log`，彙總 `chat-core-jvm-summary.json`。

新增測試保護：500ms 節流與 lifecycle flush、失敗／拒絕寫入保留 dirty、交易中到來的新 chunk、明確保存與串流共用鎖且鎖內讀最新快照、idle／drop／clear、跨會話節流，以及工具拒絕／回答順序、執行前撤銷時保留兄弟工具、核准重查取消傳遞。

另以一次性來源比對確認模型請求、工具批次、工具 retry／輸出、摘要生成、自動壓縮、手動壓縮及通知呈現的主要方法本體，在忽略縮排與具名接線替換後一致。結果位於忽略的 `build/chat-core-move-comparison.json`。這是機械搬移的輔助證據，不取代行為測試。

## APK、Lint 與最終檢查

`:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 整體 **BUILD SUCCESSFUL**，耗時 5m 37s；562 個任務中 28 個執行、534 個 up-to-date。ARM64／x86_64 Debug APK 及測試 APK 均產生於 `app/build/outputs/apk/`，沒有安裝或發布。

13 個模組的 Lint XML 合計 0 Fatal／Error、1,158 warnings、9 hints；app 為 1,148 warnings、9 hints。以 ID、訊息與檔案路徑比對先前本機 app 報告，沒有新增或搬移的 issue。未新增 baseline、停用規則或調降檢查。結果在 `build/chat-core-build-lint.log`、`chat-core-lint-summary.json` 及各模組 Lint report。

`git diff --check`、文件連結檢查與腳本三種模式 DryRun 均通過。提交前自查核對共享鎖、讀取快照時機、執行開始 suspend 回呼、核准撤銷與取消傳遞；沒有將等待中的裝置驗證記為完成。

## 重跑方式

JDK 21、既有 Android SDK；不更動專案預設 heap。完整 JVM 與 APK／Lint 分階段執行：

```powershell
$heap = '-Dorg.gradle.jvmargs=-Xmx3072m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=256m -XX:NonNMethodCodeHeapSize=32m -Dfile.encoding=UTF-8'
.\gradlew.bat testDebugUnitTest $heap --no-daemon --max-workers=1 --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug $heap --no-daemon --max-workers=1 --console=plain
```

驗證腳本已增加儲存、工具批次、核准、重試及壓縮測試，Focused 也沿用單次 3GB heap。Focused／Full／Device 三個 DryRun 都成功，不安裝應用程式。

## 文件與限制

新增 docs 索引、架構概覽、聊天生命週期、Agent 指南、測試指南及 ADR，更新聊天鏈路與修改導航。歷史壓縮指導保留原始時間背景，頁首先指向現行規格；AI 模組 README 不再要求已過時的 MNN 建置。新文件的本地 Markdown 連結已驗證存在。

目前 adb 沒有在線裝置，因此本輪未執行 instrumentation、實機 UI／通知、背景存活或真實模型請求；來源分支的裝置報告是歷史證據。本輪工具批次 JVM 新測試覆蓋非執行分支與核准重查，不能取代 Android 平台效果與實際工具副作用的裝置驗證。未使用付費 API、修改正式套件或使用者資料。
