package com.localbeats.ui.components

import androidx.compose.ui.graphics.Color

/** 当音乐文件没有封面图时，根据曲目 id 取一个独特的渐变色调色板 */
internal val placeholderPalettes = listOf(
    listOf(Color(0xFF6A1B9A), Color(0xFF1565C0)),
    listOf(Color(0xFF00695C), Color(0xFF1565C0)),
    listOf(Color(0xFF4A148C), Color(0xFFAD1457)),
    listOf(Color(0xFF0D47A1), Color(0xFF00838F)),
    listOf(Color(0xFF37474F), Color(0xFF6A1B9A)),
    listOf(Color(0xFF880E4F), Color(0xFF4A148C)),
    listOf(Color(0xFF1B5E20), Color(0xFF006064)),
    listOf(Color(0xFFBF360C), Color(0xFF4E342E)),
)
