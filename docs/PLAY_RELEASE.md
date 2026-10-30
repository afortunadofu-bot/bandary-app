# Bandary 上架 Google Play 手冊

最後更新：2026-09-14（對應 v1.67.0）。同內容的圖文版在「聽團曆上架手冊」網頁，本檔為版控中的文字版。

- **套件名稱**：`com.bandary.app`（建立應用程式時必須完全一致，之後不能改）
- **隱私權政策**：https://bandary.web.app/privacy
- **刪除帳號說明頁**：https://bandary.web.app/delete-account

---

## 程式端已完成

| 項目 | 狀態 |
|---|---|
| `targetSdk` / `compileSdk` | **36**（Google Play 自 2026-08-31 起要求新 App 與更新都要 API 36） |
| 版號 | 只手寫 `appVersionName`，`versionCode = major*10000 + minor*100 + patch`（1.67.0 → 16700），每次上傳自動遞增 |
| Release 建置 | R8 混淆 + 資源縮減；`signingConfigs.release` 讀根目錄 `keystore.properties` |
| 付費 | Google Play Billing 已接好；商品 ID 在 `BillingManager.ProPlan`（見「付費商品」） |
| 刪除帳號 | App「我的 → 刪除帳號」、網頁版「我的 → 刪除帳號」、公開說明頁 `/delete-account`；實際刪除由 Cloud Function `deleteMyAccount` 執行 |
| Spotify 金鑰 | 已從 App 移除，改由 Cloud Function `spotifySearch` 代理，金鑰存 Secret Manager |
| 權限 | 已移除 `USE_EXACT_ALARM`；`usesCleartextTraffic=false`、`allowBackup=false` |
| 隱私權政策 / 說明 | `web/privacy.html`、`web/help.html`、`web/delete-account.html` 已上線 |

---

## 流程總覽

1. 開發者帳號（已完成）→ 確認帳號類型與付款設定檔
2. 部署後端（Spotify 金鑰輪替 → Secret → Functions → Hosting）
3. 產生正式簽章金鑰
4. 打包 AAB
5. Play Console 建立應用程式
6. 上傳到內部測試，啟用 Play App Signing，把 SHA-1 加進 Firebase
7. 填 App 內容（政策表單）
8. 填商店資訊
9. 建立付費商品
10. 封閉測試（個人帳號：12 人、連續 14 天）
11. 申請正式版存取權 → 送審 → 上架
12. 上架後收尾

---

## 1. 開發者帳號（已完成，確認兩件事）

- **帳號類型**：Play Console → 設定 → 開發人員帳戶。
  - **個人帳號且 2023-11-13 之後建立**：正式上架前必須跑封閉測試，**至少 12 位測試人員、連續加入 14 天**，才能申請正式版存取權。中途退出又重新加入的人，14 天要重算。現在就可以開始找人。
  - 機構帳號、或 2023-11-13 前建立的個人帳號不受此限。
- **付款設定檔**：要收錢（Pro 商品）必須在 Play Console → 設定 → 付款設定檔 填銀行與稅務資料。沒有這個只能上架免費 App、無法建立付費商品。
- 個人帳號需完成身分驗證（可能要幾天），並驗證聯絡電話。

## 2. 部署後端

在專案根目錄執行。

1. **輪替 Spotify 金鑰**：舊的 Client Secret 曾寫在 App 原始碼與 git 紀錄裡，視為已外洩。到 https://developer.spotify.com/dashboard → 這支 App → Settings → 重新產生 Client Secret。
2. **把金鑰存進 Secret Manager**（指令會要你貼上值，不會出現在任何檔案裡）：
   ```
   firebase functions:secrets:set SPOTIFY_CLIENT_ID
   firebase functions:secrets:set SPOTIFY_CLIENT_SECRET
   ```
3. **部署 Functions**（會新增 `spotifySearch`、`deleteMyAccount`）：
   ```
   npm --prefix functions install
   firebase deploy --only functions
   ```
4. **部署網頁**（刪除帳號頁與網頁版刪除入口）：
   ```
   firebase deploy --only hosting
   ```
5. 驗證：打開 https://bandary.web.app/delete-account 能看到說明頁。

## 3. 產生正式簽章金鑰（只做一次，務必備份）

```
keytool -genkey -v -keystore bandary-release.keystore -alias bandary -keyalg RSA -keysize 2048 -validity 10000
```

- `bandary-release.keystore` 放專案根目錄。
- 複製 `keystore.properties.template` 為 `keystore.properties` 填入密碼與 alias。兩個檔都已在 `.gitignore`，**不要 commit**。
- 這把是「上傳金鑰」。啟用 Play App Signing 後，真正簽給使用者的金鑰由 Google 保管；上傳金鑰遺失可以向 Google 申請重設，但仍請備份到雲端硬碟或密碼管理工具。

## 4. 打包 AAB

```
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew.bat bundleRelease
```

- 產物：`app/build/outputs/bundle/release/app-release.aab`
- 確認 `app/google-services.json` 是正式 Firebase 專案（`bandary-e5547`）。

