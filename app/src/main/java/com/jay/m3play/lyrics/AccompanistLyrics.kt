/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** Provider compatibility at the boundary; Accompanist owns the rich vocal/caption model. */
internal object AccompanistLyrics {
    fun parse(content: String, duration: Long): SyncedLyrics {
        val input = content.removePrefix("\uFEFF").trim()
        if (input.length > 2_000_000 || input.contains("<!DOCTYPE", true) || input.contains("<!ENTITY", true)) {
            return SyncedLyrics(emptyList())
        }
        val parsed = readCore(input)
        val model = parsed?.takeIf { it.lines.isNotEmpty() }
            ?: fromEntries(LyricsUtils.parseLyrics(input), duration)
        return model.copy(lines = model.lines.mapIndexed { index, line ->
            // LRC has no explicit final end; never keep a line active for Int.MAX_VALUE ms.
            val next = model.lines.drop(index + 1).firstOrNull { it.start > line.start }?.start
            val end = if (line.end == Int.MAX_VALUE || line.end <= line.start)
                next ?: duration.takeIf { it > line.start }?.millis() ?: (line.start + 5000)
                else line.end
            withEnd(line, end.coerceAtLeast(line.start))
        })
    }

    private fun readCore(input: String): SyncedLyrics? = runCatching {
        AutoParser().parse(if (input.startsWith('<')) normalizeTtml(input) else normalizeLrc(input))
    }.getOrNull()

    /** Existing provider validation, search previews and exports can also read Core's other formats. */
    fun parseAdditionalFormat(input: String): List<LyricsEntry> {
        if (input.length > 2_000_000 || input.contains("<!DOCTYPE", true) || input.contains("<!ENTITY", true)) return emptyList()
        return readCore(input)?.lines.orEmpty().flatMap { line ->
            listOf(line) + (line as? KaraokeLine.MainKaraokeLine)?.accompanimentLines.orEmpty()
        }.map { line ->
            var offset = 0
            val words = (line as? KaraokeLine)?.syllables.orEmpty().map { syllable ->
                val start = offset
                offset += syllable.content.length
                LyricsWord(syllable.start.toLong(), start, offset, syllable.end.toLong())
            }
            LyricsEntry(line.start.toLong(), line.text(), words, line.end.toLong())
        }.sorted()
    }

    /** Core accepts clock times; providers also return seconds/ms and namespace-prefixed tags. */
    private fun normalizeTtml(input: String): String {
        val document = Jsoup.parse(input, "", Parser.xmlParser())
        val elements = document.getAllElements()
        require(elements.size <= 20_000 && document.select("p").size <= 2000)
        document.outputSettings().prettyPrint(false)
        elements.forEach { element ->
            element.tagName(element.normalName().substringAfter(':'))
            for (name in listOf("begin", "end")) {
                if (element.hasAttr(name)) {
                    val time = TtmlLyricsParser.time(element.attr(name)) ?: error("Invalid TTML time")
                    require(time < Int.MAX_VALUE)
                    element.attr(name, "${time / 1000}.${(time % 1000).toString().padStart(3, '0')}")
                }
            }
        }
        document.selectFirst("tt")?.attr("xmlns", "http://www.w3.org/ns/ttml")
        // Inherit timing for nested syllable spans used by several providers.
        val spans = document.select("span")
        fun org.jsoup.nodes.Element.role() = attributes().firstOrNull {
            it.key.substringAfter(':') == "role"
        }?.value
        spans.forEach { span ->
            val caption = (listOf(span) + span.parents()).any { it.role() in setOf("x-translation", "x-roman") }
            if (caption || span.role() == "x-bg") return@forEach
            for (name in listOf("begin", "end")) {
                if (!span.hasAttr(name)) span.parents().firstOrNull { it.hasAttr(name) }
                    ?.let { span.attr(name, it.attr(name)) }
            }
        }
        // Core expects timed syllables directly under a vocal paragraph/background span.
        spans.filter { span -> span.role() == null && span.ownText().isBlank() &&
            span.children().any { it.normalName() == "span" } }.forEach { it.unwrap() }
        return document.outerHtml()
    }

