# Bandary — Console 設定待辦清單

> 程式端都已寫好；以下是**你要在 Firebase / Google Cloud Console 手動完成**的設定，做完對應功能才會真正運作。
> 沒做的話 App 不會崩潰，但該功能會回報「失敗 / 找不到 / 尚未登入」之類訊息。

## 專案基本資料（核對用）

| 項目 | 值 |
|------|-----|
| Firebase / GCP 專案 | **bandary-e5547** |
| 專案編號 (project number) | 517762069873 |
| Android 套件名 (package) | `com.bandary.app` |
| App ID | `1:517762069873:android:c685c00f75a90fe7c3b2d0` |
| Storage bucket | `bandary-e5547.firebasestorage.app` |
| 偵錯 SHA-1（Android OAuth client）| `6f:15:1a:87:ed:c0:fa:f5:00:1d:7b:dd:87:aa:11:52:b6:6b:20:a3` |

> 兩個 console 入口：
> - **Firebase Console** → https://console.firebase.google.com/ → 選 `bandary-e5547`
> - **Google Cloud Console**（同一個專案，Calendar / OAuth 用）→ https://console.cloud.google.com/ → 上方選 `bandary-e5547`

---

## ✅ 待辦總覽（依重要性排序）

- [ ] 0. 建立 `admins/{uid}` 文件（**先做這個**：沒有它，下面的管理員功能全部會被規則擋掉）
- [ ] 1. 部署 Firestore 安全規則（**最關鍵**）：規則在版控的 `firestore.rules`，跑 `firebase deploy --only firestore:rules`。**不要手動貼 Console。**
- [ ] 1b.（選做）建 `admin_codes/{碼}` 文件（`active:true`），讓別人能用邀請碼自助成為管理員
- [ ] 2. Google Calendar API + OAuth 同意畫面（日曆匯入 / 自動推送）
- [ ] 3. 驗證 Authentication（Google 登入）仍正常
- [ ] 3b.（選做）活動自動抓取：升級 Blaze → 部署 Cloud Functions（見「活動抓取（爬蟲）」章節）
- [ ] 4.（選做）正式上線前的收尾

> ℹ️ **不需要 Firebase Storage**。免費活動的活動圖改成「本機壓縮成 Base64 直接存進 Firestore 文件」，用免費方案即可，省掉 Storage 的付費方案需求。

---

## 0. 建立 `admins/{uid}` 〔先做這個〕

**為什麼**：規則裡的 `isAdmin()` 判斷的是「Firestore 有沒有 `admins/{你的 uid}` 這份文件」。
App 裡用折扣碼開的管理員只是**本機 DataStore 的一個旗標**，Firestore 完全看不到 ——
這兩件事一直是分開的。沒有這份文件，下面這些都會失敗：

| 症狀 | 實際原因 |
|---|---|
| 折扣碼按「設為雲端」沒反應 | `discount_codes` 的 `create` 需要 `isAdmin()` |
| 使用者回饋讀取失敗 | `feedback` 的 `read` 需要 `isAdmin()` |
| 後台「尚未取得使用者資料」 | `users` 的 `read` 需要 `isAdmin()` |
| 台灣活動無法審核 / 刪除 | `free_events` 的 `update`、`delete` 需要 `isAdmin()` |
| 官方通知發不出去 | `announcements` 的 `create` 需要 `isAdmin()` |

**步驟**
1. 在 App 裡開 **設定 → 管理者 → 雲端管理員 uid**，點一下複製（沒登入 Google 就不會有 uid）。
2. Firebase Console → **Build → Firestore Database → 資料**。
3. 「開始收集」→ 集合 ID 填 `admins`。
4. 文件 ID 貼上剛剛複製的 uid（**不是 email**）。
5. 隨便加一個欄位，例如 `note`（字串）＝ `owner`。存檔。
6. 回 App，管理員功能重試一次。

> 這是 bootstrap 第一位管理員的方式（Console 寫入不受規則限制）。之後其他人可用**邀請碼**自助成為
> 管理員（見下方「管理員邀請碼」），規則只允許「附上有效邀請碼」的自助建立、且禁止改 / 刪自己那份；
> 要撤銷管理員仍是在 Console 直接刪文件。**沒有邀請碼就無法自行成為管理員。**

