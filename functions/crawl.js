/**
 * 活動蒐集引擎（定時自動抓）。
 *
 * 設計原則（重要，別改壞）：
 *  - 只抓「機器可讀」格式：iCal(.ics) 與網頁內嵌的 schema.org/Event JSON-LD。
 *    這兩種本來就是給機器讀的 → 尊重、且不會網站一改版就爆。**不做 HTML 硬爬**
 *    （不用 CSS 選擇器 / DOM 結構去猜標題價格在哪，那種寫法版面一改就壞）。
 *  - 唯一例外：「活動列表頁」的連結探索（見下方 extractEventLinks）。這不是猜版面，
 *    只認 <a href> 這種瀏覽器本來就要正確解析的東西，比 CSS class 穩定很多；
 *    找到連結後一樣只吃該連結頁面裡的 JSON-LD，不解析列表頁本身的排版內容。
 *  - 純 fetch 拿不到任何活動（iCal/JSON-LD/列表連結都槓龜）時，才退一步用無頭瀏覽器
 *    把 JS 真的跑完再重新解析一次（見 renderPage）。一樣不猜版面、一樣只吃 JSON-LD，
 *    差別只是「html 從哪裡來」。這條路貴很多（開一個真的 Chromium），所以：
 *      - 每次 crawlAll() 執行最多開 MAX_RENDER_FALLBACKS 次，全部來源共用這個額度；
 *      - 瀏覽器只開一個、整次執行重複用（開分頁而非重開瀏覽器），跑完才關閉；
 *      - 對會直接擋自動化請求的網站（見下方 tixCraft 記錄）不保證有用——那種是
 *        WAF 主動辨識，不是「這頁需要 JS」的問題，無頭瀏覽器一樣可能被擋。
 *    2026-08-18 實測記錄（別再重測一次，直接看結論）：
 *      - KKTIX：全站「探索活動」搜尋頁是前端 JS 渲染，fetch 抓不到活動；
 *        但每個「主辦方自己的頁面」（如 kklivetw.kktix.cc）是 Rails 伺服器渲染，
 *        會列出該主辦所有活動連結；個別活動頁固定內嵌 JSON-LD。robots.txt 全開放。
 *      - tixCraft：/activity/detail/ 這類活動頁對一般 UA 直接回 401
 *        {"response":"identify"}（WAF challenge，不分是否標成 bot），fetch 進不去，
 *        寫規則也沒用；robots.txt 本身也被 WAF 擋下。**不建議做，會直接失敗。**
 *      - ibon：任何頁面（含帶活動 ID 的細節頁）伺服器端回傳的都是幾乎空的 SPA 殼
 *        （純文字內容 <10 字），沒有 JS 引擎就完全拿不到資料。**同樣不建議做。**
 *      - ACCUPASS：個別活動頁（/event/{id}）本身就有完整 schema.org JSON-LD，現有邏輯
 *        直接吃得到，不用加任何程式碼；但 /search 探索頁跟 KKTIX 的探索頁一樣是前端
 *        渲染，找不到活動連結可展開，也沒有等同 KKTIX 主辦頁那種伺服器渲染列表可用。
 *        robots.txt 這條路徑本身被 WAF 擋下（回一頁 generic block page），但實際的
 *        /event/ 與 /search 頁面用一般 UA 都正常回 200 帶完整內容，不受影響。
 *      - iNDIEVOX：任何頁面都顯示「瀏覽器已關閉 JavaScript」的 noscript 訊息，跟 ibon
 *        一樣是純前端渲染的空殼，fetch 拿不到活動資料，也沒有 JSON-LD。**不建議做。**
 *      - 業界怎麼做：Bandsintown / Ticketmaster / Eventbrite 這類大型聚合服務主要靠
 *        跟票務商簽的官方 API／合作（Ticketmaster Discovery API 公開但不含台灣/亞洲），
 *        沒有官方 API 時才會用爬蟲（含無頭瀏覽器）當退路，一樣會遇到防爬機制。目前
 *        找不到任何一家台灣售票平台有對外開放的公開讀取 API。
 *  - 只抓管理員在 crawl_sources 自己加的網址（來源由人選，責任在加來源的人）。
 *  - 抓到的一律寫成 status=pending 待審，**不自動公開**——人留在迴圈裡把關。
 *  - 有禮貌：設 User-Agent、逐來源/整批有上限、每天一次、逾時與大小上限。
 *
 * 對外只匯出 crawlAll(db)；排程與 callable 在 index.js 包一層。
 */

