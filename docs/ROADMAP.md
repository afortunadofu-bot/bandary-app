# Bandary 下階段開發藍圖（ROADMAP）

> 建立日期：2026-07-08（同日補入智慧新增 / 藝文活動四項）；2026-07-09 補入 11 項新需求並整理。
> 來源：使用者 2026-07-08 待辦（19+4 項）+ 2026-07-09 待辦（11 項）+ 改版總規格書殘留項。
> 本文件是下一階段的執行依據：先分類與排序，開工時逐批執行、逐批 compile 驗證。

---

## 待辦：活動的國家篩選器

「台灣活動」已於 v1.19.1 更名為「活動」，為之後支援跨國活動預留。尚未做的是**篩選器本身**：

- `FreeEvent` 目前**沒有 `country` 欄位**（只有 `city`）。要加，並比照 `priceText` 的做法讓舊資料讀回來是 null。
- 舊資料全部是台灣場 → 遷移時視為「台灣」，不要留空，否則篩「台灣」會整批消失。
- 篩選器要同時出現在 Android 的活動頁與 PWA 的活動分頁，兩邊選項一致。
- 與現有的縣市欄位關係要想清楚：選了非台灣的國家時，縣市選單的 22 個台灣縣市就不該再是建議值。

---

## 執行順序總覽（含狀態）

| 批次 | 主題 | 狀態 |
|------|------|------|
| **A** | 錯誤修正（6 項） | ✅ 完成（2026-07-08） |
| **B** | 小型 UX 改善（7 項） | ✅ 完成（2026-07-08） |
| **C** | 主題色回歸 + 清單版面 | ✅ 完成（2026-07-08） |
| **D** | 分享管線大改版 | 🔄 `PosterRenderer` 本體 + 5 TypeStyle 已完成；**票根已於 2026-07-13（續13）整檔改用 PosterRenderer**；統計分享 + 里程碑 / Wrapped 也都走它。唯一未切＝**演唱會詳情頁 hero**（刻意用 AsyncImage 照片輪播，非海報渲染）。剩實機看圖微調座標 |
| **K** | 統計強化（新，2026-07-09） | ✅ 完成（2026-07-09）：只計已看完、歌曲→日期紀錄、分享/下載分開 |
| **L** | 好友互動 v2（新，2026-07-09） | ✅ 程式碼完成；使用者回報好友 v3 rules 已部署（2026-07-10）→ 可實機驗證（含 E2-11/12 社交通知） |
| **M** | 小型 UX 二批（新，2026-07-09） | ✅ 完成（2026-07-09）：M-1~M-4 全部完成 |
| **E** | 成長與留存（Aha + Streak） | ✅ 完成（2026-07-09）：E-1~E-5 全部完成（onboarding / ghost card / streak 彈跳 / 里程碑徽章 / 挽救推播） |
| **E2** | 留存機制二批（新，2026-07-16） | ✅ **全數完成（2026-07-16）**：A 組通知觸發 + B 組 E2-5/7/8 + C 組 E2-4/9/10 + D 組 E2-11/12（社交需好友 rules 實機驗證）。詳見 Batch E2 |
| **F** | 後台與營運 | ⏳ 純 Firebase Console 操作：部署 users 讀取規則（修「尚未取得使用者資料」）+ 內建折扣碼 seed 進雲端。程式端已就緒 |
| **G** | 規格書殘留項收尾 | ⏳ Typography（真字型）/ 8pt 間距 / 翻牌倒數動效 / 同場推薦（E2-11 已做同日+場地比對，可視為前置） |
| **H** | 智慧新增與外部整合 | ⏳ 海報判讀 / Spotify / 圖搜裁切——**皆卡決策 5/6/7（金鑰策略、裁切方案、圖搜版權）未定** |
| **I** | 活動類型擴展（藝文活動 / 表演） | 🔄 I-1 第一階段完成（2026-07-16，續35：ConcertType 加展覽/舞台劇/音樂劇/脫口秀）；**未做**全站「演唱會」字樣 audit / 是否分頁籤混計（決策⑧） |
| **J** | PWA / 網頁版 + 可下載 app 版 | ⏳ 使用者指示：全部做好後才做。尚未開工 |

> 建議接續順序：先清 **K（統計強化，多為小改+一個統計 bug）** 與 **M（小型 UX）**，再視好友規則是否部署決定 **L**；D 的 PosterRenderer 與 E 動效適合一起在真機上做。

---

## ⚠️ 開工前需確認的決策（有衝突或語意不明）