## 5. 建立應用程式

Play Console → 建立應用程式：

- 應用程式名稱：`聽團曆 Bandary`
- 預設語言：中文（台灣）– zh-TW
- 類型：應用程式；免費或付費：**免費**（App 內購買另外設定，不是付費 App）
- 勾選開發人員計畫政策與美國出口法聲明

## 6. 內部測試、App Signing、Firebase SHA-1

1. 測試與發布 → 測試 → **內部測試** → 建立新版本 → 上傳 AAB。第一次上傳時選擇「使用 Google 產生的應用程式簽署金鑰」（Play App Signing）。
2. 測試人員：建立電子郵件清單，加入自己的 Google 帳號，複製加入連結在手機上開啟並安裝。
3. **Firebase SHA-1（沒做 Google 登入會失敗）**：Play Console → 測試與發布 → 設定 → **應用程式完整性** → 應用程式簽署。複製兩組憑證的 SHA-1 與 SHA-256：
   - 應用程式簽署金鑰憑證（使用者從 Play 安裝的版本用這個）
   - 上傳金鑰憑證
   
   到 Firebase Console → 專案設定 → 你的應用程式（com.bandary.app）→ 新增指紋，兩組都加。完成後重新下載 `google-services.json` 放回 `app/`（下次打包用）。
4. 在內部測試版確認：Google 登入、雲端備份與還原、通知、Spotify 歌曲搜尋、刪除帳號（用測試帳號）都正常。

## 7. App 內容（政策 → 應用程式內容）

| 表單 | 填法 |
|---|---|
| 隱私權政策 | `https://bandary.web.app/privacy` |
| 應用程式存取權 | 「部分功能需要登入」。說明：使用任何 Google 帳號登入即可使用全部功能，不需要邀請碼。**建議提供一組審核用 Google 測試帳號**，避免審核員因登入流程卡住。 |
| 廣告 | 否，不含廣告 |
| 廣告 ID | **是**（2026-09-15 決定先保留）。用途勾「數據分析」：Firebase Analytics 會讀取廣告 ID（合併後的 manifest 含 `AD_ID`）；App 本身不顯示廣告，所以上一列「廣告」仍填否。資料安全性的「裝置或其他 ID」用途要含「數據分析」 |
| 內容分級 | 類別選「公用程式、生產力、通訊或其他」；使用者可互動（好友、投稿活動）如實勾選；通常會得到「普遍級」 |
| 目標對象 | 13 歲以上（不要勾 13 歲以下，否則會進入兒童政策審查） |
| 新聞應用程式 | 否 |
| 政府應用程式 | 否 |
| 金融功能 | 否（App 內購買不算） |
| 健康 | 否 |
| **刪除帳號** | 可在 App 內刪除：是。網址：`https://bandary.web.app/delete-account` |
| 資料安全性 | 見下表 |

### 資料安全性

總題：
- 是否收集或分享使用者資料：**是**
- 傳輸時是否加密：**是**（全程 HTTPS / Firebase）
- 使用者能否要求刪除資料：**是**（App 內與網頁皆可）

收集的資料（**分享給第三方：全部選「否」**——Firebase / Google 屬於代你處理資料的服務供應商，不算分享）：

| 資料類型 | 項目 | 是否必要 | 用途 |
|---|---|---|---|
| 個人資訊 | 電子郵件地址 | 必要（登入） | 帳戶管理、應用程式功能 |
| 個人資訊 | 姓名（暱稱 / Google 顯示名稱） | 選填 | 應用程式功能（好友、排行榜） |
| 相片和影片 | 相片 | 選填 | 應用程式功能（封面、雲端備份） |
| 應用程式活動 | 其他使用者產生的內容（演唱會紀錄、選歌、心得、活動投稿、意見回饋） | 必要 | 應用程式功能 |
| 應用程式活動 | 應用程式互動 | 必要 | 數據分析（Firebase Analytics） |
| 裝置或其他 ID | 裝置或其他 ID（推播權杖、Analytics 執行個體 ID） | 必要 | 應用程式功能（通知）、數據分析 |
| 日曆 | 日曆活動 | 選填（連結 Google 日曆才會用） | 應用程式功能 |

注意：新增演出會自動把公開活動資訊送去活動頁審核（可在「我的」關閉），屬於上表「使用者產生的內容」的應用程式功能用途，隱私權政策已寫明。

### 權限說明（審核若詢問）

- `POST_NOTIFICATIONS`：演出、開賣提醒
- `SCHEDULE_EXACT_ALARM`：準時的開賣提醒；未授權時自動改用不精確鬧鐘
- 相片：透過系統相片挑選器選封面，不需要讀取整個相簿

## 8. 商店資訊（拓展 → 主要商店資訊）

素材都在 `docs/`：