---

## 1. Firestore 安全規則 〔最關鍵〕

> ✅ **規則的單一真相來源是版控中的 [`firestore.rules`](firestore.rules)**（`firebase.json` 已指向它）。
> 部署用 CLI，**不要手動複製貼到 Console**（會與版控檔漂移，下次 deploy 又被覆蓋）：
>
> ```bash
> firebase deploy --only firestore:rules
> ```
>
> 這份 `firestore.rules` 已包含：`isAdmin()`（看 `admins/{uid}` 文件，非 email 比對）、
> `free_events` 的投稿/審核權限（非管理員只能建 `status=pending`）、好友請求/接受/藝人排行榜的
> 正確路徑、共享日曆拆成公開名片 + 好友限定內容，以及**管理員邀請碼**（`admins` 自助建立 + `admin_codes`）。

**影響功能**：免費活動（新增/審核/投稿）、意見回饋、加好友、看別人日曆、雲端備份。
沒部署 → 這些都會回「讀取/送出失敗」。

**步驟**
1. Firebase Console → 左側 **Build → Firestore Database**。
2. 若還沒建立資料庫：點「建立資料庫」→ 選**測試模式**或正式模式皆可 → 位置建議 `asia-east1`（台灣）。
3. 在專案根目錄跑 `firebase deploy --only firestore:rules`（或連同 hosting：`firebase deploy`）。
4. 到 Console → Firestore → 規則分頁確認內容與 `firestore.rules` 一致。

> 下面收合的是 **2024 舊版 email-based 規則**，**已被 `firestore.rules` 取代、請勿再使用**，僅留作歷史對照。
>
> <details><summary>舊版規則（勿貼）</summary>
>
> ```
> rules_version = '2';
> service cloud.firestore {
>   match /databases/{database}/documents {
>     match /users/{uid} {
>       allow read: if request.auth != null &&
>         (request.auth.uid == uid || request.auth.token.email == "你的管理者@gmail.com");
>     }
>     match /users/{uid}/{document=**} {
>       allow read: if request.auth != null &&
>         (request.auth.uid == uid || request.auth.token.email == "你的管理者@gmail.com");
>       allow write: if request.auth != null && request.auth.uid == uid;
>     }
>     match /discount_codes/{code} {
>       allow read: if request.auth != null;
>       allow write: if request.auth != null && request.auth.token.email == "你的管理者@gmail.com";
>     }
>     match /free_events/{id} { allow read: if true; allow write: if request.auth != null; }
>     match /feedback/{id} { allow read, write: if request.auth != null; }
>     match /shared_calendars/{uid} {
>       allow read: if request.auth != null;
>       allow write: if request.auth != null && request.auth.uid == uid;
>     }
>   }
> }
> ```
> </details>

**怎麼驗證成功**
- App 內用 Google 登入後，到「免費活動」投稿一筆 → 不再跳失敗。
- Firestore Console 的「資料」分頁會出現 `free_events` / `feedback` / `shared_calendars` 集合。

**後台「使用者人數 / 全平台統計」要能顯示，怎麼做**
> `firestore.rules` 用的是 `isAdmin()`（看 `admins/{你的 uid}` 文件），**不是 email 比對**。所以只要：
1. 在 Console → Firestore 手動建 `admins/{你的 uid}` 文件（bootstrap 第一位管理員，uid 見 App「設定 → 管理者 → 雲端管理員 uid」）。
2. 部署 `firestore.rules`（`firebase deploy --only firestore:rules`）。
3. App 用該帳號登入一次（會寫入 `users/{你的uid}` 的 email 與 `lastActiveAt`）。
4. 進「我的 → 設定 → 後台數據」→「使用者」區顯示註冊人數、7 / 30 天內活躍；全平台排行、編輯折扣碼一併生效。
- 原理：`firestore.rules` 的 `users` 讀取條件是 `isOwner(uid) || isAdmin()`，管理員才能彙整所有 user 文件。
- 沒設好時：後台顯示「尚未取得使用者資料…」，不影響其他功能。
- 活躍時間來源：每次「開啟 App」與登入時寫入 `lastActiveAt`；舊帳號只有備份過時用 `lastBackupAt` 近似。