const MAX_SOURCES = 30;        // 一次最多處理幾個來源
const MAX_EVENTS_PER_SOURCE = 40;
const MAX_LISTING_SUBFETCH = 20;   // 列表頁一次最多展開幾個「還沒收過」的活動連結
const FETCH_TIMEOUT_MS = 15000;
const MAX_BYTES = 3 * 1024 * 1024;   // 單頁最多讀 3MB，避免抓到超大檔
const UA = "BandaryBot/1.0 (+https://bandary.web.app; concert listing aggregator)";
const RENDER_TIMEOUT_MS = 20000;   // 無頭瀏覽器單頁等待上限（比純 fetch 寬，JS 要時間跑）
const MAX_RENDER_FALLBACKS = 8;    // 整次 crawlAll() 最多開幾次無頭瀏覽器分頁（貴，設硬上限）

// ── 無頭瀏覽器（只在純 fetch 拿不到東西時才用）───────────────
// 一次 crawlAll() 共用同一個瀏覽器實例（開分頁而非重開瀏覽器，省掉重複的啟動成本）。
let browserPromise = null;
async function getBrowser() {
  if (!browserPromise) {
    browserPromise = (async () => {
      const chromium = require("@sparticuz/chromium");
      const puppeteer = require("puppeteer-core");
      const executablePath = await chromium.executablePath();
      console.log(`[crawl] launching chromium: executablePath=${executablePath}`);
      const browser = await puppeteer.launch({
        args: chromium.args,
        executablePath,
        headless: chromium.headless,
        defaultViewport: chromium.defaultViewport,
      });
      console.log("[crawl] chromium launched OK");
      return browser;
    })().catch((e) => {
      console.error("[crawl] chromium launch FAILED:", e && e.stack || e);
      browserPromise = null;   // 讓下一次呼叫可以重試，不要卡在一個失敗的 promise 上
      throw e;
    });
  }
  return browserPromise;
}

/** 用真的瀏覽器開一頁、等 JS 跑完、回傳渲染後的 HTML；失敗一律回 null（不拋錯），但一定留 log。 */
async function renderPage(url) {
  let page;
  try {
    const browser = await getBrowser();
    page = await browser.newPage();
    await page.setUserAgent(UA);
    await page.goto(url, { waitUntil: "networkidle2", timeout: RENDER_TIMEOUT_MS });
    const html = await page.content();
    console.log(`[crawl] rendered OK: ${url} (${html.length} bytes)`);
    return html;
  } catch (e) {
    console.error(`[crawl] renderPage FAILED for ${url}:`, e && e.stack || e);
    return null;
  } finally {
    if (page) { try { await page.close(); } catch (e) { /* 忽略 */ } }
  }
}

/** crawlAll() 收尾用：有開過瀏覽器才需要關，沒開過（全部靜態 fetch 就搞定）就什麼都不做。 */
async function closeBrowser() {
  if (!browserPromise) return;
  try { const b = await browserPromise; await b.close(); } catch (e) { /* 忽略 */ }
  browserPromise = null;
}

/** 取網域；網址壞掉時回空字串，不要讓一個壞來源炸掉整批。 */
function hostOf(url) {
  try { return new URL(url).hostname; } catch (e) { return ""; }
}

/** 有禮貌地抓一個網址；逾時 / 過大 / 非 2xx 都回 null（單一來源失敗不影響其他）。 */
async function politeFetch(url) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      redirect: "follow",
      signal: ctrl.signal,
      headers: { "User-Agent": UA, "Accept": "text/calendar, text/html, application/json;q=0.9, */*;q=0.5" },
    });
    if (!res.ok) return null;
    const buf = await res.arrayBuffer();
    if (buf.byteLength > MAX_BYTES) return null;
    return { text: Buffer.from(buf).toString("utf8"), contentType: res.headers.get("content-type") || "" };
  } catch (e) {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

// ── iCal 解析 ─────────────────────────────────────────────
/** 把 iCal 的折行（下一行以空白 / tab 起頭＝續行）攤平。 */
function unfoldIcal(text) {
  return text.replace(/\r\n/g, "\n").replace(/\n[ \t]/g, "");
}

/** "20260801T193000Z" / "20260801T193000" / "20260801"(整天) → { date:"2026-08-01", time:"19:30"|null }。 */
function parseIcalDate(raw) {
  const v = String(raw).trim();
  const m = v.match(/^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})?(Z)?)?/);
  if (!m) return null;
  const date = `${m[1]}-${m[2]}-${m[3]}`;
  if (!m[4]) return { date, time: null };
  // 有 Z＝UTC，轉台北（+8）；沒 Z 當成當地時間直接用。這裡只做日期/時分，夠用。
  if (m[7] === "Z") {
    const d = new Date(Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] || 0)));
    d.setUTCHours(d.getUTCHours() + 8);
    const p = (n) => String(n).padStart(2, "0");
    return {
      date: `${d.getUTCFullYear()}-${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())}`,
      time: `${p(d.getUTCHours())}:${p(d.getUTCMinutes())}`,
    };
  }
  return { date, time: `${m[4]}:${m[5]}` };
}