1. ~~主題色（C-1）~~ ✅ **已定案（2026-07-08）**：四款預設色（灰/粉/綠/藍）**保留**，預設值從莫蘭迪灰改為**莫蘭迪綠**；使用者可**新增自訂色**，色盤總上限 **6 格**（即自訂最多 2 個；若實作時想放寬再調）。自訂色需要重新引入輕量取色 UI（1-2 已刪 ColorPicker.kt——重做時用小而穩的版本：預選 swatch + hex 輸入，避免當初的複雜 HSV 面板）。舊偏好遷移：未選過（null）→ 綠；已明確選灰者維持灰。
2. ~~取文字方式（D-1）~~ ✅ **已定案（2026-07-08）**：使用者提供三組海報範例（固定網格雙欄活動海報 / 龐門正道大字出血直排 / 「10 種排版方式」中英混排），確認「n 種取文字方式」= **同一份內容、多種文字構圖樣式**。完整設計規劃見 Batch D 的「海報版面系統設計」節。
3. ~~清單白框（C-3）~~ ✅ **已定位（2026-07-08 實機截圖）**：是**無封面圖**的清單列（例：公館青少年 GGteens 那列）——fallback 漸層底 + 半透明壓層在淺色主題下形成突兀的灰白框。修正方向：無圖列改用主題色推導的純色深底、文字直接放（或文字加 shadow），**不再用半透明壓層**。
4. ~~後台數據（F-1）~~ ✅ **已定案（2026-07-08）**：照建議——先實機驗證急修 4 的 globalStats 哪些缺、哪些壞，再補作，不重寫。
5. **自訂裁切（H-3）與 2026-07-01 的決策衝突**：當時「徹底移除裁切器」就是為了消除閃退源頭。重新引入必須換方案（如 uCrop / Android 內建 crop intent / Compose 自製輕量裁切），並先確認當時閃退的根因，避免重蹈覆轍。
6. **Spotify API 金鑰策略（H-2）**：Client Credentials flow 需要 client secret——放 APK 裡會被拆包盜用；PKCE flow 不用 secret 但要求使用者本人登入 Spotify。需選：(a) PKCE + 使用者登入、(b) 自架輕量代理保管 secret、(c) 接受風險內嵌。影響架構與成本。
7. **Google 圖片搜尋選型（H-3）**：官方 Custom Search JSON API 免費額度僅 100 次/日（超過 $5/千次），且需處理圖片版權（僅供個人記錄用途可註明）。替代：只搜特定授權、或改為「開瀏覽器搜尋 + 分享回 App」的零 API 方案。
8. **藝文活動範圍（I-1）**：要涵蓋哪些類型（展覽 / 舞台劇 / 音樂劇 / 脫口秀 / 電影場次…）？統計、文案（「演唱會」字樣全站出現）、篩選是否把藝文活動與演唱會分開顯示？決定資料模型走「擴充 ConcertType」還是「新增第二種活動實體」。
9. **「已看完」的判定（K-1）**：統計只計已看完的表演——用「演出日期 < 今天」判定，還是需要使用者手動標記「已看」？多日活動以結束日為準。先採「日期已過」最省事，若要更精準再加手動標記。
10. **「有興趣的活動」改名（M-1）**：目前只有免費活動能標「有興趣」。改名「有興趣的活動」是否也代表要讓一般演唱會 / 未來的藝文活動也能標「有興趣」？還是只是把免費活動那顆的文案去掉「免費」二字？先確認範圍。

---

## 使用者 2026-07-09 加開項目（/goal 追加，K 之後直接執行）

| 項目 | 狀態 |
|------|------|
| 照片重開 App 消失（本機圖 URI 失效）→ 改存 App 私有空間 + 自動修復 | ✅ 完成 |
| 複製演唱會日期跑掉 | ✅ 完成 |
| 編輯演唱會：地址欄重排（國家+縣市同列→場地→地址） | ✅ 完成 |
| 選日期可跳到月份 | ✅ 完成（主日期選擇器，其餘日期欄未做） |
| 藝人拖曳排序卡頓 → 改邊拖邊即時交換 | ✅ 完成（實機手感待驗證） |
| 我的演唱會三種顯示模式 + 清單顯示藝人 | ✅ 完成（MASONRY/BLOCKS 兩種卡片式本已存在，補了 LIST 顯示藝人） |
| 上傳照片可貼剪貼簿圖片 | ✅ 完成（新增演唱會的多照片區） |
| 備註可按暫存 | ✅ 完成（新增演唱會的備忘錄區，沿用同一儲存流程但不跳走） |
| 使用者可選起始畫面分頁 | ✅ 完成（設定 → 起始畫面，含「我的」） |
| 演唱會歌單可用圖片掃描 | ✅ 完成（沿用 TimetableOcrService，歌單區塊每位藝人各自一顆「掃歌單」） |
| 新增演唱會表單改清單式 / 極簡 / 無邊框 + 半彈窗 | ✅ 完成（2026-07-09→10）：`FormSection` 重做成清單式列（標題左 / 輸入右 / 每列細分隔線），複雜/多欄位區塊用 `wide` 變體（標題在上、內容占滿整寬，同樣加分隔線）；無邊框輸入（聚焦才顯主題色框）；縣市半彈窗；圖片預覽限半寬；暫存改明顯填色按鈕。依 2026-07-10 實機截圖回饋調整 |

---

## 新增待辦（2026-07-09）分批詳表

### Batch K — 統計強化
| # | 項目 | 檔案線索 | 備註 |
|---|------|---------|------|
| K-1 | 演出統計只計**已看完**的表演（排除未來場次） | `StatsViewModel.data`（`inPeriod` 過濾後再加「日期已過」條件） | 見決策 ⑨。多日以結束日為準；「未來」場次不進場次數 / 花費 / 藝人等統計 |
| K-2 | 歌曲統計可點進「曾選這首歌的那幾天」，逐日看紀錄 | `StatsScreen` 選歌區 `SongTitleRankRow` → 需可點；`DailySongRepository` 查該歌所有日期 → 導到選歌紀錄頁 | 需要一個「某歌的歷史日期清單」畫面或彈窗，點日期進當日紀錄 |
| K-3 | 統計分享改「**手動下載按鈕**」，不要按分享就自動存本機 | `StatsScreen` ShareSheet 匯出流程（目前 `renderStatsBitmap` → `BitmapExporter.saveAndShare` 會存檔） | 分享與「下載到本機」拆成兩顆：分享=系統分享單、下載=另存。票根分享也一併檢查一致 |

### Batch L — 好友互動 v2（接在 1-4 好友 v3 之後）✅ 程式碼完成（2026-07-09），待 rules 部署後實機驗證
| # | 項目 | 檔案線索 | 備註 |
|---|------|---------|------|
| L-1 | 跟朋友**標記彼此想搶的票**（同一場想搶→互相看得到） | `SharedShow.wantTicket`（新欄位，`FriendsRepository.publishMyCalendar` 依 `ticketStatus in {WISHLIST, LOTTERY}` 推導，免另外加 UI——沿用既有購票狀態欄位） | ✅ 完成。依賴好友 v3 rules 已部署 |
| L-2 | 看得到**朋友想搶哪些票、想去什麼活動** | `SharedCalendar.interestedFreeEventIds`（新欄位，發布時帶入 `SettingsDataStore.interestedEvents`）；`FriendCalendarScreen` 新增「想搶的票」「想去的活動」兩個區塊（後者對照 `FreeEventsRepository.events` 解析出活動資訊） | ✅ 完成。需好友都按過「發布」才看得到彼此最新狀態 |

