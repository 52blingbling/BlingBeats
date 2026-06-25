package com.localbeats.data.lyrics

/**
 * 歌词行数据
 * @param timeMs 该行起始时间（毫秒），LRC 格式解析得到；纯文本则为 0
 * @param text 歌词文本
 */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * 歌词解析器：
 * - 支持 LRC 格式（含时间戳 [mm:ss.xx]），可按播放进度定位当前行
 * - 兼容纯文本歌词（每行视为一条无时间戳的 LyricLine）
 *
 * 支持的 LRC 时间戳格式：
 *   [00:12.34]歌词
 *   [00:12.345]歌词
 *   [00:12]歌词
 *   [01:02.30][02:03.40]重复行（多时间戳）
 */
object LyricsParser {

    private val TIME_TAG_REGEX = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")

    /**
     * 解析原始歌词文本，返回按时间升序排序的歌词行列表。
     * 若文本不含任何 LRC 时间戳，则按行拆分为纯文本（timeMs = -1）。
     */
    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()

        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val result = mutableListOf<LyricLine>()

        for (line in lines) {
            val matches = TIME_TAG_REGEX.findAll(line)
            val tagList = matches.toList()
            if (tagList.isEmpty()) {
                // 无时间戳：纯文本歌词
                result.add(LyricLine(-1L, line))
                continue
            }
            // 提取该行所有时间戳对应的歌词文本（去掉所有 [..] 标签后剩余部分）
            val lyricText = line.replace(TIME_TAG_REGEX, "").trim()
            if (lyricText.isEmpty()) continue
            for (tag in tagList) {
                val (minStr, secStr, fracStr) = tag.destructured
                val minutes = minStr.toLongOrNull() ?: 0L
                val seconds = secStr.toLongOrNull() ?: 0L
                // 小数部分按位数归一化为毫秒：[12.3] → 300ms；[12.34] → 340ms；[12.345] → 345ms
                val ms = when {
                    fracStr.isEmpty() -> 0L
                    fracStr.length == 1 -> (fracStr.toIntOrNull() ?: 0) * 100L
                    fracStr.length == 2 -> (fracStr.toIntOrNull() ?: 0) * 10L
                    else -> (fracStr.take(3).toIntOrNull() ?: 0).toLong()
                }
                val totalMs = minutes * 60_000L + seconds * 1000L + ms
                result.add(LyricLine(totalMs, lyricText))
            }
        }

        // 仅当存在带时间戳的行时才按时间排序（纯文本保持原顺序）
        return if (result.any { it.timeMs >= 0 }) {
            result.filter { it.timeMs >= 0 }.sortedBy { it.timeMs }
        } else {
            result
        }
    }

    /**
     * 是否为有效的 LRC 时间戳歌词（用于判断是否可同步显示）。
     */
    fun isSyncedLyrics(lines: List<LyricLine>): Boolean =
        lines.isNotEmpty() && lines.any { it.timeMs >= 0 }

    /**
     * 根据当前播放进度定位当前应显示的歌词行索引。
     * 返回不超过 positionMs 的最大时间戳行索引；若无匹配返回 -1。
     */
    fun currentLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        // 仅对带时间戳的歌词做定位
        if (!isSyncedLyrics(lines)) return -1
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs in 0..positionMs) {
                idx = i
            } else if (lines[i].timeMs > positionMs) {
                break
            }
        }
        return idx
    }
}