---

## 管理員邀請碼（App / 網頁「我有邀請碼」）

> ✅ 對應規則**已在 [`firestore.rules`](firestore.rules)**（`admins` 的 `allow create` + `admin_codes`），
> 用 `firebase deploy --only firestore:rules` 部署即生效。你**只需在 Console 建一組邀請碼文件**（下方步驟）。
> 部署前輸入任何碼都會顯示「邀請碼無效或已停用」——那是規則還沒上，不是前端壞掉。
> **把關的是規則，不是前端**：前端只負責送出，能不能寫入完全由規則決定。

流程：前端寫一份 `admins/{自己的 uid} = { code, email, createdAt }`；規則用 `get()` 反查
`admin_codes/{code}` 是否 `active == true`，不成立就拒絕。`admin_codes` 前端完全讀不到
（規則裡 `get()` 不受 client 讀取權限限制），碼不會外洩。`admins/{uid}` 只有本人讀得到，
所以存在裡面的 `code` 也只有自己看得到。

**在 Console 建一組邀請碼**：Firestore → 資料 → 集合 ID `admin_codes` → 文件 ID **就是那組碼本身**
（例如 `BANDARY2026`）→ 欄位 `active`(boolean) = `true`。停用＝把 `active` 改 `false` 或刪文件
（已是管理員者不受影響）。

<details><summary>規則內容（已在 firestore.rules，這裡僅供對照）</summary>

```
    match /admins/{uid} {
      allow read: if isOwner(uid);
      allow create: if isOwner(uid)
        && exists(/databases/$(database)/documents/admin_codes/$(request.resource.data.code))
        && get(/databases/$(database)/documents/admin_codes/$(request.resource.data.code)).data.active == true;
      allow update, delete: if false;
    }

    // 邀請碼：只給規則的 get() 用，任何前端都讀不到、也寫不進去（只能在 Console 建）。
    match /admin_codes/{code} {
      allow read, write: if false;
    }
    match /admin_codes/{code} {
      allow read, write: if false;
    }
```
</details>

**驗證**：用非管理員帳號登入（App 或網頁）→ 輸入 `BANDARY2026` → 顯示「已成為管理員」。
輸入亂碼則顯示「邀請碼無效或已停用」（若部署前也會是這個訊息）。

---

## 好友機制 v3（代號 + 雙向請求）— ✅ 已在 `firestore.rules`

> ✅ 這些規則（`users` 本人可寫、`handles` 唯一代號、`friend_requests` / `friends` 跨帳號寫入）
> **都已包含在 [`firestore.rules`](firestore.rules)**，`firebase deploy --only firestore:rules` 即生效，
> 不需要手動貼。下面保留規則片段僅供理解其設計。

實際生效的版本以 `firestore.rules` 為準（那份用 `isAdmin()` 而非 email 比對）。設計重點：

**1. `match /users/{uid}` 追加 `allow write`（本人可寫自己的 user 文件：`lastActiveAt`、`handle`）**

```diff
     match /users/{uid} {
       allow read: if request.auth != null &&
         (request.auth.uid == uid || request.auth.token.email == "你的管理者@gmail.com");
+      allow write: if request.auth != null && request.auth.uid == uid;
     }
```

**2. 新增 `handles`（全站唯一代號；建立/更新/刪除限本人，達成唯一性）**

```
    // 代號：全站唯一。登入者可讀（搜尋加友）；
    // 只有把 uid 設為自己的人能建立；既有代號只有擁有者能改 / 刪（釋放）。
    match /handles/{handle} {
      allow read: if request.auth != null;
      allow create: if request.auth != null && request.resource.data.uid == request.auth.uid;
      allow update, delete: if request.auth != null && resource.data.uid == request.auth.uid;
    }
```

**3. 新增 `friend_requests` 與 `friends` 明確規則（雙向請求 / 接受時的跨帳號寫入）**