> L 依賴：好友 v3 的 Firestore rules 必須先部署（CONSOLE_SETUP.md diff）。與 G-4「同場推薦」同屬好友延伸，可一起做。

### Batch M — 小型 UX 二批
| # | 項目 | 檔案線索 | 備註 |
|---|------|---------|------|
| M-1 | 「有興趣的**免費**活動」→「有興趣的活動」 | `FreeEventsScreen` / `CalendarScreen` 文案；`interestedEvents` | 見決策 ⑩：先確認只是改文案、還是要擴到一般活動 |
| M-2 | 記住「我的演唱會」**上次停留的分類 / 分頁**，退出後回到該分頁 | `ShowsScreen` 的 `scope` / `display` state → 提升到 DataStore 或 rememberSaveable | 若只需 app 生命週期內記住用 rememberSaveable；跨啟動記住用 DataStore |
| M-3 | 演唱會**圖片可左右滑** | ✅ 完成（2026-07-10）：`ConcertDetailScreen` hero 改 `HorizontalPager`（封面 + photoUris），多張顯示頁點，點圖仍開全螢幕 SwipeableImageViewer |
| M-4 | 票根分享**字體自適應**，讓演唱會名稱 + 藝人名單完整顯示 | ✅ 完成：`drawAutoFitText` 逐字換行 + 依可用高度自動縮字級；2026-07-10 再加**手動字級滑桿**（0.7–1.4x，預覽與匯出同步） |

### 併入既有批次的新項目
- **F（後台）+ 一項**：修「**尚未取得使用者資料。請確認 Firestore 規則已允許管理者讀取 users**」——這是後台讀 `users` 被規則擋下的訊息。解法＝在 Firebase Console 部署 CONSOLE_SETUP.md 的 `users` 讀取規則（把管理者 email 填進去發布）。屬營運設定，非程式 bug；F-1 驗證時一併處理。
- **J（PWA）改寫**：使用者要「**同時有網頁版 + 可下載的 app 版**」。app 版（Android APK / AAB）本來就可下載上架；網頁版 = PWA（獨立 web app 共用同一套 Firestore）。J 的目標明確化為「上架可下載的 app + 一個 PWA 網頁版」，前置條件不變（schema 穩定 + rules 部署）。

---

## Batch A — 錯誤修正　✅ 已完成 + compile 綠燈（2026-07-08）

| # | 項目 | 結果 |
|---|------|------|
| A-1 | 演唱會頁面時間篩選 | 經查 scope（全部/今年/月份）過濾邏輯本身正確；「今年≈全部」是因使用者資料全在 2026（單一年份時本就相同）。真正的缺陷是「過去/未來」以本月為固定錨、與年/月 scope 疊加會空掉或看似無效 → 改為：選年/月時不再套用過去/未來（該區間即時間篩選），過去/未來 chips 僅在「全部」下顯示 |
| A-2 | 免費活動月曆切月份，下方清單不更新 | ✅ 切月/選月/今天鈕都呼叫 `syncSelected()` 同步選取日到新月份，`dayEvents` 隨之更新 |
| A-3 | 卡片地點被蓋住跑版 | ✅ 定位為短卡片（橫式封面≈170dp）時 bottom info Column 被卡片高度限制、末端的地點被裁；藝人列 2 行→1 行釋出空間 |
| A-4 | 備份跑很久 | ✅ 演唱會/選歌改 WriteBatch（≤450/批，`commitBatched` helper）；圖片壓縮+上傳改 Semaphore(4) 有界並行；刪除亦批次化。增量（sha1 跳過已上傳）本已存在 |
| A-5 | 售票主頁多網址→單一 | ✅ AddShowScreen `ticketUrls: List` → `ticketUrl: String`；ConcertDetailScreen 單一按鈕；舊 `\n` 多網址取第一個 |
| A-6 | 登入不能換帳號 | ✅ CloudSyncViewModel `switchAccount`→`signIn`（先 `firestore.signOut()` 清 Google 連線再 launch）；ProfileScreen 三個入口（頭像列/登入鈕/切換帳號）全走此路徑。「登入更好找」仍歸 B-1 |

## Batch B — 小型 UX 改善　✅ 已完成 + compile 綠燈（2026-07-08）

| # | 項目 | 結果 |
|---|------|------|
| B-1 | 登出確認 / 帳號切換 / 登入更好找 | ✅ 登出加確認對話框（同時清 Firebase/Google + 本機 pro/admin）；未登入時頁首下方新增明顯的「用 Google 登入」CTA 卡片；切換帳號沿用 A-6 的 `signIn`（先登出再選帳號） |
| B-2 | 新增演唱會區塊預設摺疊 + 欄位摘要 | ✅ 三大區塊（演唱會資訊/售票資訊/感想紀錄）預設收合；GroupHeader 加 `summary` 參數，收合時列出可填欄位 |
| B-3 | 詳情頁密度 | ✅ LazyColumn 間距 12→8dp、InfoRow 垂直 padding 8→5dp，減少中間空行。（若「藝人版面」另指特定元件，待實機指認） |
| B-4 | 藝人列排法 | ✅ 移除上/下移按鈕（改長按拖曳）；「分日期」由獨佔一列改為 row 內緊湊按鈕 |
| B-5 | 拖動體驗 | ✅ 拖曳中該列 zIndex 抬起 + `graphicsLayer translationY` 跟手指移動 + 底色高亮，放開才重排 |
| B-6 | 統計摺疊 | ✅ 新增 `StatSectionHeader`（標題+項數+箭頭）；場館/縣市/樂手/花費/平台/選歌藝人/選歌歌曲 7 段可整段收合，預設收起 |
| B-7 | 免費活動有興趣→月曆 | ✅ CalendarViewModel 注入 FreeEventsRepository+SettingsDataStore；月曆格對有興趣免費活動標紫底/紫點+圖例；當日清單顯示免費活動卡片（紫）+「加入」鈕一鍵轉演唱會（`freeRepo.join`）；進頁自動 refresh |

## Batch C — 主題色回歸 + 清單版面　✅ 已完成 + compile 綠燈（2026-07-08）

