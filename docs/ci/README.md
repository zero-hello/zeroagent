# ZeroAgent CI 參考範本

此目錄保留建置 workflow 的來源範本，本次不啟用 GitHub Actions。正式 APK 在本機低並行測試建置並完成驗證後才發布。

若日後要啟用，需具備 GitHub workflow 權限，先檢查簽章 Secrets 與 Actions 額度，再將 build.yml 放回 .github/workflows。不要把正式私鑰或密碼提交到 Git。
