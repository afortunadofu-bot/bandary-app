package com.bandary.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bandary.domain.model.ArtistEntry
import com.bandary.domain.model.containsArtist
import com.bandary.domain.model.searchArtists

/**
 * 藝人搜尋欄：打字即查共用藝人資料庫，查不到就給一個「新增到資料庫」。
 *
 * 之所以要「查不到也能加」：以前藝人只是每筆記錄裡的字串，所以只找得到**自己看過**的藝人，
 * 想追蹤一個還沒看過的團，App 裡根本沒有那個名字可選。加進去之後其他人也搜得到。
 *
 * 這個元件不碰網路，資料與新增動作都由呼叫端傳進來——選歌、新增演唱會、追蹤藝人、
 * 活動投稿之後都會用到同一個元件，各自的資料來源不該綁死在這裡。
 */
@Composable
fun ArtistPicker(
    directory: List<ArtistEntry>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "搜尋藝人",
    placeholder: String = "打幾個字就會跳建議",
    loading: Boolean = false,
    /** null＝不提供「新增到資料庫」（例如還沒登入）。 */
    onCreate: ((String) -> Unit)? = null,
    /** 已經選過的，結果列表裡會標出來。 */
    alreadyPicked: Set<String> = emptySet()
) {
    var query by remember { mutableStateOf("") }
    val hits = remember(query, directory) { directory.searchArtists(query) }
    val trimmed = query.trim()
    // 打的字跟資料庫裡完全沒有對應時才給「新增」——不然每次都跳一個誘人按錯的按鈕
    val canCreate = onCreate != null && trimmed.isNotBlank() && !directory.containsArtist(trimmed)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    query.isNotBlank() -> IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "清除")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (trimmed.isNotBlank() && (hits.isNotEmpty() || canCreate)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    hits.forEachIndexed { i, entry ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val picked = entry.name in alreadyPicked
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = !picked) { onPick(entry.name); query = "" }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (picked) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (entry.aliases.isNotEmpty()) {
                                    Text(
                                        entry.aliases.joinToString("、"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (entry.verified) {
                                Icon(
                                    Icons.Outlined.Verified, "已確認的名字",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (picked) {
                                Text(
                                    "已加入",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (canCreate) {
                        if (hits.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onCreate!!(trimmed); query = "" }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Add, null,
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "新增「$trimmed」",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "加進共用的藝人資料庫，之後大家都搜得到",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