| # | 項目 | 結果 |
|---|------|------|
| C-1 | 主題色回歸 + 預設莫蘭迪綠 + 自訂至 6 色 | ✅ 依決策①：保留 4 預設、預設由灰改**綠**（fromStored 未知→GREEN）；ThemePalette 重加 CUSTOM；SettingsDataStore 重加 customColorHex + customColors（上限 2）+ selectCustomColor/addCustomColor/removeCustomColor；Theme/MainActivity/SettingsViewModel 接回 customColorHex；ProfileScreen 調色盤 = 4 預設 + 自訂色（長按移除）+ 新增鈕（開 ColorPickerDialog，重建 ColorPicker.kt）。**註**：全站色彩 token 化（硬編碼色 audit）仍屬 D 前置，未在此批做 |
| C-2 | 卡片加寬 / BLOCKS 塞兩張 | ✅ MASONRY 2 欄 staggered → **單欄** LazyColumn（卡片≈全寬，高=寬×封面比，共用 listState）；BLOCKS 高度改由 screenHeightDp 推算讓可視區約放兩張。**尺寸需實機微調** |
| C-3 | 清單白色半透明框 | ✅ 定位為「無封面」清單列的淺莫蘭迪漸層 + 弱 scrim 造成的淺框；改為該場莫蘭迪底色偏深（lerp→black 0.35）的純色底，白字清楚、無半透明框 |

## Batch D — 分享管線大改版（最大工程，依賴 C-1）

**目標**（單一項，內含多子工作）：演唱會統計、票根、演唱會頁等所有有分享鈕的頁面，統一為同一套海報系統：

- **背景**：5 張「真實演唱會」風格底圖（不同燈光底色；單一藝人／樂團／整個場館等視角，參考使用者提供的範例圖）× 不同尺寸 + 主題色純色底（四預設＋自訂，同 C-1 色盤）+ 自訂上傳底圖。
- **排版**：一套固定網格（靠左原則）× 五種文字構圖樣式，見下方「海報版面系統設計」。
- **內容**：總覽／藝人／常去／歌曲四大類，一次最多選兩類，依選取數量自動改排版。
- **配色**：全部走 C-1 的主題色邏輯。

### 海報版面系統設計（決策 ② 定案，依使用者提供之三組參考）

**參考來源歸納**：
- 參考一（TECH/FOLK 活動海報）：**同一套網格**支撐不同視覺——左欄標題、右欄超大日期數字、右下時間表、左下 WHERE 色塊、底部場館/LOGO 橫條。「版定好之後，構圖視覺就一個簡單的姿勢。」
- 參考二（龐門正道）：**字即圖像**——超大漢字出血裁切、中央直排小字、大量留白、紙感單色底。
- 參考三（10 種排版方式）：**同一份內容、多種構圖**——中文大字 × 英文小字的量體對比，直排/橫排/角落錨定/上下夾擊等變化。

**設計原則**（全樣式共通）：
1. **單一網格**：所有樣式都放在同一套 12 欄網格上（安全邊距 = 短邊 6%）；資訊塊永遠靠左（永不置中），呼應現有票根的「靠左」原則。
2. **固定錨點**：頂部 `BANDARY` 品牌行、底部資訊條（場地 · 城市 · 日期 小字）每個樣式都在同位置——這就是參考一「同網格不同視覺」的做法。
3. **樣式只改「文字構圖」**：背景、配色、內容區塊與樣式正交，任意組合。

**五種文字構圖樣式（`TypeStyle`）**：

| 樣式 | 構圖 | 取文字規則 | 參考 |
|------|------|-----------|------|
| `GRID 格線誌` | 左欄標題（2–3 行）＋右欄超大日期數字（月小字 + DD 特大）＋右下資訊列表＋左下色塊 WHERE 區 | 標題=演唱會名（無則藝人名）；日期取 `DD`；WHERE=場地+城市 | 參考一 |
| `BLEED 大字出血` | 標題取 2–4 個關鍵字放大到出血（允許裁切），其餘資訊小字塊縮左下 | 取演唱會名前 2–4 個中文字元（或藝人名取較短者）；逐字定位 | 參考二 |
| `VERTICAL 直排` | 標題直排、右側頂天立地；小字資訊橫排靠左下；大留白 | 直排每行 ≤6 字、超過折行；日期用直排數字 | 參考二中央直排 |
| `BILINGUAL 中英對照` | 中文大標與英文小字塊上下夾疊、量體對比 | 英文來源：藝人英文名（H-2 Spotify 可補）→ 無則 `BANDARY LIVE ARCHIVE` 固定字 | 參考三 |
| `MINIMAL 簡潔` | 大量留白、單行大標、資訊三行 | 現有 BREATH（寬鬆）樣式的延續，直接沿用 | 現況 |

**與內容區塊（總覽/藝人/常去/歌曲，選 1–2）的關係**：TypeStyle 決定「標題區」長相；內容區塊填入網格的資訊欄——1 塊＝整欄大版、2 塊＝上下分欄。`GRID` 資訊容量最大（適合歌單/多藝人），`BLEED`/`MINIMAL` 只放摘要數字。

**實作備註**：
- 直排與逐字出血在 Canvas 端 = `drawText` 逐字定位（現有管線已是 Canvas 手繪，可行）；這正是管線統一（PosterRenderer）後只需寫一次的理由。
- **字體是前置**：`BLEED`/`VERTICAL` 的效果吃字重與襯線——建議把 G-1（Typography：Fraunces/Space Grotesk + 中文襯線）的**字型資源引入提前到 D 之前或 D 期間先行**，樣式才立得起來。

架構上必須先做「**渲染管線抽象化**」（見下方架構節），否則 3 入口 × (5+6+1) 底圖 × 5 樣式 × 4 內容類的組合會讓現有的重複程式碼爆炸。

