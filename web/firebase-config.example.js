// 複製這個檔案成 firebase-config.js 再填入你的設定值。
//
// 取得方式：Firebase Console → 專案設定 → 你的應用程式 → 新增「網頁應用程式」(</>)
// → 註冊後會給你一段 firebaseConfig，把值抄進來即可。
//
// firebase-config.js 已列入 .gitignore，與 google-services.json 同一個原則：
// 設定值不進版控。這些值本身不是機密（會出現在網頁原始碼裡），真正的保護來自
// Firestore 安全規則與 Firebase Console 的「已授權網域」白名單。
window.BANDARY_FIREBASE_CONFIG = {
  apiKey: "貼上你的 apiKey",
  authDomain: "bandary-e5547.firebaseapp.com",
  projectId: "bandary-e5547",
  storageBucket: "bandary-e5547.firebasestorage.app",
  messagingSenderId: "貼上你的 messagingSenderId",
  appId: "貼上網頁應用程式的 appId"
};
