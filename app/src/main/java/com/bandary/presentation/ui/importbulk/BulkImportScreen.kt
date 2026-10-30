package com.bandary.presentation.ui.importbulk

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bandary.data.util.ConcertImport
import com.bandary.domain.model.isMultiDay
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkImportScreen(
    onBack: () -> Unit,
    viewModel: BulkImportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val existingKeys by viewModel.existingKeys.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val message by viewModel.message.collectAsState()

    var text by remember { mutableStateOf("") }
    var showExample by remember { mutableStateOf(false) }
    // 「音樂節卡司」模式已移除（v1.82.0）：它只吃「7/18：A｜B｜C」一種格式，
    // 稍微不一樣就整批解析失敗，而且建出來的場次還要再逐一補場地。
    // 同樣的事交給下面的 AI 轉檔提示詞做，任何格式都能先轉成標準 CSV 再匯入。
    var showAiPrompt by remember { mutableStateOf(false) }
    val tz = kotlinx.datetime.TimeZone.of("Asia/Taipei")
    val today = remember { kotlinx.datetime.Clock.System.now().toLocalDateTime(tz).date }
    val rows = remember(text) { ConcertImport.parse(text) }
    // 選取狀態：以「來源第幾筆」為 key，預設勾選「可解析且非重複」的列。
    val selected = remember { mutableStateMapOf<Int, Boolean>() }

    fun isDup(c: com.bandary.domain.model.Concert) =
        existingKeys.contains(BulkImportViewModel.dedupeKey(c))

    LaunchedEffect(text, existingKeys) {
        selected.clear()
        rows.forEach { r ->
            r.concert?.let { c -> selected[r.lineNumber] = !isDup(c) }
        }
    }

    // 結果訊息以 Toast 呈現後返回。
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.readFile(it) { loaded -> text = loaded } }
    }

    val validCount = rows.count { it.concert != null }
    val errorCount = rows.count { it.concert == null }
    val dupCount = rows.count { it.concert != null && isDup(it.concert) }
    val selectedConcerts = rows.mapNotNull { r -> r.concert?.takeIf { selected[r.lineNumber] == true } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("批量匯入", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            viewModel.importConcerts(selectedConcerts) { n ->
                                Toast.makeText(context, "已匯入 $n 場演唱會", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        enabled = selectedConcerts.isNotEmpty() && !importing,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            if (importing) "匯入中…" else "匯入 ${selectedConcerts.size} 場",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // 整頁放在**同一個 LazyColumn** 裡。
        //
        // 原本是外層 Column（不可捲）＋ 裡面一個 weight(1f) 的 LazyColumn，
        // 所以只有「解析結果」那一段捲得動；上面的說明、選檔按鈕、兩張卡、輸入框
        // 全部固定——螢幕矮一點或鍵盤一開就有東西碰不到。
        // 實機回報：「批量匯入頁面無法捲動，輸入文字時可能會被螢幕鍵盤蓋住。」
        //
        // 不能只把外層包一層 verticalScroll：裡面那個 LazyColumn 用了 weight(1f)，
        // 同方向的捲動疊兩層會丟「measured with infinity maximum height」而崩掉。
        // 所以合成一個：標頭當一個 item、解析結果當 items，沒有嵌套、全部都能捲。
        //
        // imePadding() 移到這裡（原本只有底部按鈕列有），鍵盤才會把內容往上推；
        // LazyColumn 會自動把取得焦點的欄位捲進可見範圍。
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp, end = 16.dp
            )
        ) {
            item {
            Column {
            // ── 說明 + 選檔 ──
            Text(
                "貼上演唱會清單，或選一個 CSV 檔；本 App 匯出的 CSV 可直接匯入。",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("選擇 CSV 檔", modifier = Modifier.padding(start = 6.dp))
                }
                TextButton(onClick = { showExample = !showExample }) {
                    Text(if (showExample) "隱藏格式" else "看格式範例")
                }
            }
            // ── 用 AI 轉檔 ────────────────────────────────────────────
            // 使用者手上的清單長什麼樣子都有：記事本流水帳、Excel 表格、
            // 從售票網站複製下來的一團文字。與其為每一種寫一個解析器（「音樂節卡司」
            // 模式就是這樣死的——只吃一種寫法，差一點就整批失敗），
            // 不如給一段提示詞，讓 AI 幫使用者轉成這裡唯一認得的格式。
            Spacer(Modifier.size(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "清單格式不對？用 AI 轉一次",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "記事本、Excel、從售票網站複製下來的一團文字都可以。" +
                            "複製下面的提示詞，連同你的清單一起丟給任何 AI，" +
                            "把它回的內容貼回上面的欄位就能匯入。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                cm?.setPrimaryClip(
                                    android.content.ClipData.newPlainText("Bandary 匯入提示詞", aiConvertPrompt())
                                )
                                Toast.makeText(context, "提示詞已複製", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Text("複製提示詞", modifier = Modifier.padding(start = 6.dp), fontSize = 13.sp)
                        }
                        TextButton(onClick = { showAiPrompt = !showAiPrompt }) {
                            Text(if (showAiPrompt) "收起" else "看內容", fontSize = 13.sp)
                        }
                    }
                    if (showAiPrompt) {
                        Text(
                            aiConvertPrompt(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            if (showExample) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("欄位順序，後面欄位可省略：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(ConcertImport.HEADER.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(6.dp))
                        Text("範例：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "告五人巡迴,告五人,台北流行音樂中心,台北,台灣,2026-08-15 19:30\n" +
                                "怕胖團,怕胖團,Legacy,台北,,2026/9/1",
                            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("最少只需「名稱或藝人」+「日期」。日期可用 2026-08-15 19:30 或 2026/9/1，純日期預設 19:00。",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.size(10.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 200.dp),
                label = { Text("貼上清單 / CSV 內容") },
                placeholder = { Text("一行一場，逗號分隔") }
            )

            }
            }
            if (rows.isNotEmpty()) {
                item {
                Spacer(Modifier.size(10.dp))
                Text(
                    buildString {
                        append("可匯入 $validCount 場")
                        if (dupCount > 0) append("，其中 $dupCount 場可能已存在，預設不勾選")
                        if (errorCount > 0) append("，$errorCount 筆有問題")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    TextButton(onClick = { rows.forEach { r -> if (r.concert != null) selected[r.lineNumber] = true } }) {
                        Text("全選", fontSize = 13.sp)
                    }
                    TextButton(onClick = { selected.keys.toList().forEach { selected[it] = false } }) {
                        Text("全不選", fontSize = 13.sp)
                    }
                }
                }
                items(rows, key = { it.lineNumber }) { r ->
                        val c = r.concert
                        if (c == null) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text("第 ${r.lineNumber} 筆：${r.error}",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                Text(r.raw, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            val dup = isDup(c)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(
                                    checked = selected[r.lineNumber] == true,
                                    onCheckedChange = { selected[r.lineNumber] = it }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.name.ifBlank { c.artistName }.ifBlank { "(未命名)" },
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        buildString {
                                            append("${c.dateTime.year}/${c.dateTime.monthNumber}/${c.dateTime.dayOfMonth}")
                                            if (c.isMultiDay()) append("+")
                                            if (c.venue.isNotBlank()) append("　${c.venue}")
                                            if (c.name.isNotBlank() && c.artistName.isNotBlank()) append("　${c.artistName}")
                                        },
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                if (dup) {
                                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(6.dp)) {
                                        Text("可能已存在", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}

/**
 * 給使用者複製去餵 AI 的轉檔提示詞。
 *
 * 欄位順序直接讀 [ConcertImport.HEADER]，不另外寫死一份——
 * 格式一改這裡就跟著改，不會出現「說明寫的欄位跟解析器認的不一樣」。
 *
 * 提示詞刻意把**規則寫得比範例多**：AI 照範例仿寫時最容易錯的是
 * 「自己補沒有的資料」與「多加表頭或說明文字」，這兩件都會讓匯入整批壞掉。
 */
private fun aiConvertPrompt(): String = buildString {
    appendLine("請把我接下來給你的演唱會清單，轉成下面這個 CSV 格式。")
    appendLine()
    appendLine("欄位順序（用半形逗號分隔，後面的欄位可以省略）：")
    appendLine(ConcertImport.HEADER.joinToString(","))
    appendLine()
    appendLine("規則：")
    appendLine("1. 一行一場，不要表頭、不要編號、不要任何說明文字或 markdown 的程式碼框。")
    appendLine("2. 最少只需要「名稱或藝人」加「開始日期時間」，其他不知道就留空。")
    appendLine("3. 日期寫成 2026-08-15 19:30 或 2026/9/1。只有日期沒有時間就留日期，我這邊會當成 19:00。")
    appendLine("4. 只有年份不明時才用今年；**不要自己發明**場地、票價、時間等我沒給你的資料，不知道就留空。")
    appendLine("5. 欄位內容本身有逗號的話，用半形雙引號把那一格包起來。")
    appendLine("6. 同一團在不同日期是不同場，各佔一行。")
    // 沒有這條規則時，音樂祭的整排卡司會被 AI 全部塞進「藝人」那一欄。
    // 解析器雖然會拆（見 ConcertImport 的 artistParts），但說清楚比較不會出意外，
    // 而且「第一位當主藝人」這件事要讓使用者知道，他才知道要怎麼排順序。
    appendLine("7. 一場有多位藝人（音樂祭、拼盤）時，藝人欄用「 / 」分隔，第一位放主要的那一位。")
    appendLine("8. 票務狀態只能填：無、已購票、已取票、已看過。類型只能填：演唱會、音樂祭。")
    appendLine()
    appendLine("範例輸出：")
    appendLine("告五人巡迴,告五人,台北流行音樂中心,台北,台灣,2026-08-15 19:30")
    appendLine("怕胖團,怕胖團,Legacy,台北,,2026/9/1")
    appendLine("覺醒音樂祭,滅火器 / 血肉果汁機 / 柯泯薰,台南水萍塭公園,台南,台灣,2026-07-04 14:00,,,音樂祭")
    appendLine()
    appendLine("以下是我的清單：")
}
