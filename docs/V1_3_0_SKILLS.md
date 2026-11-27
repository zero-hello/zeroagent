# 外部開發工具來源參考

下表保留先前使用的第三方開發工具來源與固定 commit，供來源追溯；不代表目前環境已安裝、已執行或已驗證。現行介面規範與元件請查閱 [UI 元件導航](engineering/UI-UX-POLISH.md)。

| Skill | 來源 | Commit |
|---|---|---|
| frontend-design | https://github.com/anthropics/skills | `dbd4588f9e1033efb41dad4bef2f7947c8993d44` |
| ui-ux-pro-max | https://github.com/nextlevelbuilder/ui-ux-pro-max-skill | `56ea9cf174ed86786187daa9de0e6247cb0747e4` |
| impeccable | https://github.com/pbakaus/impeccable | `0665bd812ae0908fcab17b96fd4f840defb882d9` |
| extract-design-system | https://github.com/arvindrk/extract-design-system | `1873741ba8dea755e35e6e15134f7918cd58e036` |
| material-design-3-ui-skill | https://github.com/skydashnet/material-design-3-ui-skill | `82783f76de4a5b08a60421cae935cf8de7ca1ee8` |
| material-3-expressive | https://github.com/JusDots-Devs/m3e-skill | `07bd88c28ea717d7522e57d4906c333610223d90` |

採用 Android／Compose／Material3／Material 3 Expressive 指引，精確 API 以專案固定版本的官方 AndroidX 原始碼為準；不將 Web 專用 Skill 的 CSS 範例搬進 Compose，也未因安裝網站擷取 Skill 而執行網站擷取或下載瀏覽器。

本次 Material3 版本 `1.5.0-alpha27` 已由 Google Maven artifact 與官方 source jar 核對；保留既有 Kotlin 2.4.10／AGP 9.3.1／SDK37，沒有為了風格升級工具鏈。

