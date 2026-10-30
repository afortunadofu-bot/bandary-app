/**
 * Bandary（聽團曆）Cloud Functions
 *
 * notifyOnAnnouncement：當管理員在 Firestore 的 announcements/{id} 新增一則官方通知時，
 * 透過 FCM 推播給所有「訂閱 announcements topic」的裝置——即使 App 沒開也會跳系統通知。
 *
 * 部署：
 *   1. npm --prefix functions install
 *   2. firebase deploy --only functions
 * （App 端需加 firebase-messaging 並在啟動時 subscribeToTopic("announcements")，見 README。）
 */
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { getAuth } = require("firebase-admin/auth");
const { crawlAll } = require("./crawl");
const crypto = require("crypto");
const { defineSecret } = require("firebase-functions/params");

/**
 * 把上游（Spotify / Google）的失敗記進 log。
 *
 * 為什麼需要：`throw new HttpsError(...)` 是**刻意**丟的錯誤，
 * firebase-functions 不會幫它寫 log——它只會把訊息回給用戶端。
 * 結果 Cloud Logging 裡只看得到一排「503、回應 220 bytes」的空錯誤，
 * **沒有任何一行說為什麼**。2026-10-28 查這件事時就卡在這裡：
 * 兩支函式從 10-12 到 10-24 每一次呼叫都 503，而雲端 log 一句原因都沒有。
 *
 * 記的是狀態碼與上游自己的錯誤文字（Spotify 回 `invalid_client`、
 * Google 回 `API key not valid`…）——那正是分辨「金鑰沒設」與
 *「金鑰設錯」需要的東西。
 *
 * **絕對不記網址也不記金鑰**：Custom Search 的網址裡就帶著 `key=`，
 * 整串記下去等於把金鑰抄進 log。只記 `set: true/false`，
 * 那足以回答「到底有沒有設」而不洩漏值本身。
 */
async function logUpstreamFailure(who, res, secretsPresent) {
  let body = "";
  try {
    body = (await res.text()).slice(0, 300);
  } catch (e) {
    body = "(讀不到回應內容)";
  }
  console.error(
    `${who} 失敗：HTTP ${res.status}`,
    JSON.stringify({ secrets: secretsPresent, upstream: body })
  );
}

/** 只回「有沒有值」，不回值本身。 */
const isSet = (v) => {
  try {
    return typeof v.value() === "string" && v.value().length > 0;
  } catch (e) {
    return false;
  }
};

// Spotify 金鑰存在 Secret Manager，不再寫進 App（App 可以被反編譯拿走）。
// 設定：firebase functions:secrets:set SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET
const SPOTIFY_CLIENT_ID = defineSecret("SPOTIFY_CLIENT_ID");
const SPOTIFY_CLIENT_SECRET = defineSecret("SPOTIFY_CLIENT_SECRET");

// Google Custom Search（找海報）。同樣的理由：這把金鑰本來是明碼寫在
// ImageSearchService.kt 的 companion object 裡，等於跟著 APK 一起發給每個人，
// 而且也躺在公開的 git 歷史裡。
// 設定：firebase functions:secrets:set CSE_API_KEY / CSE_CX
const CSE_API_KEY = defineSecret("CSE_API_KEY");
const CSE_CX = defineSecret("CSE_CX");

initializeApp();