    private fun normalizeLrc(input: String): String {
        val offset = Regex("\\[offset:([+-]?\\d+)\\]", RegexOption.IGNORE_CASE)
        val shift = offset.find(input)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val markers = Regex("([\\[<])(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?([\\]>])")
        return markers.replace(input.replace(offset, "")) { match ->
            val time = (match.groupValues[2].toLong() * 60000 + match.groupValues[3].toLong() * 1000 +
                match.groupValues[4].padEnd(3, '0').toLong() + shift).coerceIn(0, Int.MAX_VALUE.toLong() - 1)
            "${match.groupValues[1]}${(time / 60000).toString().padStart(2, '0')}:${((time / 1000) % 60).toString().padStart(2, '0')}.${(time % 1000).toString().padStart(3, '0')}${match.groupValues[5]}"
        }
    }

    private fun fromEntries(entries: List<LyricsEntry>, duration: Long) = SyncedLyrics(entries.mapIndexed { index, entry ->
        val end = entry.endTime ?: entries.drop(index + 1).firstOrNull { it.time > entry.time }?.time
            ?: duration.takeIf { it > entry.time } ?: (entry.time + 5000)
        if (entry.words.isEmpty()) SyncedLine(entry.text, null, entry.time.millis(), end.millis())
        else KaraokeLine.MainKaraokeLine(
            syllables = entry.words.mapIndexed { wordIndex, word -> KaraokeSyllable(
                entry.text.substring(word.start, word.end), word.time.millis(),
                (word.endTime ?: entry.words.getOrNull(wordIndex + 1)?.time ?: end).coerceAtLeast(word.time).millis(),
            ) },
            translation = null, alignment = KaraokeAlignment.Unspecified,
            start = entry.time.millis(), end = end.coerceAtLeast(entry.time).millis(),
        )
    })

    /** A line-only source can still animate; real provider syllables are always preserved. */
    fun withEstimatedTiming(lyrics: SyncedLyrics): SyncedLyrics = lyrics.copy(lines = lyrics.lines.map { line ->
        if (line !is SyncedLine || line.content.isBlank()) line else {
            val words = LyricsUtils.wordsFor(LyricsEntry(line.start.toLong(), line.content), line.end.toLong())
            KaraokeLine.MainKaraokeLine(
                syllables = words.mapIndexed { index, word ->
                    val startOffset = if (index == 0) 0 else word.start
                    val nextOffset = words.getOrNull(index + 1)?.start ?: line.content.length
                    KaraokeSyllable(line.content.substring(startOffset, nextOffset), word.time.millis(),
                        (words.getOrNull(index + 1)?.time ?: line.end.toLong()).millis())
                },
                translation = line.translation, alignment = KaraokeAlignment.Unspecified,
                start = line.start, end = line.end, phonetic = line.phonetic, languageTag = line.languageTag,
            )
        }
    })

    /** Disabling word animation keeps captions/voices while using line-level rendering. */
    fun withoutWordTiming(lyrics: SyncedLyrics): SyncedLyrics = lyrics.copy(lines = lyrics.lines.flatMap { line ->
        listOf(SyncedLine(line.text(), line.translation(), line.start, line.end, line.phonetic())) +
            (line as? KaraokeLine.MainKaraokeLine)?.accompanimentLines.orEmpty().map {
                SyncedLine(it.text(), it.translation, it.start, it.end, it.phonetic)
            }
    }.sortedBy { it.start })

    private fun withEnd(line: ISyncedLine, end: Int): ISyncedLine = when (line) {
        is SyncedLine -> line.copy(end = end)
        is KaraokeLine.MainKaraokeLine -> line.copy(end = end)
        is KaraokeLine.AccompanimentKaraokeLine -> line.copy(end = end)
        else -> line
    }
}

internal fun ISyncedLine.text(): String = when (this) {
    is KaraokeLine -> syllables.joinToString("") { it.content }
    is SyncedLine -> content
    else -> ""
}
internal fun ISyncedLine.translation(): String? = when (this) {
    is KaraokeLine -> translation
    is SyncedLine -> translation
    else -> null
}
internal fun ISyncedLine.phonetic(): String? = when (this) {
    is KaraokeLine -> phonetic
    is SyncedLine -> phonetic
    else -> null
}
internal fun Long.millis(): Int = coerceIn(0, Int.MAX_VALUE.toLong() - 1).toInt()