| 欄位 | 規格 | 檔案 / 內容 |
|---|---|---|
| 應用程式名稱 | 30 字以內 | 聽團曆 Bandary |
| 簡短說明 | 80 字以內 | 見 `docs/PLAY_STORE_COPY.md` |
| 完整說明 | 4000 字以內 | 見 `docs/PLAY_STORE_COPY.md` |
| 應用程式圖示 | 512×512 PNG | `web/icon-512.png`（已確認 512×512） |
| 主打圖片 | 1024×500 PNG/JPG | `docs/play-feature-graphic.png` |
| 手機截圖 | 2–8 張，邊長 320–3840px，**長邊不能超過短邊 2 倍** | **尚未準備**：用實機截圖，照 `docs/store-screenshots/README.md` 的清單拍，再跑 `compose.py` 輸出成 1080×1920 |
| 類別 | — | 音樂與音訊（或娛樂） |
| 聯絡電子郵件 | 必填，會公開顯示 | 開發者信箱 |
| 網站 | 選填 | https://bandary.web.app |

截圖必須是 App 的真實畫面（Play 政策：宣傳素材不得誤導實際功能）。先前 `docs/` 裡的四張是手繪示意稿、資料與版面都跟真實 App 不符，已移除，不要再用。

## 9. 付費商品（營利 → 產品）

**要先有一版含 Billing 的 AAB 上傳到任一測試軌道，這頁才會解鎖。商品 ID 建立後不能修改、不能刪除。**

| 位置 | 商品 ID | 類型 | 建議價格 |
|---|---|---|---|
| 應用程式內商品 | `bandary_pro_lifetime` | 一次性 | NT$149 |
| 訂閱 | `bandary_pro_yearly` | 訂閱，基本方案 1 年、自動續訂 | NT$79 / 年 |
| 應用程式內商品 | `bandary_proplus_lifetime` | 一次性 | NT$299 |
| 訂閱 | `bandary_proplus_yearly` | 訂閱，基本方案 1 年、自動續訂 | NT$159 / 年 |

- 每個商品都要按「啟用」，草稿狀態買不到。
- 免費試用（選填）：在訂閱的基本方案底下新增「優惠方案」→ 免費試用，App 會自動選用。
- 設定 → **授權測試** → 加入自己的帳號，測試購買不會實際扣款。
- 價格隨時可在 Console 改，App 顯示的是 Play 回報的價格，不用改版。

## 10. 封閉測試（個人帳號必做）

1. 測試與發布 → 測試 → **封閉測試** → 建立測試軌道 → 上傳同一版（或更新版）AAB。
2. 測試人員用 Google 群組或電子郵件清單，至少 **12 人**；把加入連結給他們，每個人都要按「成為測試人員」並從 Play 安裝。
3. 從第 12 人加入的那天起算，**連續 14 天**不能有人退出低於 12 人。建議找 15–20 人留緩衝。
4. 期間持續更新版本、回覆回饋，申請正式版時會被問測試期間的互動情況。

## 11. 申請正式版並送審

1. 資訊主頁 → 申請正式版存取權 → 填問卷（測試人員怎麼招募、收到哪些回饋、改了什麼、App 為何已準備好）。審核約 7 天內回覆。
2. 通過後：測試與發布 → **正式版** → 建立新版本 → 選 AAB → 國家 / 地區（台灣，要擴大再加）→ 送審。
3. 首次正式版審核通常數天，被退件會寫明原因，修正後重送即可。

## 12. 上架後

- ~~介紹頁 `web/about.html` 的「Android App 準備中」按鈕~~ — v1.117.2（2026-10-30）已換成 Play 商店網址並移除 `aria-disabled`。**還需要 `firebase deploy --only hosting` 才會生效。**
- 後台 Firestore `config/version` 公告最新版號（App「我的 → 最新版本」會讀）。
- 每次更新：改 `appVersionName` → README 與 App 內更新紀錄補一筆 → `bundleRelease` → 上傳。

---

## Google 日曆（OAuth）

App 使用 Google Calendar API 讀寫使用者日曆，屬於**敏感權限範圍**。

- OAuth 同意畫面在「測試中」狀態時，只有列入測試使用者名單（最多 100 人）的帳號能連結日曆，其餘人連結會失敗，但不影響其他功能。
- 要開放所有人使用：Google Cloud Console → API 和服務 → OAuth 同意畫面 → 發布應用程式 → 送 Google 驗證。需要：隱私權政策網址、首頁網址、授權網域所有權驗證、示範影片（展示登入與日曆功能如何使用資料）、使用理由說明。驗證可能需要數週。
- 建議：先上架，日曆功能維持測試狀態；上架後再送 OAuth 驗證。

## 政策風險備忘

- **小額贊助連結**：`DONATE_URL` 目前以 `SHOW_DONATE=false` 隱藏。導向站外金流可能違反付款政策，送審前維持隱藏。
- **購買驗證**：目前只在裝置端驗證購買，未做伺服器端收據驗證。不影響上架，日後可在 Cloud Functions 加 Play Developer API 驗證。
- **ML Kit 中文 OCR** 為內建模型，安裝檔較大；AAB 會依裝置分割，實際下載量較小。
