/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Timestamp parsing logic adapted from the user-provided ArchiveTune lyrics source.
 * Copyright and authorship notices are retained in QrcLyricsParser.kt.
 */

package com.jay.m3play.lyrics

object LyricsUtils {
    private const val TIMESTAMP = """\d{1,3}:\d{2}(?:[.:]\d{1,3})?"""
    val TIME_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    val LINE_REGEX = Regex("""^((?:\[$TIMESTAMP\]\s*)+)(.*)$""")
    private val wordTimeRegex = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val offsetRegex = Regex("""\[offset:([+-]?\d+)\]""", RegexOption.IGNORE_CASE)
    private val yrcLineRegex = Regex("""^\[(\d{1,8}),(\d{1,8})](.*)$""")
    private val yrcWordRegex = Regex("""\((\d{1,8}),(\d{1,8})(?:,(\d{1,8}))?\)""")
    private const val DEFAULT_WORD_DURATION_MS = 1000L

    private data class RawTimedWord(
        val startOffset: Int,
        val endOffset: Int,
        val startMs: Long,
        val endMs: Long?,
        val text: String,
    )

    private fun timestamp(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull()?.takeIf { it in 0L..59L } ?: return null
        val fraction = match.groupValues[3]
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.times(100L)
            2 -> fraction.toLongOrNull()?.times(10L)
            3 -> fraction.toLongOrNull()
            else -> null
        } ?: return null
        return minutes * 60_000L + seconds * 1000L + millis
    }

    private fun shifted(value: Long, offset: Long): Long = (value + offset).coerceAtLeast(0L)

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        val source = lyrics.removePrefix("\uFEFF").trim()
        if (source.isEmpty() || source.length > 2_000_000 ||
            source.contains("<!DOCTYPE", true) || source.contains("<!ENTITY", true)
        ) return emptyList()

        if (source.trimStart().startsWith("<")) {
            return TtmlLyricsParser.parse(source).ifEmpty { AccompanistLyrics.parseAdditionalFormat(source) }
        }
        val yrcEntries = parseYrc(source)
        if (yrcEntries.isNotEmpty()) return yrcEntries

        if (QrcLyricsParser.isQrc(source)) {
            val qrcEntries = QrcLyricsParser.parseQrc(source).map { line ->
                val text = line.text
                var cursor = 0
                val words = line.words.mapNotNull { word ->
                    if (word.text.isEmpty()) return@mapNotNull null
                    val start = cursor
                    cursor = (cursor + word.text.length).coerceAtMost(text.length)
                    LyricsWord(
                        time = (word.startTime * 1000.0).toLong().coerceAtLeast(0L),
                        start = start.coerceAtMost(text.length),
                        end = cursor,
                        endTime = (word.endTime * 1000.0).toLong().coerceAtLeast(0L),
                    )
                }
                LyricsEntry(
                    time = (line.startTime * 1000.0).toLong().coerceAtLeast(0L),
                    text = text,
                    words = words,
                    endTime = (line.endTime * 1000.0).toLong().takeIf { it > (line.startTime * 1000.0).toLong() },
                )
            }.sorted()
            if (qrcEntries.isNotEmpty()) return qrcEntries
        }

        val lines = source.lines()
        val offset = offsetRegex.find(source)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val result = mutableListOf<LyricsEntry>()
        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("[ti:", true) ||
                line.startsWith("[ar:", true) || line.startsWith("[al:", true) ||
                line.startsWith("[by:", true) || line.startsWith("[re:", true) ||
                line.startsWith("[ve:", true) || line.startsWith("[length:", true) ||
                line.startsWith("[offset:", true)
            ) continue

            val lineMatch = LINE_REGEX.matchEntire(line)
            if (lineMatch != null) {
                val lineTimeTags = TIME_REGEX.findAll(lineMatch.groupValues[1]).toList()
                val rawContent = lineMatch.groupValues[2]
                if (lineTimeTags.isEmpty()) continue
                val nextLineTime = (index + 1 until lines.size).firstNotNullOfOrNull { nextIndex ->
                    val nextMatch = LINE_REGEX.matchEntire(lines[nextIndex].trim()) ?: return@firstNotNullOfOrNull null
                    TIME_REGEX.find(nextMatch.groupValues[1])?.let(::timestamp)
                }
                val markers = wordTimeRegex.findAll(rawContent).toList()
                if (markers.isNotEmpty()) {
                    val visible = StringBuilder()
                    val rawWords = mutableListOf<RawTimedWord>()
                    val firstMarker = markers.first()
                    visible.append(rawContent.substring(0, firstMarker.range.first))
                    markers.forEachIndexed { markerIndex, marker ->
                        val segmentStart = marker.range.last + 1
                        val segmentEnd = markers.getOrNull(markerIndex + 1)?.range?.first ?: rawContent.length
                        val segment = rawContent.substring(segmentStart, segmentEnd)
                        val startMs = timestamp(marker) ?: return@forEachIndexed
                        val prefix = if (markerIndex == 0) rawContent.substring(0, marker.range.first) else ""
                        val wordText = prefix + segment
                        val wordStart = (visible.length - prefix.length).coerceAtLeast(0)
                        visible.append(segment)
                        val wordEnd = visible.length
                        if (wordText.isNotBlank()) {
                            rawWords += RawTimedWord(
                                startOffset = wordStart,
                                endOffset = wordEnd,
                                startMs = startMs,
                                endMs = markers.getOrNull(markerIndex + 1)?.let(::timestamp),
                                text = wordText,
                            )
                        }
                    }
                    val untrimmedText = visible.toString()
                    val trimStart = untrimmedText.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
                    val trimEnd = untrimmedText.indexOfLast { !it.isWhitespace() } + 1
                    val visibleText = if (trimEnd >= trimStart) untrimmedText.substring(trimStart, trimEnd) else ""
                    if (visibleText.isNotEmpty()) {
                        val baseTime = timestamp(lineTimeTags.first()) ?: 0L
                        val lineTimes = lineTimeTags.mapNotNull(::timestamp)
                        lineTimes.forEachIndexed { lineTimeIndex, lineTime ->
                            val delta = lineTime - baseTime + offset
                            val followingLineStart = lineTimes.getOrNull(lineTimeIndex + 1)?.plus(offset)
                                ?: nextLineTime?.plus(offset)
                            val words = rawWords.mapNotNull { word ->
                                val start = (word.startOffset - trimStart).coerceIn(0, visibleText.length)
                                val end = (word.endOffset - trimStart).coerceIn(start, visibleText.length)
                                if (start >= end || word.text.isBlank()) return@mapNotNull null
                                val wordStart = shifted(word.startMs, delta)
                                val wordEndBase = word.endMs?.plus(delta) ?: followingLineStart
                                    ?: (wordStart + DEFAULT_WORD_DURATION_MS)
                                val wordEnd = shifted(wordEndBase, 0L).coerceAtLeast(wordStart + 1L)
                                LyricsWord(wordStart, start, end, wordEnd)
                            }
                            val lineStart = shifted(lineTime, offset)
                            result += LyricsEntry(
                                time = lineStart,
                                text = visibleText,
                                words = words,
                                endTime = words.maxOfOrNull { it.endTime ?: it.time },
                            )
                        }
                    }
                } else {
                    val text = rawContent.replace(Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>"""), "").trim()
                    lineTimeTags.mapNotNullTo(result) { tag ->
                        timestamp(tag)?.let { LyricsEntry(shifted(it, offset), text) }
                    }
                }
                continue
            }

            val yrcMatch = yrcLineRegex.matchEntire(line) ?: continue
            val start = yrcMatch.groupValues[1].toLongOrNull() ?: continue
            val duration = yrcMatch.groupValues[2].toLongOrNull() ?: 0L
            val cleaned = yrcMatch.groupValues[3].replace(yrcWordRegex, "").replace(Regex("""\s+"""), " ").trim()
            if (cleaned.isNotEmpty()) result += LyricsEntry(shifted(start, offset), cleaned, endTime = shifted(start + duration, offset))
        }
        return result.sorted()
    }

    /** Parse NetEase YRC's `(wordStartMs, wordDurationMs, flags)word` segments. */
    private fun parseYrc(source: String): List<LyricsEntry> {
        val offset = offsetRegex.find(source)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val entries = mutableListOf<LyricsEntry>()
        for (rawLine in source.lines()) {
            val line = rawLine.trim()
            val lineMatch = yrcLineRegex.matchEntire(line) ?: continue
            val lineStart = lineMatch.groupValues[1].toLongOrNull() ?: continue
            val lineDuration = lineMatch.groupValues[2].toLongOrNull() ?: 0L
            val content = lineMatch.groupValues[3]
            val markers = yrcWordRegex.findAll(content).toList()
            if (markers.isEmpty() || content.substring(0, markers.first().range.first).isNotBlank()) continue

            val visible = StringBuilder()
            val rawWords = mutableListOf<RawTimedWord>()
            markers.forEachIndexed { markerIndex, marker ->
                val segmentStart = marker.range.last + 1
                val segmentEnd = markers.getOrNull(markerIndex + 1)?.range?.first ?: content.length
                val segment = content.substring(segmentStart, segmentEnd)
                val start = marker.groupValues[1].toLongOrNull() ?: return@forEachIndexed
                val duration = marker.groupValues[2].toLongOrNull() ?: 0L
                val wordStart = visible.length
                visible.append(segment)
                val wordEnd = visible.length
                if (segment.isNotBlank()) {
                    rawWords += RawTimedWord(wordStart, wordEnd, start, start + duration, segment)
                }
            }

            val rawText = visible.toString()
            val trimStart = rawText.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
            val trimEnd = rawText.indexOfLast { !it.isWhitespace() } + 1
            if (trimEnd <= trimStart) continue
            val text = rawText.substring(trimStart, trimEnd)
            val words = rawWords.mapNotNull { word ->
                val wordStart = (word.startOffset - trimStart).coerceIn(0, text.length)
                val wordEnd = (word.endOffset - trimStart).coerceIn(wordStart, text.length)
                if (wordStart >= wordEnd) null
                else {
                    val startTime = shifted(word.startMs, offset)
                    val endTime = shifted(word.endMs ?: (word.startMs + DEFAULT_WORD_DURATION_MS), offset)
                        .coerceAtLeast(startTime + 1L)
                    LyricsWord(startTime, wordStart, wordEnd, endTime)
                }
            }
            val startTime = shifted(lineStart, offset)
            val endTime = shifted(lineStart + lineDuration, offset).takeIf { it > startTime }
            entries += LyricsEntry(startTime, text, words, endTime)
        }
        return entries.sorted()
    }

    fun findCurrentLineIndex(lines: List<LyricsEntry>, position: Long): Int {
        val last = lines.indexOfLast { it.time <= position }
        if (last < 0) return -1
        return lines.indexOfFirst { it.time == lines[last].time }
    }

    fun wordProgress(word: LyricsWord, position: Long, fallbackEndTime: Long): Float {
        val end = word.endTime ?: fallbackEndTime
        if (position < word.time) return 0f
        if (end <= word.time) return 1f
        return ((position - word.time).toDouble() / (end - word.time)).toFloat().coerceIn(0f, 1f)
    }

    /** Estimate word timing only when no real word timestamps are available. */
    fun wordsFor(entry: LyricsEntry, endTime: Long): List<LyricsWord> {
        if (entry.words.isNotEmpty()) return entry.words
        val tokens = Regex("""\S+""").findAll(entry.text).toList()
        val total = tokens.sumOf { it.value.length }.coerceAtLeast(1)
        val duration = (endTime - entry.time).coerceAtLeast(1L)
        var weight = 0
        return tokens.map { token ->
            LyricsWord(entry.time + duration * weight / total, token.range.first, token.range.last + 1)
                .also { weight += token.value.length }
        }
    }
}
