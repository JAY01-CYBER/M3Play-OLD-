/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

object LyricsUtils {
    private const val TIMESTAMP = "(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?"
    val TIME_REGEX = "\\[$TIMESTAMP\\]".toRegex()
    val LINE_REGEX = "^((?:\\[$TIMESTAMP\\]\\s*)+)(.*)$".toRegex()
    private val wordTimeRegex = "<$TIMESTAMP>".toRegex()
    private val offsetRegex = "\\[offset:([+-]?\\d+)\\]".toRegex(RegexOption.IGNORE_CASE)

    private fun timestamp(match: MatchResult): Long =
        match.groupValues[1].toLong() * 60_000 +
            match.groupValues[2].toLong() * 1_000 +
            match.groupValues[3].padEnd(3, '0').toLong()

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        if (lyrics.trimStart().startsWith("<")) return TtmlLyricsParser.parse(lyrics)
        val offset = offsetRegex.find(lyrics)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return lyrics.lineSequence().flatMap { line ->
            val trimmed = line.trim()
            // Consume only leading timestamps, preserving empty instrumental lines.
            val times = TIME_REGEX.findAll(trimmed).takeWhile { match ->
                trimmed.substring(0, match.range.first).replace(TIME_REGEX, "").isBlank()
            }.toList()
            if (times.isEmpty()) return@flatMap emptySequence()
            val raw = trimmed.substring(times.last().range.last + 1).trim()
            val markers = wordTimeRegex.findAll(raw).toList()
            val text = StringBuilder()
            val words = mutableListOf<LyricsWord>()
            var cursor = 0
            markers.forEachIndexed { index, marker ->
                text.append(raw.substring(cursor, marker.range.first))
                val start = text.length
                val end = markers.getOrNull(index + 1)?.range?.first ?: raw.length
                val segment = raw.substring(marker.range.last + 1, end)
                text.append(segment)
                if (segment.isNotBlank()) {
                    words += LyricsWord(timestamp(marker) + offset, start, text.length,
                        markers.getOrNull(index + 1)?.let { timestamp(it) + offset })
                }
                cursor = end
            }
            if (markers.isEmpty()) text.append(raw)
            val firstTime = timestamp(times.first())
            times.asSequence().map { time ->
                val shift = timestamp(time) - firstTime
                LyricsEntry((timestamp(time) + offset).coerceAtLeast(0), text.toString(),
                    words.map { it.copy(time = (it.time + shift).coerceAtLeast(0),
                        endTime = it.endTime?.let { end -> (end + shift).coerceAtLeast(0) }) })
            }
        }.sorted().toList()
    }

    /** Lines with the same timestamp (unison/translation) stay active together. */
    fun findCurrentLineIndex(lines: List<LyricsEntry>, position: Long): Int {
        val last = lines.indexOfLast { it.time <= position }
        if (last < 0) return -1
        return lines.indexOfFirst { it.time == lines[last].time }
    }

    /** Exact provider end times win; seeking and gaps never leave a half-filled word. */
    fun wordProgress(word: LyricsWord, position: Long, fallbackEndTime: Long): Float {
        val end = word.endTime ?: fallbackEndTime
        if (position < word.time) return 0f
        if (end <= word.time) return 1f
        return ((position - word.time).toDouble() / (end - word.time)).toFloat().coerceIn(0f, 1f)
    }

    /** Estimate word timing only when the provider supplies line timestamps. */
    fun wordsFor(entry: LyricsEntry, endTime: Long): List<LyricsWord> {
        if (entry.words.isNotEmpty()) return entry.words
        val tokens = "\\S+".toRegex().findAll(entry.text).toList()
        val total = tokens.sumOf { it.value.length }.coerceAtLeast(1)
        val duration = (endTime - entry.time).coerceAtLeast(1)
        var weight = 0
        return tokens.map { token ->
            LyricsWord(entry.time + duration * weight / total, token.range.first, token.range.last + 1)
                .also { weight += token.value.length }
        }
    }
}