```
    // 好友請求：寄件者可在「對方收件匣」建立一筆（doc id = 寄件者 uid）；
    // 收件者可讀 / 刪自己的收件匣。
    match /users/{uid}/friend_requests/{fromUid} {
      allow read, delete: if request.auth != null && request.auth.uid == uid;
      allow create: if request.auth != null && request.auth.uid == fromUid
        && request.resource.data.fromUid == request.auth.uid;
    }

    // 好友清單：本人可寫自己的；也允許「曾送請求給我的人」在我接受時，
    // 把我加進他的清單（跨帳號寫入，以請求存在為條件）。
    match /users/{ownerId}/friends/{friendId} {
      allow read: if request.auth != null &&
        (request.auth.uid == ownerId || request.auth.token.email == "你的管理者@gmail.com");
      allow write: if request.auth != null && (
        request.auth.uid == ownerId ||
        (request.auth.uid == friendId &&
          exists(/databases/$(database)/documents/users/$(friendId)/friend_requests/$(ownerId)))
      );
    }
```

**驗證流程**
1. 兩支帳號各自到「好友的日曆」設定代號（會寫 `handles/{代號}` 與 `users/{uid}.handle`）。
2. A 用 B 的代號送出請求 → `users/{B}/friend_requests/{A}` 出現一筆。
3. B 在「好友請求」按接受 → `users/{A}/friends/{B}` 與 `users/{B}/friends/{A}` 同時出現，請求消失。
4. 「允許用 email 搜尋我」開啟後，對方才能用 email 搜到你（寫入 `shared_calendars/{uid}.discoverableByEmail = true`）。

> 說明：`handles` 的「建立限本人、更新限擁有者」讓別人無法覆寫你的代號；App 端另用 transaction 先讀後寫再次確認唯一性，雙保險。跨帳號寫入好友清單以「收件匣存在對應請求」為授權條件，避免任何人未經同意把自己塞進別人的好友。

---

## 活動抓取（爬蟲）— 需 Blaze＋部署 Functions，**尚未部署**

> 定時（每天台北 06:00）自動從你指定的來源抓新活動，寫成**待審核**（不自動公開）。
> 程式已寫好（`functions/crawl.js` 引擎、`functions/index.js` 的 `crawlScheduled` 排程＋`crawlNow` 管理員手動觸發），
> 但**要你完成下面兩步才會生效**。管理介面在網頁「我的 → 管理員 → 活動抓取來源」。

**只支援機器可讀格式**：iCal（`.ics`）與網頁內嵌的 schema.org/`Event` JSON-LD。不做 HTML 硬爬（易壞＋踩 ToS）。
**請只加允許抓取的來源**（例如場地/售票頁自己提供的 .ics 行事曆匯出、或頁面本身內嵌活動結構化資料）。

**1. 升級 Blaze（按量計費）**
Firebase Console → 左下 **升級** → 選 **Blaze**。原因：免費 Spark 方案的 Cloud Functions **不能對外連線**抓別的網站。
低用量（每天一次、少量來源）通常落在免費額度內，但需綁信用卡。**這步只能你本人做。**

**2. 部署 Functions**
```bash
npm --prefix functions install
firebase deploy --only functions
```
首次部署 `crawlScheduled` 時，Firebase 會自動啟用 Cloud Scheduler / Pub/Sub（可能要求同意）。
Functions 區域為 `asia-east1`（與網頁呼叫端一致，勿改單邊）。

**3. 加來源、試抓**
網頁「我的 → 管理員 → 活動抓取來源」→ 貼一個 `.ics` 或含活動資料的頁面網址 → 新增 → 按「立即抓取一次」。
抓到的活動會出現在「待審核」，逐筆核准才公開。之後每天自動抓一次。

**規則**：`crawl_sources` 只有管理員能讀寫（已在 `firestore.rules`）；Functions 用 Admin SDK 寫 `free_events`，不受前端規則限制。

**注意**：`crawlNow` 是 callable，未部署 Functions 前按「立即抓取」會顯示 `internal / not-found` 之類錯誤——那是還沒部署，不是壞掉。

---

## 2. Google Calendar API + OAuth 同意畫面

**影響功能**：演唱會頁「從 Google 日曆匯入」、新增演唱會自動推送到日曆、免費活動從日曆匯入。
沒做 → 匯入頁會顯示「Google 日曆讀取失敗 / 取得授權失敗」並提供重新連結按鈕。

