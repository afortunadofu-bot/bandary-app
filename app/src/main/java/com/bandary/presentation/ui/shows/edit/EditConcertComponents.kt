package com.bandary.presentation.ui.shows.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions as FKeyboardOptions

/** EditRow 尾端樣式（spec §4.1）。 */
enum class RowTrailing { None, Chevron, ExternalLink, Switch }

/** 群組首列不畫上分隔線；其餘列頂部 1dp Line。此旗標由呼叫端控制。 */
@Composable
private fun EditRowFrame(
    firstInGroup: Boolean,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    val base = Modifier
        .fillMaxWidth()
        .background(EditTokens.Card)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    Column(base) {
        if (!firstInGroup) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(EditTokens.Line))
        }
        Box(Modifier.padding(horizontal = EditTokens.rowPaddingH, vertical = EditTokens.rowPaddingV)) {
            content()
        }
    }
}

@Composable
private fun RowLabel(label: String, supportingText: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = EditTokens.labelSmall, fontWeight = EditTokens.wLabel, color = EditTokens.InkFaint
        )
        if (!supportingText.isNullOrBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                supportingText,
                fontSize = EditTokens.caption, fontWeight = EditTokens.wCaption, color = EditTokens.InkFaint
            )
        }
    }
}

@Composable
private fun WarnStrip(warning: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp)
            .background(EditTokens.WarnTint, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(warning, fontSize = EditTokens.caption, color = EditTokens.Warn)
    }
}

/**
 * 動作 / 顯示列（spec §4.1）：label（上）+ value（下）。value 為 null 顯示 placeholder。
 * 用於：點開日期選擇、外連、Switch 等；純顯示的摘要也用它。
 */
@Composable
fun EditRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    firstInGroup: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
    warning: String? = null,
    isLink: Boolean = false,
    trailing: RowTrailing = RowTrailing.None,
    switchChecked: Boolean = false,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    EditRowFrame(firstInGroup, onClick) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RowLabel(label, supportingText)
                Spacer(Modifier.height(4.dp))
                val shown = value?.takeIf { it.isNotBlank() }
                Text(
                    shown ?: (placeholder ?: ""),
                    fontSize = EditTokens.bodyLarge,
                    fontWeight = EditTokens.wValue,
                    color = when {
                        shown == null -> EditTokens.InkFaint
                        isLink -> EditTokens.SageDeep
                        else -> EditTokens.Ink
                    },
                    softWrap = true,
                    overflow = TextOverflow.Clip
                )
                if (!warning.isNullOrBlank()) WarnStrip(warning)
            }
            when (trailing) {
                RowTrailing.Chevron -> Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                    tint = EditTokens.InkFaint, modifier = Modifier.size(20.dp)
                )
                RowTrailing.ExternalLink -> Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew, null,
                    tint = EditTokens.SageDeep, modifier = Modifier.size(18.dp)
                )
                RowTrailing.Switch -> Switch(
                    checked = switchChecked,
                    onCheckedChange = onSwitchChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = EditTokens.SageDeep)
                )
                RowTrailing.None -> {}
            }
        }
    }
}

/**
 * 內嵌可編輯文字列（spec §6 需鍵盤 / 驗證）：label（上）+ BasicTextField（下）。
 * 長內容 softWrap 換行不截斷（不用 Ellipsis，符合 §10）。
 */
@Composable
fun EditTextRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    firstInGroup: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
    warning: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    keyboardOptions: FKeyboardOptions = FKeyboardOptions.Default,
) {
    EditRowFrame(firstInGroup, null) {
        Column(modifier.fillMaxWidth()) {
            RowLabel(label, supportingText)
            Spacer(Modifier.height(4.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = EditTokens.bodyLarge, fontWeight = EditTokens.wValue,
                    color = if (isError) EditTokens.Warn else EditTokens.Ink
                ),
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                cursorBrush = SolidColor(EditTokens.SageDeep),
                modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp),
                decorationBox = { inner ->
                    if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                        Text(placeholder, fontSize = EditTokens.bodyLarge, color = EditTokens.InkFaint)
                    }
                    inner()
                }
            )
            if (!warning.isNullOrBlank()) WarnStrip(warning)
        }
    }
}

/** 並排兩欄（spec §4.2）：日期/時間、國家/縣市、票價/座位、開賣日期/時間。 */
@Composable
fun EditRow2Col(
    firstInGroup: Boolean = false,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(EditTokens.Card)) {
        if (!firstInGroup) Box(Modifier.fillMaxWidth().height(1.dp).background(EditTokens.Line))
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { left() }
            Box(Modifier.width(1.dp).height(52.dp).background(EditTokens.Line))
            Box(Modifier.weight(1f)) { right() }
        }
    }
}

/** ≤4 短選項用（spec §4.3）：購票狀態、開賣提醒、會想再看。 */
@Composable
fun <T> SegmentedRow(
    options: List<T>,
    selected: T?,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .background(EditTokens.SageTint, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { opt ->
            val on = opt == selected
            Box(
                Modifier.weight(1f)
                    .background(
                        if (on) EditTokens.SageDeep else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(opt) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    labelOf(opt),
                    fontSize = EditTokens.caption,
                    fontWeight = FontWeight.W700,
                    color = if (on) EditTokens.Card else EditTokens.InkSoft,
                    maxLines = 1
                )
            }
        }
    }
}

/** 可換行 chip 列（spec §4.4）——禁 Row+horizontalScroll。用於取票機台、歌手、快捷動作。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

/** 群組小標（spec §4.6）：11sp W700 InkFaint，背景 Bg（不是 Card）。 */
@Composable
fun GroupCaption(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().background(EditTokens.Bg).padding(top = 10.dp, bottom = 4.dp, start = EditTokens.rowPaddingH)
    ) {
        Text(text, fontSize = EditTokens.caption, fontWeight = FontWeight.W700, color = EditTokens.InkFaint)
    }
}