exports.notifyOnAnnouncement = onDocumentCreated(
  "announcements/{id}",
  async (event) => {
    const data = (event.data && event.data.data()) || {};
    const title = data.title || "Bandary";
    const body = data.body || "";
    const targets = Array.isArray(data.targetUids) ? data.targetUids.filter(Boolean) : [];

    // 全體公告：走 topic。訂閱者自己收，不需要知道任何人的 token。
    if (targets.length === 0) {
      await getMessaging().send({
        topic: "announcements",
        notification: { title, body },
        data: { announcementId: event.params.id },
      });
      console.log("announcement -> topic");
      return;
    }

    // 指定對象：topic 做不到「只送給其中三個人」，所以要查那幾位的裝置 token。
    // 一個人可能有手機 + 平板，所以 fcmTokens 是陣列。
    const db = getFirestore();
    const tokens = [];
    for (const uid of targets.slice(0, 500)) {
      try {
        const snap = await db.collection("users").doc(uid).get();
        const list = snap.get("fcmTokens");
        if (Array.isArray(list)) tokens.push(...list.filter(Boolean));
      } catch (e) {
        console.warn("read tokens failed", uid, e);
      }
    }
    const unique = Array.from(new Set(tokens));
    if (unique.length === 0) {
      // 收件者都沒有登記過 token（沒開過新版、或沒給通知權限）。
      // 這不是錯誤：App 內的收信匣仍然讀得到這則公告，只是不會跳通知。
      console.log("announcement -> no tokens for", targets.length, "targets");
      return;
    }

    // sendEachForMulticast 一次最多 500 個 token。
    for (let k = 0; k < unique.length; k += 500) {
      const chunk = unique.slice(k, k + 500);
      const res = await getMessaging().sendEachForMulticast({
        tokens: chunk,
        notification: { title, body },
        data: { announcementId: event.params.id },
      });
      // 失效的 token 要清掉，否則每次發公告都在對死掉的裝置重試。
      const dead = [];
      res.responses.forEach((r, idx) => {
        if (!r.success) {
          const code = r.error && r.error.code;
          if (code === "messaging/registration-token-not-registered" ||
              code === "messaging/invalid-registration-token") {
            dead.push(chunk[idx]);
          }
        }
      });
      if (dead.length) {
        for (const uid of targets) {
          try {
            await db.collection("users").doc(uid).update({
              fcmTokens: FieldValue.arrayRemove(...dead),
            });
          } catch (e) { /* 這個 uid 沒有那些 token，忽略 */ }
        }
      }
      console.log("announcement -> sent", res.successCount, "/", chunk.length);
    }
  }
);

exports.crawlScheduled = onSchedule(
  // memory 拉高到 2GiB：無頭瀏覽器（見 crawl.js 的 renderPage）比純 fetch 吃記憶體很多。
  { schedule: "0 6 * * *", timeZone: "Asia/Taipei", region: "asia-east1", memory: "2GiB", timeoutSeconds: 300 },
  async () => {
    const summary = await crawlAll(getFirestore());
    console.log("crawl scheduled:", JSON.stringify(summary));
  }
);

/**
 * 管理員手動觸發一次（設定頁的「立即抓取」）。只有 admins/{uid} 存在的帳號能呼叫。
 * 回傳這次新增 / 略過的統計。
 */
exports.crawlNow = onCall(
  { region: "asia-east1", memory: "2GiB", timeoutSeconds: 300 },
  async (request) => {
    const uid = request.auth && request.auth.uid;
    if (!uid) throw new HttpsError("unauthenticated", "需要登入");
    const db = getFirestore();
    const admin = await db.collection("admins").doc(uid).get();
    if (!admin.exists) throw new HttpsError("permission-denied", "僅限管理員");
    const summary = await crawlAll(db);
    console.log("crawl now:", JSON.stringify(summary));
    return summary;
  }
);

// ── 邀請碼 ───────────────────────────────────────────────────
//
// 邀請碼是 uid 的單向雜湊（見 App 的 InviteCode.kt），所以**沒辦法從碼反推 uid**。
// 要讓「輸入朋友的碼」行得通，就得有一份 碼 → uid 的索引：invite_codes/{code}。
//
// 這份索引只有 Admin SDK 寫得到（firestore.rules 最後的 catch-all 是 deny，
// 而 Admin SDK 不受規則限制），所以不需要為它部署任何規則。
//
// 為什麼一定要在伺服器端重算而不是讓 App 把自己的碼送上來：
// 不重算就無法驗證那組碼真的屬於呼叫者，等於誰都能先去佔走別人的碼、把推薦功勞偷走。

// 與 App 的 InviteCode.kt **必須完全一致**。改任何一邊都會讓既有邀請碼失效。
const INVITE_ALPHABET = "ACDEFGHJKMNPQRTUVWXY34679";
const INVITE_LENGTH = 6;

function inviteCodeOf(uid) {
  if (!uid) return "";
  const digest = crypto.createHash("sha1").update(uid, "utf8").digest();
  let out = "";
  for (let i = 0; i < INVITE_LENGTH; i++) {
    out += INVITE_ALPHABET[digest[i] % INVITE_ALPHABET.length];
  }
  return out;
}

/** 幾天的試用期：邀請人與被邀請人各自拿到這麼多天。 */
const INVITE_TRIAL_DAYS = 14;

/**
 * 把試用期加給某個 uid。採「從現在起算」或「接在既有試用期後面」取較晚者
 * ——連續拿到兩次獎勵，第二次不該把第一次剩下的天數吃掉。
 * 與 App 端 reviewPromo 的算法一致。
 */
