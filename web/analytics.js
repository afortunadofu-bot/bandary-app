// Google Analytics 4（網頁版）。
//
// 為什麼抽成一支檔案而不是把官方那段 inline 貼進五個頁面：
// 這裡有 index / about / help / privacy / delete-account 五頁，
// inline 的話評估編號 G-T3FZL4XQDX 會在五個地方各寫一次，
// 換帳號或停用時只要漏掉一頁就會一直送資料到舊的地方，而且不會有任何錯誤提示。
// 現在每頁只要一行 <script src="analytics.js">，編號只存在這一行。
//
// Service Worker 不會攔到 gtag：sw.js 的 fetch handler 只處理同源 GET，
// googletagmanager.com 直接放行（見 sw.js 的註解）。
(function () {
  var ID = 'G-T3FZL4XQDX';

  // 尊重瀏覽器的「不要追蹤」。這不是法規硬性要求，但使用者既然表達了，
  // 照做的成本只有這三行。
  if (navigator.doNotTrack === '1' || window.doNotTrack === '1') return;

  var s = document.createElement('script');
  s.async = true;
  s.src = 'https://www.googletagmanager.com/gtag/js?id=' + ID;
  document.head.appendChild(s);

  window.dataLayer = window.dataLayer || [];
  window.gtag = function () { window.dataLayer.push(arguments); };
  gtag('js', new Date());
  // anonymize_ip：GA4 本來就會做，寫出來是為了讓看這份程式碼的人
  // （包含之後要寫隱私權政策的自己）確定這件事有被處理。
  gtag('config', ID, { anonymize_ip: true });
})();
