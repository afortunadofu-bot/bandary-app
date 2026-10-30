package com.bandary.data.local

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * App 內語言切換（多語言）。用輕量 SharedPreferences 記錄選擇，並在 Activity.attachBaseContext
 * 用選定語系包一層 Context，讓 strings.xml 解析到對應 values-xx。空字串 = 跟隨系統。
 */
object LocaleManager {
    private const val PREFS = "bandary_locale"
    private const val KEY = "app_lang"   // "" 跟隨系統 / "zh" 中文 / "en" English

    fun getLang(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun setLang(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
    }

    /** 依使用者選擇把 Context 包上對應語系；跟隨系統時原樣回傳。全程防呆——
     *  在 attachBaseContext 這種很早期的階段若讀取失敗，一律退回原始 context，不讓 App 崩潰。 */
    fun wrap(context: Context): Context {
        val lang = runCatching { getLang(context) }.getOrDefault("")
        val locale = when (lang) {
            "zh" -> Locale.TRADITIONAL_CHINESE
            "en" -> Locale.ENGLISH
            else -> return context
        }
        return runCatching {
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.createConfigurationContext(config)
        }.getOrDefault(context)
    }
}
