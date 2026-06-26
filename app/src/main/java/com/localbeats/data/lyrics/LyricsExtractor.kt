package com.localbeats.data.lyrics

import android.content.Context
import android.net.Uri
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * 使用 jaudiotagger 提取嵌入式歌词标签。
 *
 * jaudiotagger 需要可寻址的 File 对象，SAF Uri 必须先复制到缓存目录。
 * 覆盖格式：MP3 (ID3 USLT/SYLT)、M4A (iTunes LYRICS)、FLAC (LYRICS)、OGG (LYRICS)。
 *
 * 提取策略（按优先级尝试多个字段，提高不同写入工具的兼容性）：
 * 1. FieldKey.LYRICS（统一字段，覆盖大多数容器）
 * 2. FieldKey.LYRICS 不行时，遍历 tag 所有字段，查找含 LYRICS/USLT 的项
 * 3. 最终返回 null
 *
 * 注：复制文件仅在初次扫描时执行一次，性能可接受。
 */
object LyricsExtractor {

    private const val TAG = "LyricsExtractor"

    /**
     * 从音频 Uri 提取嵌入式歌词文本，失败返回 null。
     * @param context 用于访问 ContentResolver
     * @param uri 音频文件 SAF Uri
     * @param cacheKey 缓存临时文件命名用 key（避免冲突）
     */
    fun extract(context: Context, uri: Uri, cacheKey: String): String? {
        val tempFile = File(context.cacheDir, "lyrics_tmp_$cacheKey")
        return try {
            // 复制 SAF Uri 到临时文件（jaudiotagger 需要 File）
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false
            if (!copied) {
                Log.w(TAG, "复制文件失败: $uri")
                return null
            }
            Log.d(TAG, "临时文件大小=${tempFile.length()} bytes, uri=$uri")

            val audioFile = try {
                AudioFileIO.read(tempFile)
            } catch (t: Throwable) {
                Log.w(TAG, "AudioFileIO.read 失败: ${t.javaClass.simpleName}: ${t.message}")
                return null
            }
            // jaudiotagger 2.2.3 的 AudioFile.getTag() 在无 tag 时抛异常或返回 null，统一用 try/catch 处理
            val tag = try {
                audioFile.tag
            } catch (t: Throwable) {
                Log.w(TAG, "读取 tag 失败: ${t.message}")
                null
            }
            if (tag == null) {
                Log.w(TAG, "无 tag: $uri")
                return null
            }

            // 主路径：LYRICS 字段（jaudiotagger 抽象后的统一字段，覆盖 ID3 USLT / iTunes ©lyr / FLAC LYRICS）
            val primary: String? = try {
                tag.getFirst(FieldKey.LYRICS)
            } catch (t: Throwable) {
                Log.w(TAG, "getFirst(LYRICS) 异常: ${t.message}")
                null
            }
            if (primary != null && primary.isNotBlank()) {
                Log.i(TAG, "提取到歌词（LYRICS字段），长度=${primary.length}")
                return cleanLyrics(primary)
            }

            // 兜底1：少数工具把歌词写入 COMMENT 字段，用启发式判断避免误把短评论当歌词
            val comment: String? = try {
                tag.getFirst(FieldKey.COMMENT)
            } catch (_: Throwable) { null }
            if (comment != null && comment.isNotBlank() && looksLikeLyrics(comment)) {
                Log.i(TAG, "兜底提取到歌词（COMMENT），长度=${comment.length}")
                return cleanLyrics(comment)
            }

            // 兜底2：反射遍历 tag 所有字段，查找 id 含 LYRIC/USLT 的非标准字段
            // （覆盖 jaudiotagger FieldKey 未映射的自定义 TXXX 帧）
            val reflective = extractByReflection(tag)
            if (reflective != null && reflective.isNotBlank()) {
                Log.i(TAG, "反射兜底提取到歌词，长度=${reflective.length}")
                return cleanLyrics(reflective)
            }

            Log.i(TAG, "未找到歌词字段: $uri")
            null
        } catch (t: Throwable) {
            Log.w(TAG, "提取失败: ${t.javaClass.simpleName}: ${t.message}")
            null
        } finally {
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Throwable) {
                // 忽略删除失败
            }
        }
    }

    /** 清洗歌词文本：去语言代码行、空白修剪 */
    private fun cleanLyrics(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) return text
        // 部分播放器写入的 USLT 含语言描述前缀（如 "eng" 单独一行），去除纯语言代码行
        val lines = text.split("\n").toMutableList()
        val iterator = lines.listIterator()
        while (iterator.hasNext()) {
            val line = iterator.next().trim()
            // 去除仅含 2-3 字母语言代码的行（如 "eng", "chi"）
            if (line.length in 2..3 && line.all { it.isLetter() }) {
                iterator.remove()
            }
        }
        text = lines.joinToString("\n").trim()
        return text
    }

    /**
     * 启发式判断文本是否像歌词（避免把短评论误判为歌词）。
     * 满足以下任一条件视为歌词：
     * - 含 LRC 时间戳（[mm:ss.xx]）
     * - 多行文本（≥3 行）且总长度 ≥30
     */
    private fun looksLikeLyrics(text: String): Boolean {
        if (text.contains(Regex("""\[\d+:\d+"""))) return true
        val lineCount = text.lines().count { it.isNotBlank() }
        return lineCount >= 3 && text.length >= 30
    }

    /**
     * 反射遍历 tag 所有字段，查找 id 含 LYRIC/USLT 的非标准字段并取内容。
     * jaudiotagger 不同格式 TagField 的内容获取方法不一，用反射尽量兼容；
     * 任何反射异常都安全吞掉，返回 null。
     */
    private fun extractByReflection(tag: Any): String? {
        return try {
            val getFieldsMethod = tag.javaClass.getMethod("getFields")
            val fieldsObj = getFieldsMethod.invoke(tag) ?: return null
            // jaudiotagger 的 getFields() 返回 Iterator<TagField>
            if (fieldsObj !is Iterator<*>) return null
            while (fieldsObj.hasNext()) {
                val field = fieldsObj.next() ?: continue
                val id = try {
                    field.javaClass.getMethod("getId").invoke(field) as? String
                } catch (_: Throwable) { null }
                if (id == null || !(id.contains("LYRIC", true) ||
                        id.contains("USLT", true) || id.contains("LYR", true))) {
                    continue
                }
                // 依次尝试 getContent / getFirstContent / toString 取内容
                val content = try {
                    field.javaClass.getMethod("getContent").invoke(field) as? String
                } catch (_: Throwable) {
                    try {
                        field.javaClass.getMethod("getFirstContent").invoke(field) as? String
                    } catch (_: Throwable) {
                        try {
                            field.toString()
                        } catch (_: Throwable) { null }
                    }
                }
                if (!content.isNullOrBlank() && looksLikeLyrics(content)) {
                    return content
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }
}