### ⚠️ 背景圖硬阻塞（開工前需你決定）
「5 張現實版演唱會照片」**無法由我在此環境用程式生成**——我沒有照片級圖像生成工具（只能做 SVG / 向量 / 程式繪製）。三選一：
- (a) 你在外部（AI 繪圖 / 授權圖庫）備妥 5 張 9:16 演唱會照片交我打包（建議，最貼近你要的「現實版」）。
- (b) 我做**向量 / 程式繪製**的演唱會場景底圖（燈光、燈海、紙花——非照片級，但可控、免版權、免 APK 圖檔）。
- (c) 沿用現有 `generatePresetBackground` 的漸層底，先不做寫實圖。

子工作拆解與進度：
1. ✅ **完成（2026-07-09）** `share/PosterRenderer`：spec 驅動（背景 × 配色 × TypeStyle × 內容區塊）→ bitmap。`PosterSpec`/`Block`/`Background`（Preset/Solid/Photo/None）+ 12 欄網格（安全邊距=短邊6%）+ 固定錨點（頂部 BANDARY 品牌行、底部資訊條）+ 5 種 TypeStyle 全部實作（GRID/BLEED/VERTICAL/BILINGUAL/MINIMAL）。字體：**使用者決定先用系統 Serif/SansSerif 加粗體近似**（不用 Google Fonts 下載式 API，避免未驗證裝置上的網路/憑證風險），日後要真的换 Fraunces/Space Grotesk 再說。**已接上統計分享頁**（`StatsScreen.statsToPosterSpec` + ShareSheet 新增「排版樣式（新）」選項列，選「現有」沿用原本 renderStatsBitmap，選 5 種新樣式則走 PosterRenderer——**使用者決定先加成額外選項、不動現有票根/統計 renderer**，降低沒實機驗證就切換的風險）。統計海報把 GRID 的「日期大數字」重新詮釋成「總場次大數字」（沒有單一日期可用時的合理轉換）。
2. **✅ 背景圖管線就緒（2026-07-08，compile 綠燈）+ ⛔ 待實際圖檔**：使用者選 (a) 外部備 5 張照片交我打包。已建 `share/PosterBackgrounds`（讀 `assets/posters/bg_1..5.webp`，偵測不到就沿用漸層、不出錯）。接上三處：
   - 票根分享 + 統計分享：底圖清單顯示這 5 張、**預設帶入第一張演唱會背景**、預覽與匯出都能用。
   - **新增 / 編輯演唱會**：封面區多一列「內建演唱會背景」縮圖，點一下即設為這場封面（存 `file:///android_asset/posters/bg_N.webp`，Coil 直接載入，卡片 / 月曆 / 詳情頁照常顯示，見 `PosterBackgrounds.coverUri`）。
   - **✅ 圖檔已到位（2026-07-08）**：使用者自行存進 `app/src/main/assets/posters/`（原本檔名多了一次副檔名 bg_N.jpg.jpg + 單張達 5–7MB / 原圖 4000–6000px）。已用 PowerShell + System.Drawing 修正檔名並在原地縮圖 + 壓縮（最長邊 ≤2000、JPEG q80、以 MemoryStream 載入避開 Remove-Item 封鎖）：5 張共 ~1.5MB（185–445KB/張），皆有效 JPEG。`PosterBackgrounds` 已支援 webp/jpg/jpeg/png 自動偵測。**注意**：其中 3 張是橫式（2000×1333），套進 9:16 分享圖 / 直式封面時會置中裁切（只顯示中間直條）——功能正常，若要完整場景可換直式圖。
3. ⏳ TypeStyle × 5：先做 GRID 與 MINIMAL 驗證網格，再補 BLEED/VERTICAL/BILINGUAL。**未做**（依賴 1）。
4. **✅ Phase 1（2026-07-08，已 compile 綠燈）**：內容模型改為使用者定案版——統計分享的四大類（總覽/藝人/常去/歌曲）**改為最多選兩項**（預設總覽+藝人、至少留 1）；移除 >2 項的舊 SHARE_PRESETS；**配色改跟隨 App 主題四色**（THEME_SHARE_COLORS，預設帶入目前主題主色）。「依數量改排版」目前沿用既有堆疊（1 塊 / 2 塊），細緻版式待 PosterRenderer。
5. 🔄 三入口頁改接新管線：**票根已於 2026-07-13（續13）整檔改用 PosterRenderer**（刪掉自繪 Canvas / 私有 renderer）；**統計分享 + 里程碑徽章 + 年度 Wrapped** 也都走 PosterRenderer。唯一未切＝**演唱會詳情頁 hero**，那是照片輪播（AsyncImage）不是海報渲染，本就不該套海報樣式。剩實機看圖微調 GRID/BLEED/VERTICAL 座標。

## Batch E — 成長與留存

**Bandary 的一句話價值主張**（onboarding 定位思考的產出）：「不用再翻聊天紀錄找日期、自己算場次和花費——每一場演唱會、每天選的歌，記一次就自動變成你的追星檔案與統計，還能跟朋友互相看到誰在搶同一張票。」已寫進 E-1 的引導首頁。

**Onboarding 四原則對應**：① 了解價值＝上面這句話（ValueStep）；② 個人化＝選最常用頁面直接寫入起始畫面設定（PersonalizeStep，重用 M-2 做的 `startTab`）；③ 權限請求＝說明通知用途＋拒絕後果全透明、不擋任何功能（PermissionStep）；④ 付費轉換＝**刻意不做**——Bandary 核心記錄功能全部免費、Pro 只是錦上添花的加值解鎖（折扣碼），沒有需要「說清楚免費/付費差異」的硬付費牆，強塞一個付費頁面會是干擾而非幫助。

