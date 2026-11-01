# ZeroAgent 原始碼與授權聲明

ZeroAgent 由 Zero 維護。本產品修改自既有 AGPL-3.0 原始碼，匯入基準 commit 為 c6525a3b4f7611fca2ab965c9537d326ee4d8f9c；來源沿革另保留於開發用 Git metadata 與受保護的來源備份。

原始著作權聲明與第三方授權仍保留在各自的來源檔和授權文件中。LICENSE 是未修改的 GNU AGPL 第 3 版原文。產品名稱與維護者資訊不代表原始著作權轉移，也不宣稱 Zero 是所有原始程式碼的作者。此文件不主張另有書面授權或權利轉讓協議。

ZeroAgent v1.0.7 修改日期：2026-10-17。第三方依賴的下載座標與舊資料辨識碼可能保留原有識別字，以確保可重現建置與資料相容；它們不是 ZeroAgent 的產品品牌或官方雲端服務。


## 情境記憶的第三方元件

- SQLite 3.54.1 官方 amalgamation：<https://www.sqlite.org/2026/sqlite-amalgamation-3540100.zip>，公有領域；原始標頭保留於 native memory/vendor。
- sqlite-vec v0.1.9：<https://github.com/asg017/sqlite-vec>，MIT／Apache-2.0；MIT 授權附於 native memory/vendor。此處 VSS 代表 Vector Similarity Search，使用 vec0 進行真正 SQLite 餘弦 KNN。
- multilingual-e5-small：<https://huggingface.co/intfloat/multilingual-e5-small>，MIT；下載版為 TwinSunsLLC 的 Q8_0 GGUF，固定 revision 與 SHA-256 寫入 MemoryManager。模型權重獨立下載，不包含在 APK 或原始碼庫。
