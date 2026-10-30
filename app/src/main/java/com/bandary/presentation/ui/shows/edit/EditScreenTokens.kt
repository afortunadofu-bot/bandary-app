package com.bandary.presentation.ui.shows.edit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 編輯演唱會頁面的設計 token（spec §3）。全畫面禁止硬編碼色碼與尺寸，一律引這裡。
 * 這是刻意固定的淺色票根視覺，不隨主題切換——編輯頁走「票根紙感」而非 app 全域配色。
 */
object EditTokens {
    // ── Color ──
    val Bg = Color(0xFFFAFAF6)
    val Card = Color(0xFFFFFFFF)
    val Line = Color(0xFFEDEFE7)
    val Ink = Color(0xFF20241D)
    val InkSoft = Color(0xFF5B6355)
    val InkFaint = Color(0xFF9BA292)
    val Sage = Color(0xFF4C6B4A)
    val SageDeep = Color(0xFF3B5539)   // 主要行動 / Tab indicator
    val SageTint = Color(0xFFE9F0E6)
    val Warn = Color(0xFFB8722E)
    val WarnTint = Color(0xFFFBF0E4)
    val Star = Color(0xFFD9A441)       // 評分 + 心得 badge

    // ── Type（四級，不得增加）──
    val titleLarge = 17.sp     // TopAppBar
    val titleMedium = 15.sp    // Tab、群組標題
    val bodyLarge = 15.sp      // 欄位值
    val labelSmall = 12.sp     // 欄位 label
    val caption = 11.sp        // 輔助說明

    val wTitle = FontWeight.W700
    val wValue = FontWeight.W500
    val wLabel = FontWeight.W700
    val wCaption = FontWeight.W500

    // ── Hero 專用 ──
    val countdownNum = 30.sp   // 倒數天數
    val ticketTitle = 18.sp

    // ── Shape / Spacing ──
    val rowPaddingH = 20.dp
    val rowPaddingV = 14.dp
    val cardRadius = 16.dp
    val heroRadius = 20.dp
    val heroHeight = 280.dp
}
