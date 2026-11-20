/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import org.junit.Assert.*
import org.junit.Test

class AccompanistLyricsTest {
    @Test fun `provider seconds and namespaces preserve rich vocals and captions`() {
        val xml = """<tt:tt xmlns:tt="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><tt:body><tt:div>
            <tt:p begin="1s" end="4000ms"><tt:span begin="1s" end="1.5s">Hello</tt:span> <tt:span begin="2s" end="3s">world</tt:span><tt:span ttm:role="x-bg" begin="2.5s" end="4s"><tt:span begin="2.5s" end="4s">Echo</tt:span></tt:span><tt:span ttm:role="x-translation">Translation</tt:span></tt:p>
            </tt:div></tt:body></tt:tt>"""
        val line = AccompanistLyrics.parse(xml, 5000).lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals("Hello world", line.text())
        assertEquals(1000, line.start)
        assertEquals(4000, line.end)
        assertEquals(listOf(1500, 3000), line.syllables.map { it.end })
        assertEquals("Translation", line.translation)
        val background = line.accompanimentLines!!.single()
        assertEquals("Echo", background.text())
        assertEquals(2500, background.start)
        assertEquals(4000, background.end)
    }

    @Test fun `short LRC timestamps and file offset are normalized for core`() {
        val lines = AccompanistLyrics.parse("[offset:-500]\n[00:01]One\n[00:03.5]Two", 6000).lines
        assertEquals(listOf(500, 3000), lines.map { it.start })
        assertEquals(listOf(3000, 6000), lines.map { it.end })
        assertEquals(listOf("One", "Two"), lines.map { it.text() })
    }

    @Test fun `word preference preserves nested vocals and captions without syllables`() {
        val source = AccompanistLyrics.parse("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><p begin="1s" end="4s"><span begin="1s" end="2s">Lead</span><span ttm:role="x-bg"><span begin="2s" end="3s">Echo</span></span><span ttm:role="x-translation">Caption</span></p></body></tt>""", 5000)
        val lines = AccompanistLyrics.withoutWordTiming(source).lines
        assertTrue(lines.all { it is SyncedLine })
        assertEquals(listOf("Lead", "Echo"), lines.map { it.text() })
        assertEquals("Caption", lines.first().translation())
        assertEquals(listOf(1000, 2000), lines.map { it.start })
    }

    @Test fun `provider TTML fallback retains inherited timing`() {
        val line = AccompanistLyrics.parse("""<tt><body><p begin="1s" end="3s"><span begin="1.5s" end="2.5s"><span>世界</span></span></p></body></tt>""", 5000).lines.single()
        assertEquals("世界", line.text())
        assertTrue(line is KaraokeLine)
        val word = (line as KaraokeLine).syllables.single()
        assertEquals(1500, word.start)
        assertEquals(2500, word.end)
    }

    @Test fun `enhanced LRC retains singer alignment`() {
        val lines = AccompanistLyrics.parse("[00:01.00]v1: <00:01.00>Lead<00:02.00>\n[00:03.00]v2: <00:03.00>Reply<00:04.00>", 5000).lines
        assertEquals(listOf(com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment.Start,
            com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment.End),
            lines.map { (it as KaraokeLine).alignment })
    }

    @Test fun `core formats also work in provider validation and manual previews`() {
        val input = "[1000,2000](1000,1000,0)Hello (2000,1000,0)world"
        val entries = LyricsUtils.parseLyrics(input)
        assertEquals("Hello world", entries.single().text)
        assertEquals(listOf(1000L, 2000L), entries.single().words.map { it.time })
        assertEquals("Hello world", AccompanistLyrics.parse(input, 4000).lines.single().text())
    }

    @Test fun `romanization remains a caption and does not become a timed word`() {
        val input = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><p begin="1s" end="3s"><span begin="1s" end="2s">世界</span><span ttm:role="x-roman">Sekai</span></p></body></tt>"""
        val line = AccompanistLyrics.parse(input, 4000).lines.single()
        assertEquals("世界", line.text())
        assertEquals("Sekai", line.phonetic())
    }

    @Test fun `line timing estimates preserve spaces and never replace provider syllables`() {
        val lineOnly = AccompanistLyrics.parse("[00:01.00]Hello wonderful world", 5000)
        val estimated = AccompanistLyrics.withEstimatedTiming(lineOnly).lines.single() as KaraokeLine
        assertEquals("Hello wonderful world", estimated.text())
        assertEquals(1000, estimated.syllables.first().start)
        assertEquals(5000, estimated.syllables.last().end)
        assertTrue(estimated.syllables.zipWithNext().all { (a, b) -> a.end == b.start })
        val exact = AccompanistLyrics.parse("[00:01.00]<00:01.00>One <00:02.00>two<00:03.00>", 4000)
        assertEquals(exact, AccompanistLyrics.withEstimatedTiming(exact))
    }

    @Test fun `unsafe and oversized XML and plain text are not rendered as timed lyrics`() {
        for (input in listOf("<!DOCTYPE tt><tt><body/></tt>", "<!ENTITY x 'y'><tt/>",
            "<".repeat(2_000_001), "Plain lyrics", "<tt><body><p>Untimed</p></body></tt>")) {
            assertTrue(AccompanistLyrics.parse(input, 5000).lines.isEmpty())
        }
    }

    @Test fun `QRC word timestamps reach karaoke syllables without estimation`() {
        val line = AccompanistLyrics.parse("[1000,2500]Hello (1000,800,0)world (1800,700,0)", 5000)
            .lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals("Hello world", line.text())
        assertEquals(listOf(1000, 1800), line.syllables.map { it.start })
        assertEquals(listOf(1800, 2500), line.syllables.map { it.end })
    }
}