| # | 項目 | 備註 |
|---|------|------|
| E-1 | 首次啟動 Aha 路徑：全螢幕四步驟引導（了解價值→個人化→權限說明→新增第一場） | ✅ 完成（2026-07-09）：`presentation/ui/onboarding/OnboardingScreen.kt`；`SettingsDataStore.hasOnboarded` + `MainActivity` 的 `OnboardingViewModel.shouldShowOnboarding` 判斷（已有演唱會資料的既有使用者自動跳過，不受影響）；「新增第一場」沿用既有的 `deepLinkRoute` 機制導去 AddShow。未做熱門演出預填清單（沒有可靠的即時演出資料源，先讓使用者自己填） |
| E-2 | 空狀態改可點擊示範卡片（ghost card） | ✅ 完成（2026-07-09，`EmptyState` 加 `onClick`/`actionLabel`，虛線框樣式；先接上「我的演唱會」空狀態，其餘畫面沿用原本純資訊性版本、未逐一升級） |
| E-3 | 打卡成功 streak 火焰 +1 彈跳動畫（scale + haptic） | ✅ 完成（2026-07-09）：`HomeViewModel.streakBump` 只在「今天首次記錄且連續天數真的往上跳」時 +1；`StreakCard` 收到訊號時火焰 icon spring 彈跳（1→1.4→1）+ 長按型 haptic。編輯過去日子不觸發 |
| E-4 | 連續 7/14/30/100 天徽章卡 + 一鍵生成分享圖 | ✅ 完成（2026-07-09）：`HomeViewModel.MILESTONES = {7,14,30,100}`，`badgeMilestone` 只在連續天數第一次跨過里程碑時觸發；`StreakBadgeDialog` 用 PosterRenderer 的 BLEED 樣式把天數放大成主體，可一鍵分享（shareOnly）/下載（downloadToGallery） |
| E-5 | 20:00 未打卡發挽救推播「別讓 N 天斷在今天」 | ✅ 完成（2026-07-09）：`DailyCheckInReceiver` 響鈴時先查 `getTodaySong()`——今天已記錄就不打擾；沒記錄則用 `streakEndingYesterday()`（新，從昨天往回算，避免今天算成 0）決定語氣，**streak ≥ 3 才用「別讓 N 天斷在今天」挽救框架**，否則一般提醒。時間沿用使用者設定的每日提醒時間（預設 21:00，可自訂成 20:00） |

## Batch E2 — 留存機制二批（2026-07-16 新增）

> 來源：2026-07-16 與 AI 的留存策略討論（全球高留存 App 機制拆解：觸發 / 資產累積 / 儀式化 / 期待感 / 社交）。
> 設計前提：Bandary 是**低頻 App**（演唱會不是天天有），因此不走 TikTok 式時長路線，走 Pinterest / Spotify Wrapped 式「資產累積 + 儀式化 + 正當觸發」路線。E 批已完成習慣迴路基本盤（onboarding / streak / 徽章 / 挽救推播），本批是下一層。
> 工程量標記：S（小，複用現有管線）/ M（中，需新元件或資料模型）。

**建議執行順序**：E2-1 → E2-2 → E2-3 → E2-4 → E2-5（全為 S/M、複用現有 Worker / 通知深連結 / 分享管線，分別覆蓋觸發、資產累積、儀式化三種機制）。社交組（E2-11 / E2-12）槓桿最大但依賴好友 v3 rules 部署，排在真機驗證批之後。

### A 組 — 通知觸發（最划算，Worker 基礎設施都在）

| # | 項目 | 量 | 備註 |
|---|------|----|------|
| E2-1 | 演出當天「實用推播」：早上推「今晚 XX @ 場館——點開看取票序號與入場時間」 | S | ✅ 完成（2026-07-16，`DailyDigestReceiver`：今天有演出→推時間/場館/取票序號，深連結詳情頁） |
| E2-2 | 演出後 24h「補紀錄」提醒：「昨晚的演出如何？補上評分和照片」→ 深連結編輯頁 | S | ✅ 完成（昨天有演出且缺評分/照片→推補紀錄，深連結 AddShow 編輯，`editConcertId` extra） |
| E2-3 | 「N 年前的今天」回憶推播（仿 Google Photos） | S | ✅ 完成（同月同日更早年份→推回憶，深連結詳情頁） |
| E2-6 | 週末免費活動推播：每週四/五推「這週末有 N 場免費活動」 | S | ✅ 完成（週四/五→算本週末免費活動數→推，深連結台灣活動；`navTarget=events`） |

### B 組 — 儀式化 / Wrapped（統計資料都現成）

| # | 項目 | 量 | 備註 |
|---|------|----|------|
| E2-5 | 月結小回顧：每月 1 號推播 + Home 插卡「上月看了 N 場、選了 N 首歌、最常聽 XX」 | M | ✅ 完成（2026-07-16，續35）：`DailyDigestReceiver` 每月 1 號推月結回顧（深連結 stats）；選歌頁每月前 7 天插 `MonthRecapCard` |
| E2-7 | 年度 Wrapped「事件化」：12 月初推播「你的年度聽團回顧出爐了」→ 逐頁 story 式回顧（總場次→最遠場館→最常見藝人→總花費→年度選歌），最後一頁才是分享 | M | ✅ 完成（2026-07-16，續36）：`WrappedScreen`（逐頁 story + 頂部進度條 + 點左右翻頁 + 9:16 分享圖）、`WrappedViewModel`、Nav `wrapped/{year}`、統計頁入口卡、12 月初推播 navTarget=wrapped |
| E2-8 | 場次里程碑徽章：第 10/50/100 場、第一次去新場館、看過 10 組不同藝人 | S | ✅ 完成（2026-07-16，續35）：統計頁 `AchievementsCard`（場次/藝人/場館/縣市里程碑）+ `MilestoneBadgeDialog` 複用 PosterRenderer BLEED 分享 |

### C 組 — 期待感 / 未來導向

| # | 項目 | 量 | 備註 |
|---|------|----|------|
| E2-9 | 「想看 / 關注中」願望清單：演唱會加一種狀態，配開賣時間追蹤 | M | ✅ 完成（2026-07-16，續37）：`WishlistScreen` + `WishlistViewModel` 收集即將到來且 ticketStatus∈{WISHLIST,LOTTERY} 的場次 + 開賣倒數，依開賣時間排序；入口＝ShowsScreen 書籤鈕 + TicketSaleWidget 點擊 navTarget=wishlist。**決策：沿用既有 WISHLIST/LOTTERY，零 Room migration**（決策⑩ 的可逆最小解；更前期的「關注中」狀態日後再議） |
| E2-10 | 「今日選歌」桌面 Widget：一鍵直達選歌 | S | ✅ 完成（2026-07-16，續35）：`TodaySongWidget`（顯示今天是否已記錄 + 連續天數，點擊 navTarget=songs 直達選歌行事曆） |
| E2-4 | Streak 補救券（仿 Duolingo Streak Freeze）：每月一張「補打卡券」，斷簽隔天可回補 | S | ✅ 完成（2026-07-16，續35）：每月自動發券（存 3 張上限），選歌頁 `StreakRescueCard` 一鍵補回昨天；`calculateStreak(freezeDates)` 接回連續紀錄 |