**2a. 啟用 Calendar API**
1. Google Cloud Console → 確認上方專案是 **bandary-e5547**。
2. 左側 **API 和服務 → 程式庫 (Library)**。
3. 搜尋「Google Calendar API」→ 進去點 **啟用 (Enable)**。（先前若已啟用會顯示「管理」即可。）

**2b. OAuth 同意畫面**
1. 左側 **API 和服務 → OAuth 同意畫面 (OAuth consent screen)**。
2. User Type 選 **外部 (External)**（個人帳號只能選這個）。
3. 應用程式資訊：App 名稱填 `Bandary`、使用者支援電子郵件選你的 Gmail、開發人員聯絡 email 同樣填你的。
4. **範圍 (Scopes)**：點「新增或移除範圍」→ 手動加入
   `https://www.googleapis.com/auth/calendar`（完整日曆讀寫）→ 更新 → 儲存。
5. **測試使用者 (Test users)**：點「新增使用者」→ 加入**你自己實機登入用的那個 Gmail**（很重要，沒加會被擋）。
6. 發布狀態維持「**測試中 (Testing)**」即可（自用足夠；測試中 token 有效期較短，過期重新連結即可）。

**2c. 登入時遇到「Google 尚未驗證這個應用程式」**
- 這是測試中應用的正常畫面。點左下 **進階 (Advanced) → 繼續前往 Bandary（不安全）** 即可授權。只有列在測試使用者裡的帳號能這樣通過。

**怎麼驗證成功**
- App「我的」用 Google 登入 → 演唱會頁右上角 📅「從 Google 日曆匯入」→ 選範圍 → 跳出你日曆上的事件清單。
- 若顯示「尚未連結 Google 日曆」就按連結鈕；若「取得授權失敗」多半是該帳號沒被加進測試使用者（回 2b-5）。

---

## 3. 驗證 Authentication（Google 登入）

**影響功能**：所有雲端功能的前提（備份、免費活動、回饋、好友）。先前已設過，這裡只是核對。

**步驟**
1. Firebase Console → **Build → Authentication → Sign-in method**。
2. 確認 **Google** 供應商是 **已啟用 (Enabled)**。
3. 確認 **設定 → 已授權網域** 含預設的 `bandary-e5547.firebaseapp.com`。
4. （若曾改過 SHA-1）Firebase Console → 專案設定 ⚙️ → 你的 Android App → 確認 SHA-1 含上表那組偵錯指紋；改過要重新下載 `google-services.json` 覆蓋 `app/`。

**怎麼驗證成功**
- App「我的」分頁能用 Google 登入並顯示你的 email。

---

## 4.（選做）正式上線前收尾

- [ ] **Firestore 規則收緊**：把「任何登入者可讀回饋」改成只有管理員；把 free_events 寫入限管理員（用 custom claims）。
- [ ] **OAuth 同意畫面發布**：從「測試中」改成「正式版」（需 Google 驗證流程，自用可略）。
- [ ] **Release 簽章 SHA-1**：正式打包的 keystore SHA-1 也要加進 Firebase（否則 release 版登入會失敗）。詳見 README「上架 Google Play Store 步驟」。
- [ ] **ML Kit 模型瘦身**（選）：中文 OCR 目前 bundled，APK 約 67MB；可改 unbundled 降到 ~25MB（首次使用才下載模型）。

---

## 做完後的快速自我測試（App 內）

1. 「我的」→ Google 登入成功、顯示 email。
2. 「免費活動」→ 投稿一筆（附圖 + 來源網址）→ 成功、卡片有圖、來源可點。
3. 管理員帳號 → 免費活動右上審核鈕 → 看到待審、能核准。
4. 「設定 → 意見回饋」送一筆 → 管理員「使用者回饋」看得到。
5. 「日曆」右上 👥 → 加好友（對方需先登入開過此頁）→ 看到對方即將演出。
6. 「演唱會」右上 📅 → 從 Google 日曆匯入 → 勾選 → 匯入到我的演唱會。
7. 「設定 → 管理者 → 後台數據」→ 看到免費活動統計。