async function grantTrial(db, uid, days, reason) {
  const ref = db.collection("promo_rewards").doc(uid);
  const now = Date.now();
  const snap = await ref.get();
  const existing = (snap.exists && snap.get("trialUntil")) || 0;
  const until = Math.max(existing, now) + days * 24 * 60 * 60 * 1000;
  await ref.set(
    { uid, trialUntil: until, grantedBy: "system:invite", grantedAt: now, reason },
    { merge: true }
  );
  return until;
}

/**
 * 登入後呼叫一次，把「我的邀請碼 → 我的 uid」記進索引。
 * 不這樣做的話，別人輸入我的碼時查不到我是誰，獎勵就發不出去。
 * 冪等：重複呼叫只是把同一份文件再寫一次。
 */
exports.registerInvite = onCall(
  { region: "asia-east1", maxInstances: 5 },
  async (request) => {
    const uid = request.auth && request.auth.uid;
    if (!uid) throw new HttpsError("unauthenticated", "需要登入");
    const code = inviteCodeOf(uid);
    const db = getFirestore();
    const ref = db.collection("invite_codes").doc(code);
    const snap = await ref.get();
    // 理論上碰撞機率極低，但真的撞到時先到先得——後來的人不覆蓋，
    // 否則前一個人已經發出去的碼會突然指向別人。
    if (snap.exists && snap.get("uid") !== uid) {
      console.warn("invite code collision", code);
      return { code, registered: false };
    }
    await ref.set({ uid, code, createdAt: Date.now() }, { merge: true });
    return { code, registered: true };
  }
);

/**
 * 兌換朋友的邀請碼。兩邊各拿 INVITE_TRIAL_DAYS 天。
 *
 * 一定要走伺服器端：
 *   - 要寫 promo_rewards/{對方的 uid}，用戶端規則擋著（那是 admin-only）。
 *   - 「只能兌換一次」這種限制如果放在 App 端，重裝就破了。
 */
exports.redeemInvite = onCall(
  { region: "asia-east1", maxInstances: 5 },
  async (request) => {
    const uid = request.auth && request.auth.uid;
    if (!uid) throw new HttpsError("unauthenticated", "需要登入");
    const raw = String((request.data && request.data.code) || "").trim().toUpperCase();
    const code = raw.split("").filter((c) => INVITE_ALPHABET.includes(c)).join("").slice(0, INVITE_LENGTH);
    if (code.length !== INVITE_LENGTH) throw new HttpsError("invalid-argument", "邀請碼格式不對");
    if (code === inviteCodeOf(uid)) throw new HttpsError("failed-precondition", "不能用自己的邀請碼");

    const db = getFirestore();
    const used = db.collection("invite_redemptions").doc(uid);
    if ((await used.get()).exists) {
      throw new HttpsError("already-exists", "你已經兌換過邀請碼了");
    }
    const owner = await db.collection("invite_codes").doc(code).get();
    if (!owner.exists) throw new HttpsError("not-found", "找不到這組邀請碼");
    const referrer = owner.get("uid");
    if (!referrer || referrer === uid) throw new HttpsError("failed-precondition", "不能用自己的邀請碼");

    // 先記下「這個人兌換過了」再發獎勵。順序反過來的話，
    // 發完獎勵但記錄寫失敗，就會變成可以無限重領。
    await used.set({ uid, code, referrer, redeemedAt: Date.now() });
    await grantTrial(db, uid, INVITE_TRIAL_DAYS, "使用邀請碼 " + code);
    await grantTrial(db, referrer, INVITE_TRIAL_DAYS, "朋友使用了你的邀請碼");
    return { days: INVITE_TRIAL_DAYS };
  }
);

