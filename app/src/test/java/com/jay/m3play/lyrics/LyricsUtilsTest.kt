/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUtilsTest {
    @Test fun `parses line timestamps with optional fractional seconds and instrumental gaps`() {
        val lines = LyricsUtils.parseLyrics("[ar:Artist]\n[00:01]One\n[00:02.5]\n[00:03.25]Three\n[00:04.125]Four")
        assertEquals(listOf(1000L, 2500L, 3250L, 4125L), lines.map { it.time })
        assertEquals("", lines[1].text)
    }

    @Test fun `enhanced LRC preserves display text and word offsets`() {
        val line = LyricsUtils.parseLyrics("[00:01.00]<00:01.00>Hello, <00:01.50>world!<00:02.00>").single()
        assertEquals("Hello, world!", line.text)
        assertEquals(listOf(LyricsWord(1000, 0, 7, 1500), LyricsWord(1500, 7, 13, 2000)), line.words)
    }

    @Test fun `repeated lines shift word timestamps with the line`() {
        val lines = LyricsUtils.parseLyrics("[00:01][00:05]<00:01>Hello <00:02>again")
        assertEquals(listOf(1000L, 5000L), lines.map { it.time })
        assertEquals(listOf(5000L, 6000L), lines[1].words.map { it.time })
    }

    @Test fun `file offset shifts line and word timing and clamps negative timestamps`() {
        val line = LyricsUtils.parseLyrics("[offset:-1500]\n[00:01]<00:01>Hello <00:02>world").single()
        assertEquals(0L, line.time)
        assertEquals(listOf(0L, 500L), line.words.map { it.time })
    }

    @Test fun `unison lines activate together exactly at their timestamp`() {
        val lines = LyricsUtils.parseLyrics("[00:01]Lead\n[00:01]Harmony\n[00:03]Next")
        assertEquals(-1, LyricsUtils.findCurrentLineIndex(lines, 999))
        assertEquals(0, LyricsUtils.findCurrentLineIndex(lines, 1000))
        assertEquals(0, LyricsUtils.findCurrentLineIndex(lines, 2999))
        assertEquals(2, LyricsUtils.findCurrentLineIndex(lines, 3000))
        // Seeking backwards restores the whole simultaneous group.
        assertEquals(0, LyricsUtils.findCurrentLineIndex(lines, 1500))
    }

    @Test fun `metadata and plain lyrics do not masquerade as synced lyrics`() {
        assertTrue(LyricsUtils.parseLyrics("[ar:Artist]\nPlain lyrics\nAnother line").isEmpty())
        assertEquals(-1, LyricsUtils.findCurrentLineIndex(emptyList(), 0))
    }

    @Test fun `estimated word timing is weighted and keeps punctuation and unicode offsets`() {
        val line = LyricsEntry(1000, "Hi 世界! wonderful")
        val words = LyricsUtils.wordsFor(line, 4000)
        assertEquals(listOf("Hi", "世界!", "wonderful"), words.map { line.text.substring(it.start, it.end) })
        assertEquals(1000L, words.first().time)
        assertTrue(words.zipWithNext().all { (a, b) -> b.time > a.time })
        assertTrue(words.last().time < 4000L)
    }

    @Test fun `provider word timing takes precedence over estimates`() {
        val line = LyricsUtils.parseLyrics("[00:01]<00:01.1>One <00:02.4>two").single()
        assertEquals(line.words, LyricsUtils.wordsFor(line, 5000))
    }
    @Test fun `word sweep follows provider timing gaps and backwards seeking`() {
        val word = LyricsWord(1000, 0, 5, 2000)
        assertEquals(0f, LyricsUtils.wordProgress(word, 999, 5000))
        assertEquals(0.5f, LyricsUtils.wordProgress(word, 1500, 5000))
        assertEquals(1f, LyricsUtils.wordProgress(word, 3000, 5000))
        assertEquals(0.25f, LyricsUtils.wordProgress(word, 1250, 5000))
    }

    @Test fun `estimated sweep uses next word timing and handles instantaneous segments`() {
        assertEquals(0.5f, LyricsUtils.wordProgress(LyricsWord(1000, 0, 3), 1500, 2000))
        assertEquals(0f, LyricsUtils.wordProgress(LyricsWord(1000, 0, 3, 1000), 999, 2000))
        assertEquals(1f, LyricsUtils.wordProgress(LyricsWord(1000, 0, 3, 1000), 1000, 2000))
    }

    @Test fun `YRC word markers preserve start and duration timestamps`() {
        val line = LyricsUtils.parseLyrics("[1000,2000](1000,1000,0)Hello (2000,1000,0)world").single()
        assertEquals("Hello world", line.text)
        assertEquals(listOf(1000L, 2000L), line.words.map { it.time })
        assertEquals(listOf(2000L, 3000L), line.words.map { it.endTime })
        assertEquals(listOf("Hello ", "world"), line.words.map { line.text.substring(it.start, it.end) })
    }

    @Test fun `QRC word-after-timestamp format preserves word timing`() {
        val line = LyricsUtils.parseLyrics("[1000,2500]Hello (1000,800,0)world (1800,700,0)").single()
        assertEquals("Hello world", line.text)
        assertEquals(listOf(1000L, 1800L), line.words.map { it.time })
        assertEquals(listOf(1800L, 2500L), line.words.map { it.endTime })
        assertEquals(listOf("Hello ", "world"), line.words.map { line.text.substring(it.start, it.end) })
    }
}
