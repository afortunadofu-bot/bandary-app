# Bandary Cloud Functions

## `notifyOnAnnouncement`
當 Firestore `announcements/{id}` 新增一筆（管理員在 App「發送官方通知」時），透過 FCM
推播給所有訂閱 `announcements` topic 的裝置，即使 App 沒開也會跳系統通知。

## `imageSearch`
找海報用的 Google Custom Search 代理。App 端沒有任何金鑰。
只給登入者呼叫（否則它就是一個公開的免費圖片搜尋 API）。

## `registerInvite` / `redeemInvite`
邀請碼。碼是 uid 的**單向雜湊**（見 App 的 `InviteCode.kt`），從碼反推不回 uid，
所以需要一份索引 `invite_codes/{code} → uid`：

- `registerInvite`：登入後呼叫一次，**在伺服器端用 `request.auth.uid` 重算**邀請碼再寫索引。
  一定要重算而不是讓 App 把碼送上來——不重算就無法驗證那組碼屬於呼叫者，
  等於誰都能先去佔走別人的碼、把推薦功勞偷走。
- `redeemInvite`：兌換朋友的碼，雙方各拿 14 天試用期，每個帳號限一次
  （`invite_redemptions/{uid}`）。先寫「兌換過了」再發獎勵——順序反過來的話，
  發完獎勵但記錄寫失敗就會變成可以無限重領。

`invite_codes` / `invite_redemptions` / `promo_rewards` 都只由 Admin SDK 寫入。
`firestore.rules` 最後的 catch-all 是 deny，Admin SDK 不受規則限制，
所以**這兩支不需要部署任何規則**。

⚠️ `functions/index.js` 的 `inviteCodeOf()` 與 App 的 `InviteCode.of()` 必須完全一致。
改任何一邊，所有既有邀請碼都會靜悄悄失效（查不到 uid → 使用者看到「找不到這組邀請碼」，
看起來像自己打錯）。`InviteCodeTest` 釘了三組跨實作的期望值，改壞會先紅。

## `spotifySearch`
Spotify 搜尋代理。同樣只給登入者呼叫。

## `crawlScheduled` / `crawlNow`
活動蒐集。排程每天台北 06:00 跑一次；`crawlNow` 給管理員在
App 的「設定 → 管理者 → 活動蒐集來源」手動觸發。
兩者都只讀 `crawl_sources`（管理員自己加的來源），抓到的一律寫成待審。

## 維基百科巡演匯入
在「活動蒐集來源」加一筆網址是 `https://zh.wikipedia.org/wiki/<條目>` 的來源就會生效
（靠網域自動判斷，`type` 不用填）。排程會去 MediaWiki API 拿 wikitext、
解析巡演表格（場次 / 日期 / 城市 / 國家地區 / 舉辦場館），寫成**待審**的 `free_events`。

- **歷史場次不會被丟掉**：其他來源套「只收今天以後」，維基來源不套——
  這條來源的重點本來就是過去的場次。
- 單一條目最多收 200 場。
- 適合的條目：各藝人的「演唱會列表」、各巡演的專屬條目。
- 授權：日期 / 城市 / 場館是**事實**，不受著作權保護；敘述文字不碰。每筆記 `sourceUrl`。

## 金鑰（Secret Manager）
金鑰一律**不寫進原始碼**，也不寫進任何設定檔——App 可以被反編譯，
git 歷史也是公開的。下面每一行都會互動式地問你要貼什麼：

```bash
firebase functions:secrets:set SPOTIFY_CLIENT_ID
firebase functions:secrets:set SPOTIFY_CLIENT_SECRET
firebase functions:secrets:set CSE_API_KEY
firebase functions:secrets:set CSE_CX
# 設定完要重新部署，函式才會拿到新值：
firebase deploy --only functions
```

### ⚠️ 搜尋引擎只能用「指定網站」模式
Google 從 **2026-01-20** 起，新建的搜尋引擎一律不能開「搜尋整個網路」；
既有的開著才能繼續用到 2027-01-01，而且**一旦關掉就再也打不開**。
本專案的既有引擎沒有開，所以只剩指定網站（最多 50 個網域）這條路。

對找海報來說這不算差——演唱會海報本來就集中在售票平台，限定網域反而比全網乾淨
（不會搜到同人圖與轉貼）。建議貼進「要搜尋的網站」：

```
kktix.com
tixcraft.com
ibon.com.tw
kham.com.tw
accupass.com
indievox.com
opentix.life
year.ticket.com.tw
zh.wikipedia.org
```

加進去之前請自行確認各站的使用條款。另外搜尋字串刻意只補「演唱會」不補「海報」：
這些站上每一頁本來就是演唱會，而頁面文字幾乎不會出現「海報」兩個字，
多加這個詞只會把本來找得到的圖濾掉。

### 整條路 2027-01-01 會停
Custom Search JSON API 已不收新客戶且當天停止服務。找海報的第 2～3 層屆時要移除；
第 1 層（活動資料庫）與第 4 層（Spotify 藝人照）不受影響，
所以替代方案就是把活動資料庫養肥——爬蟲來源與維基匯入做的就是這件事。

`CSE_API_KEY` / `CSE_CX` 來自 Google Custom Search：
1. Cloud Console 啟用「Custom Search API」並建一把 API key
2. programmablesearchengine.google.com 建一個搜尋引擎（開啟「圖片搜尋」+「搜尋整個網路」），複製 CX id

額度：免費方案一天 100 次，**全部使用者共用這一把金鑰**。所以
App 端的找海報是「逐層往下退、第一層有結果就停」，而且同一句查詢
在一個 process 裡只打一次（含空結果的負向快取）。

## 部署
```bash
# 專案根目錄
npm --prefix functions install
firebase deploy --only functions
# 一併部署 Firestore 規則：
firebase deploy --only firestore:rules
```
（首次需先 `firebase login` 並 `firebase use <你的專案ID>`。）

## App 端需要的搭配（已在 App 內建，供參考）
1. `app/build.gradle.kts` 加 `implementation("com.google.firebase:firebase-messaging")`。
2. `BandaryMessagingService`（`data/push/`）接收推播並顯示系統通知（channel `announcements`）。
3. App 啟動時 `FirebaseMessaging.getInstance().subscribeToTopic("announcements")`。
4. `AndroidManifest.xml` 註冊該 service。

## 注意
- 免付費的 Firebase「Spark」方案**不支援 Cloud Functions**，需升級到「Blaze」(按量計費，通知量小幾乎免費)。
- FCM topic 推播是「盡力送達」，非保證即時；App 內的「官方通知」收信匣仍是可靠的備援（開 App 一定看得到）。