### D 組 — 社交觸發（好友模組已有；⚠️ 依賴好友 v3 rules 部署）

| # | 項目 | 量 | 備註 |
|---|------|----|------|
| E2-11 | 「好友也要去」同場通知：好友共享日曆做同日期+場地比對，命中推「你的好友 X 也要去 7/20 的 XX」 | M | ✅ 程式完成（2026-07-16，續36）：`DailyDigestReceiver` 比對我的即將到來 vs 好友共享日曆（同日+同場地）→ notifId 5007 深連結詳情。**需好友 rules 部署後實機驗證** |
| E2-12 | 好友新增演出通知：「好友 X 剛新增了一場 YY 的演出」 | S | ✅ 程式完成（2026-07-16，續36）：好友共享日曆出現沒看過的演出→ notifId 5008 深連結好友頁（`seenFriendShows` + 首次只記錄不通知）。**需好友 rules 部署後實機驗證** |

## Batch F — 後台與營運

| # | 項目 | 備註 |
|---|------|------|
| F-1 | 後台數據補齊：所有使用者選歌藝人排行、歌曲排行、演出藝人排行、縣市排行、總註冊數、每日上線 | 已過決策 ④：**急修 4 大多已做**（globalStats）→ 先實機驗證缺什麼、哪些壞再補，不重寫 |
| F-2 | 折扣碼管理：內建折扣碼也能編輯與改權限 | 把 App 內建碼 seed 進 Firestore `discount_codes`，全部走雲端；內建碼僅作離線 fallback 或全移除 |

## Batch G — 規格書殘留項收尾

| # | 項目 | 備註 |
|---|------|------|
| G-1 | Part 2-2 Typography：Fraunces / Space Grotesk subset + Noto provider | 動 res/font + gradle |
| G-2 | Part 2-1 間距 8pt：篩選器壓一排 + bottom sheet、免費活動 header 減 icon | |
| G-3 | Part 2-4/2-5 動效：翻牌倒數、票根噪點撕邊、溢出數字、SharedTransitionLayout | 翻牌+haptic 與 E-3 同批做 |
| G-4 | 1-4 二階段：同場推薦 | 前置：先定義「同場」判定（用好友共享日曆同日期+場地比對？或新增 attendance 集合？）；好友 v3 rules diff 也要先部署 |
| G-5 | Part 3 ASO（素材/營運，不動程式碼） | 規格書 3-1~3-5 |

## Batch H — 智慧新增與外部整合

三項都在強化「新增演唱會」的輸入體驗，共用同一批架構工作（外部服務層 + API 金鑰管理），一批做。

| # | 項目 | 現況基礎 | 作法方向 |
|---|------|---------|---------|
| H-1 | **海報自動判讀**：丟一張演唱會海報 → 自動判斷地點 / 時間 / 藝人 / 演唱會名稱等基本資訊 | **已有** `TimetableOcrService`（ML Kit 中文 OCR，on-device），目前是「逐行辨識 → 使用者點選填入」 | 升級為自動欄位分類：OCR 行 → 規則引擎判讀（日期時間 regex、場地關鍵字庫「Legacy/Zepp/小巨蛋/流行音樂中心…」、剩餘大字級行 = 標題/藝人）→ 預填表單、使用者確認修正。可選進階：接 LLM API 做判讀（成本與金鑰問題同決策 ⑥） |
| H-2 | **Spotify 藝人搜尋**：搜尋並獲取藝人資料（頭像、正式名稱、類型） | 無 | Spotify Web API `/search?type=artist`；金鑰策略見決策 ⑥。用途：新增演唱會時藝人自動補全 + 頭像；藝人詳情頁顯示官方資料。快取進 Room 避免重複請求 |
| H-3 | **Google 圖片搜尋找海報 + 自訂裁切**：找到最美的演出海報，自訂裁切完美展示 | 裁切器已於 2026-07-01 因閃退**整組移除**（決策 ⑤）；圖搜無 | 圖搜選型見決策 ⑦；裁切重新引入需換實作（uCrop / Compose 自製）。裁切功能做成通用元件：封面、票根底圖、頭貼都能用 |

**H 的共用架構工作**：
- `data/remote/` 新增外部服務層：`SpotifyService` / `ImageSearchService`，與 `FirestoreService` 平行；全部走 Repository 進 UI。
- **API 金鑰管理**：金鑰放 `local.properties` → `BuildConfig`（不進版控），README 補設定說明；有代理需求時再起最小 Cloud Function。
- 外部請求全部要有：快取（Room / 記憶體）、超時、失敗不擋手動輸入（外部整合永遠是「加速器」不是「必經路」）。

## Batch I — 活動類型擴展（藝文活動 / 表演）

| # | 項目 | 備註 |
|---|------|------|
| I-1 | 除了演唱會，也能記錄其他藝文活動 / 表演（展覽、舞台劇、音樂劇、脫口秀…） | ✅ 第一階段完成（2026-07-16，續35）：`ConcertType` 擴充**展覽 / 舞台劇 / 音樂劇 / 脫口秀**（新增演唱會的類型選單自動出現，enum 以名稱儲存零遷移），統計觀看時長給預設。**尚未做**：全站「演唱會」字樣 audit、是否分頁籤混計（隨決策 ⑧ 續議） |

排在 PWA 前的理由：這是資料模型層的變動，PWA 需要穩定 schema。

## Batch J — PWA（最後）

