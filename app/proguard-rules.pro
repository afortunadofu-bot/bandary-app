# App 自己的 ProGuard / R8 規則。
#
# 原則：第三方程式庫（Firebase、Play 服務、Hilt、Room、WorkManager、Glance、ML Kit…）都附帶
# 自己的 consumer rules，打包時會自動併入（已在 mapping/release/configuration.txt 確認）。
# 不要在這裡對它們整包 -keep：整包保留會讓 R8 無法縮減與混淆那些程式庫，
# 這是 Play Console「應用程式最佳化」只有 42% 的主因。
# 這裡只保留 R8 看不出來、用「字串或反射」參照的東西。
#
# domain/model 不整包保留：Firestore 讀寫是手動轉 Map（toCloudMap / fromMap），
# 全專案沒有 toObject、Gson、kotlinx.serialization 或反射（2026-09-15 查證）。

# kotlinx-datetime 沒有附 consumer rules，保守保留。
-keep class kotlinx.datetime.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class com.bandary.data.local.entity.** { *; }
-dontwarn androidx.room.paging.**

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Enum：以 .name 存進 Firestore / DataStore，還原時用 valueOf，需保留常數名稱
-keepclassmembers enum com.bandary.** { *; }

# Firebase / Play 服務：規則由程式庫自帶，這裡只關掉它們可選依賴的警告
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Manifest 以類名字串參照、或 WorkManager 以類名字串反射建立的類別，名稱不能被改
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends android.appwidget.AppWidgetProvider { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