function parseIcal(text) {
  const t = unfoldIcal(text);
  const out = [];
  const blocks = t.split("BEGIN:VEVENT").slice(1);
  for (const b of blocks) {
    const body = b.split("END:VEVENT")[0];
    const get = (key) => {
      // 支援 SUMMARY / DTSTART;TZID=... 這種帶參數的鍵
      const re = new RegExp(`^${key}(?:;[^:\\n]*)?:(.*)$`, "mi");
      const mm = body.match(re);
      return mm ? mm[1].trim() : null;
    };
    const summary = get("SUMMARY");
    const dtstart = get("DTSTART");
    if (!summary || !dtstart) continue;
    const start = parseIcalDate(dtstart);
    if (!start) continue;
    const end = get("DTEND") ? parseIcalDate(get("DTEND")) : null;
    out.push({
      name: unescapeIcal(summary),
      date: start.date,
      time: start.time,
      endDate: end && end.date !== start.date ? end.date : null,
      endTime: end ? end.time : null,
      venue: unescapeIcal(get("LOCATION") || ""),
      sourceUrl: (get("URL") || "").trim() || null,
      note: unescapeIcal(get("DESCRIPTION") || "") || null,
    });
    if (out.length >= MAX_EVENTS_PER_SOURCE) break;
  }
  return out;
}

function unescapeIcal(s) {
  return String(s).replace(/\\n/gi, "\n").replace(/\\,/g, ",").replace(/\\;/g, ";").replace(/\\\\/g, "\\").trim();
}

// ── JSON-LD (schema.org/Event) 解析 ───────────────────────
function parseJsonLd(html) {
  const out = [];
  const re = /<script[^>]+type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi;
  let m;
  while ((m = re.exec(html)) && out.length < MAX_EVENTS_PER_SOURCE) {
    let json;
    try { json = JSON.parse(m[1].trim()); } catch (e) { continue; }
    collectEvents(json, out);
  }
  return out;
}

function collectEvents(node, out) {
  if (!node || out.length >= MAX_EVENTS_PER_SOURCE) return;
  if (Array.isArray(node)) { node.forEach((n) => collectEvents(n, out)); return; }
  if (typeof node !== "object") return;
  if (node["@graph"]) collectEvents(node["@graph"], out);
  const type = node["@type"];
  const isEvent = type && (Array.isArray(type) ? type : [type]).some((t) => String(t).toLowerCase().includes("event"));
  if (isEvent && node.name && node.startDate) {
    const ev = mapJsonLdEvent(node);
    if (ev) out.push(ev);
  }
}

function isoToParts(iso) {
  const m = String(iso).match(/^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/);
  if (!m) return null;
  return { date: `${m[1]}-${m[2]}-${m[3]}`, time: m[4] ? `${m[4]}:${m[5]}` : null };
}

function mapJsonLdEvent(n) {
  const start = isoToParts(n.startDate);
  if (!start) return null;
  const end = n.endDate ? isoToParts(n.endDate) : null;
  const loc = n.location || {};
  const venue = typeof loc === "string" ? loc : (loc.name || "");
  const addr = (loc && loc.address) || {};
  const city = typeof addr === "string" ? null : (addr.addressLocality || addr.addressRegion || null);
  // 票價：offers 可能是物件或陣列，取最低價；有 price 但為 0 → 免費。
  const offers = [].concat(n.offers || []);
  const prices = offers.map((o) => Number(o && o.price)).filter((p) => !Number.isNaN(p));
  const price = prices.length ? Math.min(...prices) : null;
  const img = Array.isArray(n.image) ? n.image[0] : (n.image && n.image.url) || n.image;
  return {
    name: String(n.name).trim(),
    date: start.date,
    time: start.time,
    endDate: end && end.date !== start.date ? end.date : null,
    endTime: end ? end.time : null,
    venue: String(venue || "").trim(),
    city: city ? String(city).trim() : null,
    ticketPrice: price,
    ticketUrl: (offers[0] && typeof offers[0].url === "string" ? offers[0].url : null),
    sourceUrl: typeof n.url === "string" ? n.url : null,
    imageUri: typeof img === "string" ? img : null,
  };
}

// ── 維基百科巡演表格 ───────────────────────────────────────
//
// 為什麼走這條路而不是 Wikidata：Wikidata 的授權（CC0）最乾淨，但覆蓋率是災難——
// 全球 8,755 筆巡演條目裡，台灣藝人的只有 1 筆；場地在台灣的單場演唱會只有 18 筆。
// 中文維基百科剛好相反：「演唱會列表」6,197 篇、「巡迴演唱會」5,736 篇，
// 而且是現成的逐場表格（場次 / 日期 / 城市 / 國家地區 / 舉辦場館）。
//
// 授權：日期、城市、場館是**事實**，不受著作權保護；有版權的是敘述文字，這裡不碰。
// 每一筆仍然記 sourceUrl 指回原條目。
//
// 抓的是 wikitext 而不是渲染後的 HTML：維基的 HTML 會隨佈景與改版浮動，
// wikitext 的表格語法十幾年沒變過，而且 API 一次就給完整內容、不用開無頭瀏覽器。

