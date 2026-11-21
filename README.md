# ZeroAgent

ZeroAgent 是由 Zero 維護的 Android AI 助理。你可以在手機上切換 AI 模型、整理對話與文件，並使用模型支援的圖片或語音功能。

## 下載與安裝

目前正式版為 **v2.0.0**，需要 **Android 12 或更新版本**。

| 裝置 | APK |
| --- | --- |
| 多數 Android 手機與平板 | [arm64-v8a](https://github.com/zero-hello/zeroagent/releases/download/v2.0.0/ZeroAgent-v2.0.0-arm64-v8a.apk) |
| 使用 x86_64 系統映像的 Android 模擬器 | [x86_64](https://github.com/zero-hello/zeroagent/releases/download/v2.0.0/ZeroAgent-v2.0.0-x86_64.apk) |

1. 依裝置架構下載一個 APK。每個 APK 都可獨立安裝。
2. 開啟下載完成的檔案。若 Android 顯示安裝來源提示，請依系統畫面決定是否允許。
3. 開啟 ZeroAgent，進入供應商設定，選擇模型並依需要填入自己的 API 金鑰。

[所有版本與 SHA-256 校驗碼](https://github.com/zero-hello/zeroagent/releases)

## 可以用 ZeroAgent 做什麼

- **聊天與搜尋** · 在對話中切換模型，整理及搜尋過往對話。需要近期資訊時，可使用已設定的搜尋服務。
- **圖片與語音** · 依所選供應商、模型和帳戶支援情況生成圖片、進行即時語音對話或朗讀文字。
- **文件與記憶** · 將文件加入專案後詢問內容，並管理助理保存的長期記憶。
- **本地模型與工具** · 在相容裝置上設定本地模型，或從對話開啟內建終端機，使用 Android 提供的指令。

- **聊天操作** · 長按訊息可引用、編輯、重新生成或刪除。聊天草稿會按聊天室保存，也可開啟無痕對話。
- **手機整合** · 支援 Android 分享選單、快速設定方塊與桌面圖示捷徑。裝置狀態、小紙條和截圖提示可在設定開關。跨 App 截圖提示需要浮窗權限，並由你指定截圖資料夾。
- **更新與搬移** · 可在 App 檢查更新並開啟 Android 安裝畫面。換機前可匯出單一備份檔。需要退回舊版時，可匯出先前保留的 APK。請先備份資料，再依 Android 的安裝流程操作。

## 權限與資料

使用雲端模型時，問題和所附的資料會傳送至你選擇的供應商。額度、費用與資料處理方式依供應商而異。相機、螢幕擷取和裝置控制會依功能使用 Android 的權限。你可以在設定查看及調整工具授權。Root 屬於選用功能，須由裝置上的 Root 管理工具授權。

## 回報問題

請提供 ZeroAgent 版本、Android 版本、裝置型號及重現步驟。貼上截圖或錯誤訊息前，請先移除金鑰、對話及個人資料。[回報問題或提出建議](https://github.com/zero-hello/zeroagent/issues/new/choose)

## 授權

ZeroAgent 以 RikkaHub 為基礎，依 GNU AGPL 3.0 發布。公開原始碼與第三方授權可從對應發行頁查閱。部分早期發行頁未附可確認的對應原始碼。

[授權全文](LICENSE) · [上游專案](https://github.com/rikkahub/rikkahub)