- 使用者指示：全部做好後才寫，先留著待辦。
- 架構前提見下節：Compose 無法直接轉 web，PWA = 獨立輕量 web app（Firebase JS SDK）讀寫**同一套 Firestore**。
- 前置條件：(1) 好友 v3 rules 已部署且涵蓋 web 用例；(2) Firestore schema 穩定（分享管線、attendance、活動類型擴展等資料模型都定案）；(3) 決定 PWA 範圍（唯讀日曆檢視？還是可記錄？）。

---

## 專案架構現況與影響分析

### 現況（單 module Android app）

```
app/
 ├─ presentation/ui/<feature>/   Compose + Material3（Screen 檔普遍 500–1900 行）
 ├─ presentation/widget/         Glance 四款桌面小工具
 ├─ data/local/                  Room（演唱會/選歌）+ DataStore（設定）+ entity mapping
 ├─ data/remote/FirestoreService Firebase Auth(Google) + Firestore 全部雲端功能（單檔 ~900 行）
 ├─ data/repository/             Repository 層（Hilt singleton）
 └─ domain/model/Models.kt       純 data class
背景工作：WorkManager（自動備份）、AlarmManager（開賣/入場/打卡提醒）
雲端集合：users/{uid}/{concerts,daily_songs,images,friends,friend_requests}、
          shared_calendars、handles、free_events、feedback、discount_codes
```

### 七個結構性議題（下階段的架構主軸）

1. **分享渲染管線抽象化（做 D 前必做）**
   現況：`TicketShareScreen.kt`（~1100 行）與 `StatsScreen.kt`（~1200 行）各養一套私有 Canvas renderer，邏輯高度雷同。D-1 的組合空間（3 入口 × 5+4+1 底圖 × 4 內容類 × 排版變化）+ E-4 徽章卡都吃這條管線。
   → 抽出 `presentation/share/PosterRenderer`：以 `PosterSpec(background, palette, layout, blocks)` 為輸入、輸出 bitmap；Compose 預覽與匯出走同一份。三個入口頁只組 spec。

2. **色彩 token 化（C-1 的本體）**
   現況：`Theme.kt` 的 `brandFromHex` 推導是對的方向，但全站仍散落硬編碼色（`BandaryColors` legacy 物件、各頁 `Color(0x…)`、分享 renderer 內寫死的色票）。「整個 app 有顏色的地方都依主題色邏輯」＝一次全站 audit：語意化（primary/surface/accent/…）→ 全部由選定主題色推導。主題色歷史已反覆三次（6 色→4 色灰→回歸綠），**把色彩定義收斂到單一檔案**是止血的辦法。

3. **背景圖資產策略（D-2，控制 APK）**
   1-3 才確認過「全程式繪製、APK 幾乎不變」。D 要引入 5 張真實感演唱會照片：
   - 格式：WebP（品質 ~80），主檔 9:16 1080×1920，每張約 200–400KB → 5 張約 1–2MB APK 增量。
   - **不要每尺寸各存一套**：其他尺寸用程式縮放/裁切（中央構圖裁切安全區）。
   - 生成：AI 產圖（不同燈光底色：暖橘/紫藍/粉紅/青綠/金黃；視角：單一藝人剪影/樂團全景/場館海洋/手機燈海/紙花瞬間——參照使用者範例）。

4. **巨檔拆分（順手做，不專案化）**
   `AddShowScreen`（~1900 行）、`StatsScreen`、`TicketShareScreen`、`ProfileScreen` 都破千行。規則：**下階段每碰一個巨檔，就把該次觸及的區塊抽成獨立 composable 檔**，不做一次性大重構（風險高、無使用者價值）。

5. **備份效能（A-4）**
   疑點：急修 1 的圖片備份逐張 `Tasks.await` 序列上傳 + 每次全量。方向：(a) 用現成的 sha1 doc id 先查存在 → 只傳新圖（增量）；(b) 批次並行（協程 async + awaitAll，限流 4–6 併發）；(c) 進度回報到 UI。

6. **外部整合層與金鑰管理（H 的地基）**
   目前唯一外部依賴是 Firebase（google-services.json）與 on-device ML Kit，**還沒有任何需要自管金鑰的 HTTP API**。H 一次引入兩個（Spotify、圖搜）：
   - `data/remote/` 建平行的 `SpotifyService` / `ImageSearchService`（Retrofit 或 Ktor client，走 Repository）。
   - 金鑰：`local.properties` → `BuildConfig` 注入，不進版控；Spotify secret 若走代理，起最小 Cloud Function（也是未來 PWA 可共用的後端起點）。
   - 原則：外部整合是加速器不是必經路——斷網 / 額度用盡 / 無金鑰時，手動輸入流程完全不受影響。
   - OCR 判讀升級（H-1）維持 on-device 優先（免費、離線、隱私），LLM 判讀只作為可選增強。

7. **活動類型的資料模型（I 的地基）**
   `Concert.type: ConcertType` 已存在，擴展藝文活動的最小路徑是加 enum 值——但要同時盤點：全站「演唱會」文案、統計是否混計、篩選/月曆/Widget 的顯示、Firestore 備份與共享日曆的 type 欄位相容（舊版 App 讀到新 type 的 fallback）。PWA（J）之前必須定案。

### PWA 的架構定位（H）

Compose Android 無法直接輸出 web。務實路線：獨立輕量 web app（任選 React/Svelte/vanilla + Firebase JS SDK），與 Android 共用 **Firestore schema + security rules 作為唯一契約**。因此下階段所有新資料模型（分享不涉及、attendance 涉及、好友 v3 涉及）定案時都要想「web 端讀寫這個 schema 是否可行」。rules 部署（目前好友 v3 diff 尚未部署）是 PWA 的硬前置。

---

## 與現有進度的銜接

- 改版總規格書已完成：急修批次、1-1 文案、1-2 主題色四款收斂、1-3 海報兩款收斂、1-4 好友 v3 第一階段、2-0 視覺一致性（全部 compile 綠燈）。
- 好友 v3 的 **Firestore rules diff 尚未部署**（CONSOLE_SETUP.md），部署前好友新功能在真機不可用。
- C-1 會部分回退 1-2 的收斂——這是使用者方向調整，不是缺陷；開工時以決策 ① 定案為準。
