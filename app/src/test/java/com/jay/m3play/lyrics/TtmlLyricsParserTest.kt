/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

import org.junit.Assert.*
import org.junit.Test

class TtmlLyricsParserTest {
    @Test fun `preserves syllables spaces namespaces and overlapping background timing`() {
        val xml = """<tt:tt xmlns:tt="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><tt:body><tt:div>
          <tt:p begin="00:01.000" end="4s"><tt:span begin="1s" end="1.4s">Hel</tt:span><tt:span begin="1.4s" end="2s">lo</tt:span> <tt:span begin="2s" end="3s">world!</tt:span><tt:span ttm:role="x-bg" begin="2.5s" end="4s"><tt:span begin="2.5s" end="4s">Echo</tt:span></tt:span><tt:span ttm:role="x-translation">Translated</tt:span></tt:p>
        </tt:div></tt:body></tt:tt>"""
        val lines = LyricsUtils.parseLyrics(xml)
        assertEquals(2, lines.size)
        assertEquals("Hello world!", lines[0].text)
        assertEquals(listOf("Hel", "lo", "world!"), lines[0].words.map { lines[0].text.substring(it.start, it.end) })
        assertEquals(listOf(1400L, 2000L, 3000L), lines[0].words.map { it.endTime })
        assertEquals(4000L, lines[0].endTime)
        assertEquals("Echo", lines[1].text)
        assertEquals(2500L, lines[1].time)
    }

    @Test fun `inherits timing of nested spans and ignores XML indentation`() {
        val line = LyricsUtils.parseLyrics("""<tt><body><p begin="1s" end="3s">
          <span begin="1.5s" end="2.5s"><span>世界</span></span>
        </p></body></tt>""").single()
        assertEquals("世界", line.text)
        assertEquals(LyricsWord(1500, 0, 2, 2500), line.words.single())
    }

    @Test fun `supports provider time formats and rejects unsafe or untimed documents`() {
        assertEquals(3723125L, TtmlLyricsParser.time("01:02:03.125"))
        assertEquals(62300L, TtmlLyricsParser.time("1:02.3"))
        assertEquals(250L, TtmlLyricsParser.time("250ms"))
        assertEquals(1500L, TtmlLyricsParser.time("1.5s"))
        assertNull(TtmlLyricsParser.time("NaN"))
        assertNull(TtmlLyricsParser.time("-1s"))
        for (xml in listOf("<tt/>", "<tt><body><p>No timing</p></body></tt>",
            "<!DOCTYPE tt><tt><body><p begin='1s'>Bad</p></body></tt>", "<".repeat(2_000_001))) {
            assertTrue(LyricsUtils.parseLyrics(xml).isEmpty())
        }
    }
}
