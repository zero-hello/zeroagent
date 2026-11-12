# ZeroAgent 原始碼與授權聲明

ZeroAgent 由 Zero 維護。本產品修改自既有 AGPL-3.0 原始碼，匯入基準 commit 為 c6525a3b4f7611fca2ab965c9537d326ee4d8f9c；來源沿革另保留於開發用 Git metadata 與受保護的來源備份。

原始著作權聲明與第三方授權仍保留在各自的來源檔和授權文件中。LICENSE 是未修改的 GNU AGPL 第 3 版原文。產品名稱與維護者資訊不代表原始著作權轉移，也不宣稱 Zero 是所有原始程式碼的作者。此文件不主張另有書面授權或權利轉讓協議。

目前產品版本為 ZeroAgent v1.9.4（versionCode 24）。第三方依賴的下載座標與舊資料辨識碼可能保留原有識別字，以確保可重現建置與資料相容；它們不是 ZeroAgent 的產品品牌或官方雲端服務。

## 產品功能與第三方邊界

獨立系統語音助理、背景喚醒與產品 Web 伺服器已退役。一般原生語音通話、TTS、AI API 網路連線及 OAuth 本機回呼仍有各自用途；舊資料的相容處理不代表重新提供已移除功能。

內建工作區使用 PRoot 與獨立下載的 Linux rootfs，不要求安裝 Termux App，也不將其描述為 Termux 官方產品。PRoot、rootfs 套件、模型權重與其他隨附或下載元件仍須保留其各自來源與授權；產品名稱及本聲明不會取代第三方授權條款。實際隨 APK 封裝及環境下載的檔案以建置設定與來源清單為準。


## 情境記憶的第三方元件

- SQLite 3.54.1 官方 amalgamation：<https://www.sqlite.org/2026/sqlite-amalgamation-3540100.zip>，公有領域；原始標頭保留於 native memory/vendor。
- sqlite-vec v0.1.9：<https://github.com/asg017/sqlite-vec>，MIT／Apache-2.0；MIT 授權附於 native memory/vendor。此處 VSS 代表 Vector Similarity Search，使用 vec0 進行真正 SQLite 餘弦 KNN。
- multilingual-e5-small：<https://huggingface.co/intfloat/multilingual-e5-small>，MIT；下載版為 TwinSunsLLC 的 Q8_0 GGUF，固定 revision 與 SHA-256 寫入 MemoryManager。模型權重獨立下載，不包含在 APK 或原始碼庫。


## PRoot 原生產物的來源證據

四個既有 PRoot exec／loader 原生檔案已逐 byte 核對上游 rikkahub commit `f4508dfac2255cf83e75859a8fe37dd7da6778a3` 的 raw 產物。逐檔 URL 與 SHA-256，以及同版本 Termux source tag／recipe 的查核資料保存在 `docs/licenses/proot-reference/`。目前只能確認二進位取得來源，尚不能確認該 binary 的完整對應原始碼、補丁、依賴及建置過程；本聲明和上游授權原文不代表已完成這部分授權封裝查核。
