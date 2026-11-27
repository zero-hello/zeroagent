# PRoot 二進位來源查核

查核日期：2026-11-12。沒有執行二進位或重新編譯，也未修改 APK 建置副本。

## 已證實的取得來源

目前 `workspace/src/main/jniLibs/{arm64-v8a,x86_64}/libproot_{exec,loader}.so` 四個檔案，均與下列上游 commit 的 raw 下載內容逐 byte 相同：

https://github.com/rikkahub/rikkahub/commit/f4508dfac2255cf83e75859a8fe37dd7da6778a3

執行檔最後修改於該 commit（2026-08-24），loader 由更早的 workspace 合併 commit `ead966334c9ade5a467cd1d833faac05b3aa8930`（2026-06-14）繼承。`PROVENANCE.json` 記錄逐檔原始下載 URL、長度與 SHA-256；本地檔案也與 HEAD Git blob 相同。這確認了二進位從哪個上游版本取得，但不是對應原始碼與建置過程的證明。

兩個 exec 的可列印字串包含 `5.1.107.92` 與 `Copyright (C) 2015 STMicroelectronics, licensed under GPL v2 or later.`。四個檔案的 ELF 沒有找到 GNU build-id。`.comment` 有 Android clang 21.0.0／LLD 21.0.0；exec 另含 clang 19.0.1。exec 的 DT_NEEDED 只有 liblog.so、libc.so，loader 沒有 DT_NEEDED；這些都是靜態 metadata，不能用來推定完整來源或補丁。

## 已取得的參考來源，尚未證實與二進位對應

- Termux PRoot tag `v5.1.107.92` 實際指向 commit `7266fb3e8516535682f5a9c8f3a7e70f6506eddb`：https://github.com/termux/proot/tree/7266fb3e8516535682f5a9c8f3a7e70f6506eddb
- Termux 套件 recipe commit `08b49b3ce00b1e14a3a0365200f30e50f8dfafe1`：https://github.com/termux/termux-packages/tree/08b49b3ce00b1e14a3a0365200f30e50f8dfafe1/packages/proot 。該目錄只有 build.sh 與 termux-chroot；此份 recipe 沒有同目錄額外 patch。`build.sh` 保存原始 bytes。
- recipe 的 source archive SHA-256 為 `29385d1ddb619a9c4449ab512bfd55032034b22f724ddf98fc95ff300ea32135`。實際下載 tag archive 後校驗一致，記於 `archive-verification.txt`；`COPYING` 從該 archive 原樣取出。
- recipe 宣告 libandroid-shmem、libtalloc 依賴，設置 ARG_MAX=131072、VERSION、PROOT_WITH_LIBANDROID_SHMEM、PROOT_UNBUNDLE_LOADER。這是可查得的同版本建置參考，沒有證據證明是目前二進位真正使用的 recipe。

## 仍缺的證據

1. 上游提供目前這四個確切二進位的對應來源 commit、所有補丁、完整建置指令與環境。
2. 實際納入的 libtalloc、libandroid-shmem 等依賴版本、對應來源、編譯與連結方式及應保留聲明。
3. 產物與來源的建置證明、原始發行校驗清單或可重現比對。相同版本字串和來源 archive 校驗，無法排除額外補丁或不同依賴。

因此本次只能確認「上游二進位取得來源」與「同版本參考來源」，不能宣稱已完成 PRoot 對應來源與授權封裝查核。

## 可執行的補齊方案

優先取得上游對上述四個 SHA-256 的來源與建置資料，核對後將來源 commit、patch、依賴、授權與 build recipe 一併固定。

若上游無法補出證據，改由專案固定經審閱的 PRoot source tag/commit、所有依賴及工具鏈，保存完整 patch／recipe／原始授權，重新產生 arm64-v8a 與 x86_64 執行檔及 loader，再完成產物校驗與實機 PTY／中斷／Rootfs 測試。此方案會替換 native 執行環境，不能只藉新增 COPYING 或改檔名視為完成。本次沒有啟動這項新建置。
