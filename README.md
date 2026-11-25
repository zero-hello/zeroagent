# ZeroAgent

ZeroAgent 是由 Zero 維護的 Android AI 助理。你可以使用自己的模型服務，或下載相容的本機模型。支援文字聊天、附件、即時語音、記憶管理與內建終端機。

**目前版本 v2.4.0 · Android 12 以上**

[下載完整 APK](https://github.com/zero-hello/zeroagent/releases/latest) · [AGPL-3.0 授權](LICENSE) · [來源與第三方聲明](SOURCE_NOTICE.md)

## 開始使用

1. 從發行頁選擇 `arm64-v8a` 或 `x86_64` APK，依 Android 指示安裝。
2. 在「設定 → 供應商」連線到模型服務，或在本機模型頁瀏覽 Hugging Face 的公開 GGUF 模型。
3. 回到聊天選擇模型。你可以貼上文字或圖片、加入相容附件，或使用 Android 的分享選單。

## 聊天與記憶

- AI 回覆逐段浮現。可在「設定 → 趣味功能」開啟貓咪掌印。Android 移除動畫時，文字直接顯示。
- ZeroAgent Lab 提供記憶抽屜。啟用後可查看、修改或刪除記憶。關閉抽屜會保留既有資料。
- 可選的情境記憶使用本機 Embedding 模型。下載時會顯示已下載大小與總大小。相關記憶片段可能隨聊天請求交給你選擇的模型服務。

## 模型與工具

- 支援多家模型服務與自訂 API。可用模型、費用、推理與圖片功能依供應商、帳戶以及模型而異。
- 公開網頁可排版後讀取，支援圖片的模型可查看首屏預覽。需要登入或由頁面腳本產生的內容可能無法讀取。
- 裝置診斷工具可讀取 Android 版本、記憶體、省電與溫度等級、網路狀態和顯示設定。精簡裝置狀態只在相關聊天中使用。詳見 [對話規範與 Gemini 安全設定](docs/CHAT_POLICY.md)。
- 本機模型頁可搜尋 Hugging Face 的 GGUF 目錄並逐頁瀏覽。相容檔案、記憶體與裝置架構會影響安裝與執行。
- 內建終端機使用 Android 系統 Shell。Root、Shizuku 與手機工具須依 Android 或對應權限管理程式授權。

## 更新與安裝

下載更新時可在 App 內查看進度。允許通知後，也可在通知列查看。ZeroAgent 會驗證更新的套件與簽章，再交給 Android 安裝。發行頁提供完整 APK 與 SHA-256 校驗檔。

## 從原始碼建置

在 GitHub 選擇 **Fork**，下載你的副本。使用 JDK 21、Gradle Wrapper 和專案指定的 Android SDK。一般開發可先建置可偵錯版本。

```sh
./gradlew :app:assembleDebug
```

如果使用 Git 子模組，先執行 `git submodule update --init --recursive`。發行自己的 APK 時，請使用自己的簽章，並透過 `ZEROAGENT_SIGNING_PROPERTIES` 指向倉庫外的簽章設定。不同簽章無法直接覆蓋安裝，切換前請先備份資料。

ZeroAgent 採 [GNU Affero General Public License v3.0](LICENSE)。第三方元件沿用各自的授權。
