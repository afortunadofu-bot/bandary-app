import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    // Firebase: google-services plugin (classpath declared in root build.gradle.kts).
    id("com.google.gms.google-services")
}

// 正式簽章：從專案根目錄的 keystore.properties 讀取金鑰資訊（此檔已列入 .gitignore，不進版控）。
// 沒有這個檔時，release 會維持未簽章（供本機 compile 檢查用），實際上架前請依 docs/PLAY_RELEASE.md 設定。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

// USB 測試版（debug）與 Play 版分開安裝：debug 用 com.bandary.app.debug。
// 兩版同一個套件名時，手機上只能裝其中一個，換版本必須先解除安裝，本機資料就跟著被清掉。
// 前提是 Firebase 已登記 com.bandary.app.debug 並重新下載 google-services.json；
// 還沒登記時 google-services 外掛找不到對應設定會讓建置失敗，所以偵測到才啟用。
val debugFirebaseClientRegistered = file("google-services.json").let { f ->
    f.exists() && f.readText().contains("\"com.bandary.app.debug\"")
}

// ── 版號：全專案唯一真相來源 ────────────────────────────────────────────────
// 規則 MAJOR.MINOR.PATCH，versionCode 由 versionName 推導（不手寫，避免兩者漂移）：
//   MAJOR * 1000000 + MINOR * 1000 + PATCH   例：1.100.0 → 1100000
//
// 為什麼從 `M*10000 + m*100 + p` 換掉：舊公式的 MINOR 只有兩位數可用，
// **1.100.0 會算出 20000，跟 2.0.0 撞號**。Play 要求 versionCode 嚴格遞增，
// 一旦撞號就再也上不了 2.0.0。我們走到 1.99.x 才發現離那道牆只剩一版。
//
// 新公式給 MINOR 與 PATCH 各三位數（0–999），上限是 Play 的 2,100,000,000，
// MAJOR 可以到 2100，夠用到不用再想這件事。
//
// **換公式本身是安全的**：已上傳過的最大 versionCode 是 19901（v1.99.1），
// 而同一個 1.99.1 用新公式算出來是 1099001，遠大於它，仍然遞增。
//
// README 更新記錄與 git commit 訊息都用同一個 vMAJOR.MINOR.PATCH。
val appVersionName = "1.117.2"
val appVersionCode = appVersionName.split(".").let { (major, minor, patch) ->
    val mj = major.toInt()
    val mn = minor.toInt()
    val pt = patch.toInt()
    // 寧可讓建置當場失敗，也不要默默算出一個會跟別版撞號的 versionCode——
    // 撞號要等到上傳 Play 被退才會發現，那時候 AAB 都打完了。
    require(mn in 0..999 && pt in 0..999) {
        "版號 $appVersionName 超出編碼範圍：MINOR 與 PATCH 各限 0–999（見 README 版號規則）"
    }
    mj * 1000000 + mn * 1000 + pt
}

android {
    namespace = "com.bandary"
    // 36 = Android 16。Google Play 自 2026-08-31 起要求新 App 與更新都要 targetSdk 36 才能上傳。
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bandary.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                // rootProject.file：相對路徑以專案根目錄為準（跟 keystore.properties 放同一層）。
                // 用 file() 會變成相對 app/ 資料夾，金鑰放根目錄時打包會找不到檔案。
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 有設定 keystore.properties 才套用正式簽章（否則維持未簽章，僅供本機驗證）。
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
            // R8：程式碼縮減 + 混淆 + 移除未用資源，讓正式 APK 更難被反編譯 / 竄改。
            // keep 規則見 proguard-rules.pro（Room / Hilt / Firebase / kotlinx.datetime / 資料模型 / Glance）。
            isMinifyEnabled = true
            isShrinkResources = true
            // 把第三方原生程式庫（ML Kit 等）的符號表一起打進 AAB，Play Console 才能解析原生當機紀錄；
            // 沒有這行上傳時會出現「尚未上傳偵錯符號檔」警告。
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            if (debugFirebaseClientRegistered) applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.datetime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.core.splashscreen)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation(libs.coil.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.play.services.auth)
    implementation(libs.mlkit.text.chinese)
    implementation(libs.mlkit.text.latin)
    // 上傳前的裁切。相依本來就宣告在 version catalog 裡，但一直沒被 include 進來。
    // 裁切是 OCR 辨識率最大的單一槓桿：把畫面裁到只剩歌單／票根，雜訊直接歸零。
    implementation(libs.android.image.cropper)
    // Google Play 帳單：Pro 解鎖（一次買斷 + 年訂閱）。商品 ID 見 BillingManager.ProPlan。
    implementation(libs.play.billing)

    // QR code 產生（邀請碼、票根上的分享碼）。純 Java、沒有 Android 相依、離線可用；
    // 只用到 QRCodeWriter，不引入 zxing 的 android-embedded（那包會帶相機 Activity）。
    implementation("com.google.zxing:core:3.5.3")

    // 單元測試：只測不碰 Android framework 的純計算（統計、連續天數、撞場判定、名稱正規化…）。
    // 這些是最容易寫錯又最不容易被人工測出來的部分，也是唯一不需要模擬器就跑得動的一層。
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.datetime)

    // Firebase (google-services.json is in app/). BoM keeps versions aligned.
    implementation(platform("com.google.firebase:firebase-bom:34.15.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    // 呼叫 Cloud Functions（Spotify 搜尋代理、刪除帳號）——金鑰與高權限操作都留在伺服器端。
    implementation("com.google.firebase:firebase-functions")
}