const WIKI_MAX_ROWS = 200;        // 單一條目最多收幾場（五月天那種列表條目會很長）
const WIKI_API_TIMEOUT_MS = 20000;

/** 從 https://zh.wikipedia.org/wiki/XXX 取出條目名稱。 */
function wikiTitleFromUrl(url) {
  try {
    const u = new URL(url);
    if (!/\.wikipedia\.org$/i.test(u.hostname)) return null;
    const m = /^\/wiki\/(.+)$/.exec(u.pathname);
    if (m) return decodeURIComponent(m[1]);
    const t = u.searchParams.get("title");
    return t ? decodeURIComponent(t) : null;
  } catch (e) {
    return null;
  }
}

/**
 * 去掉 wiki 標記，只留下純文字。
 * `[[臺北小巨蛋]]` → `臺北小巨蛋`、`[[台北|臺北]]` → `臺北`（顯示字優先）。
 */
function stripWiki(s) {
  return String(s || "")
    .replace(/<!--[\s\S]*?-->/g, "")
    .replace(/<ref[^>]*\/>/gi, "")
    .replace(/<ref[\s\S]*?<\/ref>/gi, "")
    .replace(/<br\s*\/?>/gi, " ")
    .replace(/<[^>]+>/g, "")
    // [[甲|乙]] 要留「乙」——那是實際顯示的字
    .replace(/\[\[([^\]|]*)\|([^\]]*)\]\]/g, "$2")
    .replace(/\[\[([^\]]*)\]\]/g, "$1")
    .replace(/\{\{[^{}]*\}\}/g, " ")
    .replace(/'''?/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/** 2011年12月23日 / 2011-12-23 / 2011.12.23 → 2011-12-23；認不出來回 null。 */
function parseWikiDate(s, fallbackYear) {
  const t = stripWiki(s);
  let m = /(\d{4})\s*[年\-./]\s*(\d{1,2})\s*[月\-./]\s*(\d{1,2})/.exec(t);
  if (m) return `${m[1]}-${String(m[2]).padStart(2, "0")}-${String(m[3]).padStart(2, "0")}`;
  // 有些表格只在該區塊開頭寫一次年份，之後的列只有「12月24日」。
  m = /(\d{1,2})\s*月\s*(\d{1,2})\s*日/.exec(t);
  if (m && fallbackYear) {
    return `${fallbackYear}-${String(m[1]).padStart(2, "0")}-${String(m[2]).padStart(2, "0")}`;
  }
  return null;
}

/** 在最外層切割（不切進 [[...]] 與 {{...}} 裡面）。 */
function splitTop(s, sep) {
  const out = [];
  let depth = 0, last = 0;
  for (let i = 0; i < s.length; i++) {
    if (s.startsWith("[[", i) || s.startsWith("{{", i)) { depth++; i++; continue; }
    if (s.startsWith("]]", i) || s.startsWith("}}", i)) { depth = Math.max(0, depth - 1); i++; continue; }
    if (depth === 0 && s.startsWith(sep, i)) { out.push(s.slice(last, i)); i += sep.length - 1; last = i + 1; }
  }
  out.push(s.slice(last));
  return out;
}

/**
 * 一格的內容與 rowspan。
 * `rowspan=7 | [[臺北]]` → { text: "臺北", rowspan: 7 }
 *
 * rowspan 是這整件事最重要的細節：表格用它讓連續七場共用同一個場館，
 * 不處理的話會解出六筆空場館——而且是靜悄悄地錯，不會有任何例外。
 */
function cellInfo(raw) {
  let rowspan = 1;
  let colspan = 1;
  let text = raw;
  let depth = 0;
  for (let i = 0; i < raw.length; i++) {
    if (raw.startsWith("[[", i) || raw.startsWith("{{", i)) { depth++; i++; continue; }
    if (raw.startsWith("]]", i) || raw.startsWith("}}", i)) { depth = Math.max(0, depth - 1); i++; continue; }
    if (depth === 0 && raw[i] === "|") {
      const attrs = raw.slice(0, i);
      // 屬性區只會有 key=value 之類的東西；看到 [[ 或 {{ 就代表這個 | 是內容的一部分。
      if (!/\[\[|\{\{/.test(attrs)) {
        const m = /rowspan\s*=\s*"?(\d+)/i.exec(attrs);
        if (m) rowspan = parseInt(m[1], 10) || 1;
        // colspan 也要算。不算的話後面每一欄都往左位移一格，
        // 「城市」會讀到場館、「場館」會讀到暖場嘉賓——而且是靜悄悄地錯。
        const c = /colspan\s*=\s*"?(\d+)/i.exec(attrs);
        if (c) colspan = parseInt(c[1], 10) || 1;
        text = raw.slice(i + 1);
      }
      break;
    }
  }
  return {
    text: stripWiki(text),
    rowspan: Math.min(rowspan, 60),
    colspan: Math.min(colspan, 20),
  };
}

/** 把 wikitext 裡每一個 {| ... |} 表格切出來（會跳過 {{模板}}，避免提早結束）。 */
function extractWikiTables(wt) {
  const tables = [];
  let i = 0;
  while (i < wt.length) {
    const start = wt.indexOf("{|", i);
    if (start < 0) break;
    let j = start + 2, depth = 1;
    while (j < wt.length && depth > 0) {
      if (wt.startsWith("{{", j)) {
        let td = 1; j += 2;
        while (j < wt.length && td > 0) {
          if (wt.startsWith("{{", j)) { td++; j += 2; }
          else if (wt.startsWith("}}", j)) { td--; j += 2; }
          else j++;
        }
        continue;
      }
      if (wt.startsWith("{|", j)) { depth++; j += 2; continue; }
      if (wt.startsWith("|}", j)) { depth--; j += 2; continue; }
      j++;
    }
    tables.push(wt.slice(start, j));
    i = j;
  }
  return tables;
}

/** 一個表格 → 每列的格子陣列（已處理 rowspan 往下帶）。 */
function parseWikiTable(tableText) {
  const lines = tableText.split("\n");
  const rawRows = [];
  let cur = null;
  for (let k = 1; k < lines.length; k++) {
    const t = lines[k].trim();
    if (t.startsWith("|-")) { if (cur) rawRows.push(cur); cur = []; continue; }
    if (t === "|}") break;
    if (t.startsWith("|+")) continue;                 // 表格標題
    if (t.startsWith("!") || t.startsWith("|")) {
      if (!cur) cur = [];
      const sep = t.startsWith("!") ? "!!" : "||";
      for (const piece of splitTop(t.slice(1), sep)) cur.push(cellInfo(piece));
    } else if (cur && cur.length && t) {
      // 續行：接回上一格（表格裡的多行內容）
      const last = cur[cur.length - 1];
      last.text = stripWiki(last.text + " " + t);
    }
  }
  if (cur) rawRows.push(cur);

  // rowspan 往下帶
  const carry = [];
  return rawRows.map((cells) => {
    const out = [];
    let ci = 0, si = 0;
    while (si < cells.length || (carry[ci] && carry[ci].left > 0)) {
      if (carry[ci] && carry[ci].left > 0) {
        out[ci] = carry[ci].text;
        carry[ci].left--;
        ci++;
        continue;
      }
      const c = cells[si++];
      out[ci] = c.text;
      if (c.rowspan > 1) carry[ci] = { text: c.text, left: c.rowspan - 1 };
      ci++;
      // 跨欄的格子要把後面的欄位空出來，不然整列會左移。
      for (let k = 1; k < c.colspan; k++) { out[ci] = ""; ci++; }
    }
    return out;
  });
}

/** 欄名 → 我們要的欄位。只認得出來的才用，認不出的欄位直接忽略。 */
function wikiColumnField(header) {
  const h = String(header || "");
  if (/日期|時間|date/i.test(h)) return "date";
  if (/場館|場地|地點|venue/i.test(h)) return "venue";
  if (/城市|都市|city/i.test(h)) return "city";
  if (/國家|地區|country/i.test(h)) return "country";
  return null;
}

/**
 * 條目 wikitext → 原始活動列表（欄位對齊 mapJsonLdEvent 的輸出）。
 *
 * [title] 當場次名稱：巡演條目的標題就是巡演名稱。
 */
function parseWikiTourTables(wikitext, title, pageUrl) {
  const out = [];
  const artist = wikiArtistOf(wikitext);
  for (const table of extractWikiTables(wikitext)) {
    const rows = parseWikiTable(table);
    if (!rows.length) continue;

    // 找表頭：第一列裡認得出「日期」的那一列。沒有就不是場次表，跳過。
    let headerIdx = -1, fields = null;
    for (let r = 0; r < Math.min(rows.length, 3); r++) {
      const f = rows[r].map(wikiColumnField);
      if (f.includes("date")) { headerIdx = r; fields = f; break; }
    }
    if (headerIdx < 0) continue;

    let year = null;
    for (let r = headerIdx + 1; r < rows.length; r++) {
      if (out.length >= WIKI_MAX_ROWS) return out;
      const row = rows[r];
      // 區塊標題列（例如「末日狂歡版」）只有一格，沒有日期，跳過。
      if (row.filter(Boolean).length < 2) continue;

      const get = (name) => {
        const i = fields.indexOf(name);
        return i >= 0 ? (row[i] || "") : "";
      };
      const date = parseWikiDate(get("date"), year);
      if (!date) continue;
      year = date.slice(0, 4);

      const venue = get("venue");
      const city = get("city");
      if (!venue && !city) continue;   // 連在哪都不知道的列沒有價值

      out.push({
        name: title,
        artistName: artist,
        date,
        time: null,
        endDate: null,
        endTime: null,
        venue,
        city: city || null,
        country: get("country") || null,
        ticketPrice: null,
        ticketUrl: null,
        sourceUrl: pageUrl,
        imageUri: null,
      });
    }
  }
  return out;
}

/** 從 infobox 撈演出者。撈不到就留空，讓管理員審核時補。 */
function wikiArtistOf(wikitext) {
  const m = /\|\s*(?:artist|藝人|歌手|演出者|表演者)\s*=\s*([^\n|]+)/i.exec(wikitext);
  return m ? stripWiki(m[1]) : "";
}

/** 抓一篇維基條目的 wikitext（走 API，不抓渲染後的 HTML）。 */
async function fetchWikiEvents(source) {
  const title = wikiTitleFromUrl(source.url);
  if (!title) return [];
  const host = new URL(source.url).hostname;
  const api =
    `https://${host}/w/api.php?action=parse&prop=wikitext&formatversion=2&format=json&page=` +
    encodeURIComponent(title);
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), WIKI_API_TIMEOUT_MS);
  try {
    const res = await fetch(api, { headers: { "User-Agent": UA }, signal: ctrl.signal });
    if (!res.ok) return [];
    const json = await res.json();
    const wt = json && json.parse && json.parse.wikitext;
    if (!wt) return [];
    return parseWikiTourTables(wt, (json.parse.title || title), source.url);
  } catch (e) {
    return [];
  } finally {
    clearTimeout(timer);
  }
}

// ── 活動列表頁 → 個別活動連結 ─────────────────────────────
/**
 * 從一頁 HTML 裡找出「看起來是單一活動頁」的連結，且跟來源頁同網域（不跟去別的網站）。
 * 只認 href，不猜版面，改版對這個影響很小。涵蓋幾個常見台灣售票平台的活動頁網址形狀：
 * KKTIX 的 /events/{slug}、ACCUPASS 的 /event/{id}、tixCraft／iNDIEVOX 的
 * /activity/detail/{code}。多寫幾種形狀不會誤傷——不符合的網站這裡就是找不到連結。
 */
const EVENT_LINK_RE = /\/(events?|activity\/detail)\/[a-z0-9_-]+\/?$/i;
function extractEventLinks(html, baseUrl) {
  let origin;
  try { origin = new URL(baseUrl).origin; } catch (e) { return []; }
  const re = /<a\b[^>]*href=["']([^"'#]+)["'][^>]*>/gi;
  const out = [];
  const seen = new Set();
  let m;
  while ((m = re.exec(html))) {
    let absUrl;
    try { absUrl = new URL(m[1], baseUrl); } catch (e) { continue; }
    // 用 origin 屬性比對，不能用字串 startsWith——"https://a.com".startsWith 對
    // "https://a.com.evil.com" 也會是 true（字首相同、host 完全不同），這樣同源限制
    // 就形同虛設，會把攻擊者控制的網域當「同一個來源」去抓、甚至送進無頭瀏覽器渲染。
    if (absUrl.origin !== origin) continue;
    const abs = absUrl.href;
    if (!EVENT_LINK_RE.test(abs)) continue;
    if (seen.has(abs)) continue;
    seen.add(abs);
    out.push(abs);
  }
  return out;
}

// ── 一頁 html → 活動列表（iCal / 直接 JSON-LD / 展開列表頁連結）─────
async function parseFetchedPage(text, contentType, source, existingUrls, state) {
  const looksIcal = /text\/calendar/i.test(contentType) || /^BEGIN:VCALENDAR/m.test(text);
  if (source.type === "ical" || (source.type !== "jsonld" && looksIcal)) return parseIcal(text);

  const direct = parseJsonLd(text);
  if (direct.length || source.type === "jsonld") return direct;

  // 這頁本身沒有 JSON-LD：試著當「活動列表頁」（例如 KKTIX 主辦方自己的頁面）——
  // 找出頁面上的活動連結，只展開「還沒收過」的，逐一開來看有沒有 JSON-LD。
  // 不是這種頁面的話 extractEventLinks 就是回傳空陣列，直接跳過，沒有副作用。
  const links = extractEventLinks(text, source.url).filter((u) => !existingUrls.has(u));
  const out = [];
  for (const link of links.slice(0, MAX_LISTING_SUBFETCH)) {
    if (out.length >= MAX_EVENTS_PER_SOURCE) break;
    const sub = await politeFetch(link);
    let subEvents = sub ? parseJsonLd(sub.text) : [];
    // 個別活動頁自己也可能是空殼（例如 iNDIEVOX 的活動頁）：一樣有額度才試著渲染。
    if (!subEvents.length && state.renderCount < MAX_RENDER_FALLBACKS) {
      state.renderCount++;
      const rendered = await renderPage(link);
      if (rendered) subEvents = parseJsonLd(rendered);
    }
    out.push(...subEvents);
  }
  return out;
}

// ── 來源 → 原始活動列表 ───────────────────────────────────
async function fetchSource(source, existingUrls, state) {
  // 維基條目走 API 拿 wikitext，不走 politeFetch + JSON-LD 那條路
  // （維基頁面沒有 schema.org/Event，照原流程跑只會一路退到無頭瀏覽器然後拿到空的）。
  if (source.type === "wikipedia" || /\.wikipedia\.org$/i.test(hostOf(source.url))) {
    return await fetchWikiEvents(source);
  }
  const fetched = await politeFetch(source.url);
  const raws = fetched
    ? await parseFetchedPage(fetched.text, fetched.contentType, source, existingUrls, state)
    : [];
  if (raws.length) return raws;
  if (state.renderCount >= MAX_RENDER_FALLBACKS) return raws;

  // 靜態抓取（含展開列表連結）什麼都沒拿到：退一步用無頭瀏覽器把這頁重新開一次，
  // 等 JS 跑完再照同一套邏輯解析。渲染失敗（例如被 WAF 擋下）就維持原本的空結果。
  state.renderCount++;
  const rendered = await renderPage(source.url);
  if (!rendered) return raws;
  return await parseFetchedPage(rendered, "text/html", source, existingUrls, state);
}

// ── 卡司字串正規化 ─────────────────────────────────────────
//
// 爬蟲從不同來源抓到的卡司分隔符五花八門：「、」「｜」「／」「 / 」、換行、
// 甚至沒有空白的「/」。而 App 端的 castLabel() 只認 " / "，
// 所以分隔符不對的那些，在畫面上就是一整串黏在一起、嘉賓也對不上主藝人
// （實機回報「藝人沒被分開的是爬蟲來的活動」）。
//
// 規則刻意跟 App 的 splitArtistNamesPasted 對齊：
//   - 認 ｜ | 、 ／ ; ； 換行，以及**前後有空白**的半形斜線
//   - 單一空格不拆：很多團名自己就帶空格（The Dinoaur'y skin）
//   - 沒空白的「/」不拆：AC/DC、7/18 會被切壞
const CAST_SEPARATORS = /(?:[｜|、／;；\r\n]|\s+\/\s+)+/;

function normalizeCast(raw) {
  if (!raw) return "";
  const parts = String(raw)
    .split(CAST_SEPARATORS)
    .map((x) => x.trim())
    // 「7/18：」這種日期前綴在卡司表上很常見，當成雜訊去掉。
    .map((x) => x.replace(/^\s*\d{1,2}\s*\/\s*\d{1,2}\s*[:：]?\s*/, "").trim())
    .filter((x) => x && !/^\s*\d{1,2}\s*\/\s*\d{1,2}\s*$/.test(x));
  return Array.from(new Set(parts)).join(" / ");
}

/**
 * 往前補：把既有 free_events 裡分隔符不對的 artistName 改成 " / "。
 *
 * 每次 crawlAll 跑完都做一次，所以是**自動**的——不需要管理員記得去後台按按鈕。
 * 只動分隔符與空白、不動任何名字，正規化後與原值相同的不寫回，
 * 所以重複執行不會愈跑愈歪，也不會白白製造一整個集合的寫入。
 */
async function normalizeExistingCast(db) {
  const snap = await db.collection("free_events").get();
  let fixed = 0;
  let batch = db.batch();
  let inBatch = 0;
  for (const doc of snap.docs) {
    const raw = doc.get("artistName");
    if (!raw) continue;
    const normalized = normalizeCast(raw);
    if (!normalized || normalized === raw) continue;
    batch.update(doc.ref, { artistName: normalized });
    fixed++;
    inBatch++;
    // Firestore 單批上限 500 筆寫入。
    if (inBatch >= 400) {
      await batch.commit();
      batch = db.batch();
      inBatch = 0;
    }
  }
  if (inBatch > 0) await batch.commit();
  return fixed;
}

// ── 對齊 free_events 的文件格式（與 App 的 freeEventFromMap 一致）─────
function toFreeEventDoc(raw, source, id, crawlKey) {
  const time = raw.time || "00:00";
  return {
    id,
    name: raw.name,
    // 寫進去的時候就正規化，新抓到的不會再有分隔符不對的問題。
    artistName: normalizeCast(raw.artistName),
    guestsByArtist: {},
    venue: raw.venue || "",
    city: raw.city || null,
    // 維基的巡演表格有「國家/地區」欄；FreeEvent 本來就有這個欄位，
    // 收日韓場次時用得到。其他來源解不出來就是 null，與既有行為一致。
    country: raw.country || null,
    dateTime: `${raw.date}T${time}`,
    endDate: raw.endDate || null,
    endTime: raw.endTime || null,
    ticketPrice: (raw.ticketPrice === 0 || raw.ticketPrice > 0) ? raw.ticketPrice : null,
    priceText: null,
    ticketUrl: raw.ticketUrl || null,
    saleTimes: [],
    note: raw.note || null,
    sourceUrl: raw.sourceUrl || source.url,
    imageUri: raw.imageUri || null,
    imageUris: raw.imageUri ? [raw.imageUri] : [],
    status: "pending",
    reviewComment: null,
    createdBy: `crawler:${source.label || source.url}`,
    createdAt: Date.now(),
    editOfId: null,
    crawlKey,          // 去重用：同一場再抓到不會重複建
  };
}

/** 去重鍵：名稱正規化 + 日期。與哪個來源無關，避免兩個來源抓到同一場各建一筆。 */
function dedupeKey(name, date) {
  return `${String(name).trim().toLowerCase().replace(/\s+/g, " ")}|${date}`;
}

/**
 * 主流程：讀 crawl_sources（active）→ 逐一抓取解析 → 去重 → 寫 pending。
 * 回傳統計摘要（給 callable 顯示）。用 Admin SDK 寫入，不受前端規則限制。
 */
async function crawlAll(db) {
  const today = new Date();
  const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;

  const srcSnap = await db.collection("crawl_sources").where("active", "==", true).limit(MAX_SOURCES).get();
  const sources = srcSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  if (!sources.length) return { sources: 0, added: 0, skipped: 0, note: "沒有啟用中的來源" };

  // 既有活動的去重鍵（含各狀態，避免重覆建 / 重覆送審已被退回的）。
  // existingUrls 另外給「活動列表頁」用：同一個活動連結已經開過一次，
  // 之後每天重跑就不用再開，禮貌一點也省執行時間。
  const existing = new Set();
  const existingUrls = new Set();
  const evSnap = await db.collection("free_events").get();
  evSnap.forEach((d) => {
    const e = d.data();
    if (e.crawlKey) existing.add(e.crawlKey);
    if (e.name && e.dateTime) existing.add(dedupeKey(e.name, String(e.dateTime).slice(0, 10)));
    if (e.sourceUrl) existingUrls.add(e.sourceUrl);
  });

  // renderCount：這次執行全部來源共用的無頭瀏覽器額度（見檔頭說明）。
  const state = { renderCount: 0 };
  let added = 0, skipped = 0;
  try {
    for (const source of sources) {
      let raws = [];
      try { raws = await fetchSource(source, existingUrls, state); } catch (e) { raws = []; }
      // 維基來源的重點就是**過去**的場次，不能套「只收今天以後」那條規則，
      // 否則整張巡演表會被全部丟掉（五月天 2011 年那場就是這樣消失的）。
      const allowPast = source.type === "wikipedia" ||
        /\.wikipedia\.org$/i.test(hostOf(source.url));
      for (const raw of raws) {
        if (!raw.name || !raw.date) { skipped++; continue; }
        if (!allowPast && raw.date < todayIso) { skipped++; continue; }
        const key = dedupeKey(raw.name, raw.date);
        if (existing.has(key)) { skipped++; continue; }
        existing.add(key);
        const ref = db.collection("free_events").doc();
        await ref.set(toFreeEventDoc(raw, source, ref.id, key));
        added++;
      }
      // 記一下這個來源最後抓取時間 / 筆數，管理端看得到。
      try {
        await db.collection("crawl_sources").doc(source.id).set(
          { lastRunAt: Date.now(), lastCount: raws.length }, { merge: true });
      } catch (e) { /* 忽略 */ }
    }
  } finally {
    await closeBrowser();   // 這次執行有開過無頭瀏覽器才需要關；沒開過就是 no-op。
  }
  // 往前補既有資料的卡司分隔符。放在最後、各自 try：
  // 這是整理既有資料，失敗不該讓「這次抓到幾筆」這個主要結果一起不見。
  let castFixed = 0;
  try {
    castFixed = await normalizeExistingCast(db);
  } catch (e) {
    console.warn("normalizeExistingCast failed", e);
  }
  return { sources: sources.length, added, skipped, rendered: state.renderCount, castFixed };
}

module.exports = {
  crawlAll, parseIcal, parseJsonLd, dedupeKey, extractEventLinks, normalizeCast,
  // 維基解析：匯出給測試用，解析邏輯不碰網路就驗得了。
  parseWikiTourTables, parseWikiTable, parseWikiDate, stripWiki, wikiTitleFromUrl,
};