// ── 圖片搜尋代理（找海報）─────────────────────────────────────
// App 原本自己帶 Google Custom Search 的 API key 直接打 googleapis.com。
// 金鑰寫在原始碼 = 跟著 APK 發出去，誰都能拿去用光額度或讓你被收費。
//
// 額度：免費方案一天 100 次，**全部使用者共用這一把金鑰**。
// 所以 App 端的搜尋是「逐層往下退、第一層有結果就停」，不是一次打四個查詢。
exports.imageSearch = onCall(
  { region: "asia-east1", secrets: [CSE_API_KEY, CSE_CX], maxInstances: 5 },
  async (request) => {
    // 只給登入者用，免得變成公開的免費圖片搜尋代理。
    if (!request.auth) throw new HttpsError("unauthenticated", "需要登入");
    const data = request.data || {};
    const q = String(data.q || "").trim().slice(0, 100);
    const limit = Math.min(Math.max(parseInt(data.limit, 10) || 10, 1), 10);
    if (!q) return { items: [] };

    const url =
      "https://www.googleapis.com/customsearch/v1" +
      `?key=${encodeURIComponent(CSE_API_KEY.value())}` +
      `&cx=${encodeURIComponent(CSE_CX.value())}` +
      `&searchType=image&num=${limit}&q=${encodeURIComponent(q)}`;
    const res = await fetch(url);
    if (!res.ok) {
      // **不要把 url 記進 log**：它裡面就帶著 key=。
      await logUpstreamFailure("Custom Search", res, {
        CSE_API_KEY: isSet(CSE_API_KEY),
        CSE_CX: isSet(CSE_CX),
      });
      // 額度用完是 429／403。把它跟真的壞掉分開講，不然看 log 的人會以為是 bug。
      if (res.status === 429 || res.status === 403) {
        throw new HttpsError("resource-exhausted", "圖片搜尋今天的額度用完了");
      }
      throw new HttpsError("unavailable", `Custom Search HTTP ${res.status}`);
    }
    const json = await res.json();
    const items = (json.items || []).map((o) => ({
      thumbnailUrl: (o.image && o.image.thumbnailLink) || o.link || "",
      fullUrl: o.link || "",
      width: (o.image && o.image.width) || 0,
      height: (o.image && o.image.height) || 0,
    })).filter((o) => o.fullUrl);
    if (items.length === 0) {
      // 金鑰沒問題但回空的：最常見的原因是 Programmable Search Engine
      // 裡還沒加任何網域，那時 Google 回 HTTP 200 + 空清單，
      // 看起來跟「真的沒有這張圖」一模一樣。記一行才分得出來。
      console.warn("Custom Search 回 0 筆", JSON.stringify({
        q,
        totalResults: (json.searchInformation || {}).totalResults,
      }));
    }
    return { items };
  }
);

// ── 刪除帳號 ─────────────────────────────────────────────────
// Google Play 規定可建立帳號的 App 必須提供刪除帳號（App 內＋網頁）。
// 一定要走伺服器端：別人的好友清單、排行榜、登入帳號本身，用戶端規則都刪不到。
// 每一步各自 try，最後才刪登入帳號——前面任何一步失敗，使用者都還能用同一個帳號再試一次。
exports.deleteMyAccount = onCall(
  { region: "asia-east1", memory: "512MiB", timeoutSeconds: 300 },
  async (request) => {
    const uid = request.auth && request.auth.uid;
    if (!uid) throw new HttpsError("unauthenticated", "需要登入");
    const email = (request.auth.token && request.auth.token.email) || null;
    const db = getFirestore();
    const userRef = db.collection("users").doc(uid);
    const failed = [];
    const attempt = async (name, fn, { required = true } = {}) => {
      try {
        await fn();
      } catch (e) {
        console.error(`deleteMyAccount ${name} failed:`, e);
        if (required) failed.push(name);
      }
    };

    // 好友：先讀自己的好友清單（要在刪 users/{uid} 之前），把自己從對方清單移除。
    await attempt("好友關係", async () => {
      const friends = await userRef.collection("friends").get();
      await Promise.all(friends.docs.map((d) =>
        db.collection("users").doc(d.id).collection("friends").doc(uid).delete()));
    });
    // 寄出但對方還沒處理的好友請求。集合群組查詢可能缺索引，失敗不擋整個流程（請求本身只剩一個失效的 uid）。
    await attempt("寄出的好友請求", async () => {
      const sent = await db.collectionGroup("friend_requests").where("fromUid", "==", uid).get();
      await Promise.all(sent.docs.map((d) => d.ref.delete()));
    }, { required: false });
    // 演唱會、選歌、雲端備份照片、好友清單、收件匣、設定……全部在 users/{uid} 底下。
    await attempt("個人資料", () => db.recursiveDelete(userRef));
    await attempt("共享日曆", () => db.recursiveDelete(db.collection("shared_calendars").doc(uid)));
    await attempt("好友代碼", async () => {
      const handles = await db.collection("handles").where("uid", "==", uid).get();
      await Promise.all(handles.docs.map((d) => d.ref.delete()));
    });
    await attempt("藝人粉絲排名", async () => {
      const artists = await db.collection("artist_fans").listDocuments();
      await Promise.all(artists.map((a) => a.collection("fans").doc(uid).delete()));
    });
    await attempt("年度排行榜", async () => {
      const years = await db.collection("yearly_leaderboard").listDocuments();
      await Promise.all(years.map((y) => db.recursiveDelete(y.collection("entries").doc(uid))));
    });
    await attempt("管理員身分", () => db.collection("admins").doc(uid).delete());
    if (email) {
      // 已公開的活動是大家共用的資料，保留內容、拿掉投稿者 email；還沒公開的直接刪。
      await attempt("投稿的活動", async () => {
        const events = await db.collection("free_events").where("createdBy", "==", email).get();
        await Promise.all(events.docs.map((d) =>
          d.get("status") === "approved" ? d.ref.update({ createdBy: null }) : d.ref.delete()));
      });
      await attempt("意見回饋", async () => {
        const fb = await db.collection("feedback").where("createdBy", "==", email).get();
        await Promise.all(fb.docs.map((d) => d.ref.delete()));
      });
    }

    if (failed.length) {
      throw new HttpsError("internal", `部分資料刪除失敗（${failed.join("、")}），請稍後再試一次`);
    }
    await getAuth().deleteUser(uid);
    console.log("account deleted:", uid);
    return { ok: true };
  }
);

