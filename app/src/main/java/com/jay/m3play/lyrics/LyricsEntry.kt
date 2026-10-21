/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

data class LyricsEntry(
    val time: Long,
    val text: String,
    val words: List<LyricsWord> = emptyList(),
    val endTime: Long? = null,
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = time.compareTo(other.time)

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
    }
}

/** A timed text segment, using character offsets in the displayed line. */
data class LyricsWord(
    val time: Long,
    val start: Int,
    val end: Int,
    val endTime: Long? = null,
)
