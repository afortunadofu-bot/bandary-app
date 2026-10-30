package com.bandary.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 署名區。全 App 只有這一份，「我的」頁與設定頁都用它。
 *
 * 版面對齊壽星日的頁尾：點陣標誌 + 品牌名 + tagline → 一句話定位 → 分隔線 →
 * 三個產品互連 → 版權行。三個 App 的署名層長一樣，使用者才看得出是同一個人做的。
 *
 * 關於品牌色：BRAND.md §7 說「不要把品牌色套進 app 介面」，
 * 但 §3 同時指定**署名層就是品牌該出現的地方**。所以 amber 只用在這個區塊裡
 * （tagline 與點陣的重點色），區塊之外的 App 介面一律不碰。
 *
 * 版本號一定要從 packageManager 讀。設定頁以前寫死「1.0.0」，從第一版留到現在都沒改，
 * 使用者回報問題時報的版本全是錯的。
 */
@Composable
fun BrandFooter(
    modifier: Modifier = Modifier,
    contactEmail: String? = null,
    showIcon: Boolean = true
) {
    val ctx = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val pkg = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
    }
    val ver = pkg?.versionName.orEmpty()
    val code = remember(pkg) {
        pkg?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 產品自己的版本行（品牌化＝品牌名寫在版本編號附近）──────────
        Text(
            buildString {
                append(PRODUCT_NAME)
                if (ver.isNotBlank()) append(" v").append(ver)
                if (code > 0) append(" (").append(code).append(")")
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = muted
        )
        contactEmail?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = muted)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── 品牌區 ────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showIcon) {
                DotMatrixMark(ink = ink, modifier = Modifier.size(52.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
            }
            Column {
                Text(
                    BRAND_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    // 字距 0.2em（BRAND.md §2 的排版習慣）
                    letterSpacing = 0.2.em
                )
                Text(
                    BRAND_TAGLINE,
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandAmber,
                    // tagline 字距 0.34em
                    letterSpacing = 0.34.em
                )
            }
        }
        Text(
            BRAND_POSITIONING,
            style = MaterialTheme.typography.bodySmall,
            color = muted
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── 三個產品互連。目前這支標成重點色，其他兩個可以點。 ─────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIBLING_PRODUCTS.forEachIndexed { i, p ->
                if (i > 0) {
                    Text(
                        "  ·  ",
                        style = MaterialTheme.typography.labelMedium,
                        color = muted.copy(alpha = 0.6f)
                    )
                }
                val current = p.name == PRODUCT_SHORT_NAME
                Text(
                    p.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = if (current) ink else muted,
                    // 目前這支不給點——點了只會回到自己，是死連結。
                    // 網址取不到時（id 對不上）也不給點：點了什麼都不會發生，
                    // 那比看起來不能點更像壞掉。
                    modifier = if (current || p.url.isBlank()) Modifier
                    else Modifier.clickable { runCatching { uriHandler.openUri(p.url) } }
                )
            }
        }
        Text(
            BRAND_COPYRIGHT,
            style = MaterialTheme.typography.labelSmall,
            color = muted.copy(alpha = 0.75f),
            textAlign = TextAlign.Start
        )
    }
}

/**
 * 4×4 點陣標誌。**不要重畫、不要改點位**（BRAND.md §2）：
 * ```
 * . W . A
 * W A . .
 * . . A W
 * A . W .
 * ```
 * A = amber、W = ink、. = ink 22%。
 * 用 Canvas 畫而不是放圖：點的顏色要跟著淺色／深色主題走，寫死的圖會在其中一邊變不見。
 */
@Composable
private fun DotMatrixMark(ink: Color, modifier: Modifier = Modifier) {
    val dim = ink.copy(alpha = 0.22f)
    // 每一列四個點的狀態：0 = dim、1 = ink、2 = amber
    val grid = remember {
        listOf(
            intArrayOf(0, 1, 0, 2),
            intArrayOf(1, 2, 0, 0),
            intArrayOf(0, 0, 2, 1),
            intArrayOf(2, 0, 1, 0)
        )
    }
    Canvas(modifier = modifier) {
        val step = size.minDimension / 4f
        val r = step * 0.30f
        grid.forEachIndexed { row, cells ->
            cells.forEachIndexed { col, state ->
                drawCircle(
                    color = when (state) {
                        1 -> ink
                        2 -> BrandAmber
                        else -> dim
                    },
                    radius = r,
                    center = Offset(step * (col + 0.5f), step * (row + 0.5f))
                )
            }
        }
    }
}

/** 品牌重點色（BRAND.md §2 的 --amber）。只用在署名區。 */
private val BrandAmber = Color(0xFFF2A73B)

/** 產品名。子產品不加品牌前綴（BRAND.md §4）——「小數據聽團曆」會讓兩邊都變弱。 */
const val PRODUCT_NAME = "Bandary 聽團曆"
private const val PRODUCT_SHORT_NAME = "Bandary"

const val BRAND_NAME = "小數據所"
const val BRAND_TAGLINE = "日子有數"
const val BRAND_POSITIONING = "把沒人在記的生活，變成你看得懂的數據。"
const val BRAND_COPYRIGHT = "© 2026 小數據所 · Celine Fu"

private data class SiblingProduct(val name: String, val label: String, val url: String)

/**
 * 網址一律從 [com.bandary.domain.model.OutboundLinks] 取，**不在這裡再抄一份**。
 *
 * 這支就是「各寫各的」會變成什麼樣子的實例：OutboundLinks 早就換成
 * Play 商店網址了，這裡卻還停在封測的 Google 報名表單
 *（Bandary 與 RelateStats 兩條都是），而且**不會報錯**
 * ——兩份清單各自看起來都很合理，只有同時打開才看得出來對不上。
 * 現在網址只存在一個地方，想錯也錯不了。
 */
private fun siblingUrl(id: String): String =
    com.bandary.domain.model.OutboundLinks.byId(id)?.url.orEmpty()

/**
 * 三個產品的互連。順序固定（壽星日 → Bandary → RelateStats），跟品牌文件一致。
 *
 * 兩支 App 都已經上架，所以連的是 Play 商店頁。
 *（以前連報名表單是因為還在封測——商店頁對還不是測試者的人是 404，
 * 點進去只會覺得壞掉。那個理由已經不成立了。）
 */
private val SIBLING_PRODUCTS = listOf(
    SiblingProduct("壽星日", "壽星日", siblingUrl(com.bandary.domain.model.OutboundLinks.BIRTHDAY)),
    SiblingProduct("Bandary", "Bandary", siblingUrl(com.bandary.domain.model.OutboundLinks.BANDARY_PLAY)),
    SiblingProduct("RelateStats", "RelateStats", siblingUrl(com.bandary.domain.model.OutboundLinks.RELATESTATS))
)