// ── Spotify 搜尋代理 ─────────────────────────────────────────
// App 原本直接帶 Client Secret 呼叫 Spotify；上架後任何人拆開 APK 都拿得到。
// 改成 App 呼叫這個 callable，金鑰只存在伺服器端。只回傳畫面用得到的欄位。
let spotifyToken = null;
let spotifyTokenExpiresAt = 0;

async function spotifyAccessToken() {
  const now = Date.now();
  if (spotifyToken && now < spotifyTokenExpiresAt) return spotifyToken;
  const basic = Buffer.from(`${SPOTIFY_CLIENT_ID.value()}:${SPOTIFY_CLIENT_SECRET.value()}`).toString("base64");
  const res = await fetch("https://accounts.spotify.com/api/token", {
    method: "POST",
    headers: { Authorization: `Basic ${basic}`, "Content-Type": "application/x-www-form-urlencoded" },
    body: "grant_type=client_credentials",
  });
  if (!res.ok) {
    // 400 + invalid_client 幾乎一定是「Client ID／Secret 沒設或設錯」。
    // 這一行就是 2026-10-28 之前一直拿不到的那行。
    await logUpstreamFailure("Spotify token", res, {
      SPOTIFY_CLIENT_ID: isSet(SPOTIFY_CLIENT_ID),
      SPOTIFY_CLIENT_SECRET: isSet(SPOTIFY_CLIENT_SECRET),
    });
    throw new HttpsError("unavailable", `Spotify token HTTP ${res.status}`);
  }
  const json = await res.json();
  spotifyToken = json.access_token;
  spotifyTokenExpiresAt = now + ((json.expires_in || 3600) - 60) * 1000;
  return spotifyToken;
}

exports.spotifySearch = onCall(
  { region: "asia-east1", secrets: [SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET], maxInstances: 5 },
  async (request) => {
    // 只給登入者用，避免被當成公開的免費 Spotify 代理濫用。
    if (!request.auth) throw new HttpsError("unauthenticated", "需要登入");
    const data = request.data || {};
    const type = data.type === "artist" ? "artist" : "track";
    const q = String(data.q || "").trim().slice(0, 100);
    const limit = Math.min(Math.max(parseInt(data.limit, 10) || 8, 1), 20);
    if (!q) return { items: [] };

    const token = await spotifyAccessToken();
    const url = `https://api.spotify.com/v1/search?type=${type}&limit=${limit}&q=${encodeURIComponent(q)}`;
    const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
    if (!res.ok) {
      await logUpstreamFailure("Spotify search", res, {});
      throw new HttpsError("unavailable", `Spotify search HTTP ${res.status}`);
    }
    const json = await res.json();

    if (type === "artist") {
      const items = ((json.artists && json.artists.items) || []).map((o) => ({
        id: o.id || "",
        name: o.name || "",
        imageUrl: (o.images && o.images[0] && o.images[0].url) || null,
        genres: o.genres || [],
        followers: (o.followers && o.followers.total) || 0,
        popularity: o.popularity || 0,
      }));
      return { items };
    }
    const items = ((json.tracks && json.tracks.items) || []).map((o) => ({
      id: o.id || "",
      title: o.name || "",
      artistName: (o.artists && o.artists[0] && o.artists[0].name) || "",
      albumName: (o.album && o.album.name) || "",
      albumImageUrl: (o.album && o.album.images && o.album.images[0] && o.album.images[0].url) || null,
    }));
    return { items };
  }
);
