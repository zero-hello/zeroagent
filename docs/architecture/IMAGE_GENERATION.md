# 圖片生成能力與請求

核對日期：2026-11-12。這是實作依據，不代表已呼叫付費服務或通過裝置驗證。

- `ImageCapabilities.kt` 統一檢查供應商協定、啟用狀態、模型圖片輸出、圖片編輯的輸入能力及實際已實作端點。Cohere、Claude、AICore、Codex 與 Gemini OAuth 不會因手動標記圖片能力而出現假的生成入口。
- OpenAI 官方已在 2026-05-12 關閉 DALL·E 2/3，並於 2026-10-23 關閉 gpt-image-1。只在 api.openai.com 排除這些官方退役 ID；不替第三方相容端點臆測相同退役政策。
- 第三方仍使用明確 DALL·E 2/3 ID 時採用各自已公開的尺寸、數量與 base64 回傳格式；DALL·E 3 每次一張，多張以分次請求完成。GPT Image 不送 response_format。未知相容模型只送 model、prompt 與使用者明確設定的自訂欄位，不猜尺寸或數量能力。
- 工具與直接生成畫面都先檢查模型，失效的指定模型不會偷偷換成其他模型扣額度。生成後需能解碼為圖片，才寫入完成結果。預覽與匯出沿用 ImagePreviewDialog、FilesManager 的實際儲存流程。

官方依據：

- https://developers.openai.com/api/reference/python/resources/images/methods/generate
- https://developers.openai.com/api/docs/deprecations
- https://developers.openai.com/cookbook/articles/what_is_new_with_dalle_3
