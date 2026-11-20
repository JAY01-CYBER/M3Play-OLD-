/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LyricsLookupTest {
    @Test fun `isolates failures blank results and prefers synced lyrics over plain`() = runTest {
        val result = selectLyrics(listOf(
            LyricsLookup("broken") { error("offline") },
            LyricsLookup("empty") { Result.success(" ") },
            LyricsLookup("plain") { Result.success("Hello") },
            LyricsLookup("synced") { Result.success("[00:01]Hello") },
            LyricsLookup("unused") { error("must not run") },
        ))
        assertEquals("synced", result?.providerName)
    }

    @Test fun `times out one provider and keeps first plain fallback`() = runTest {
        val result = selectLyrics(listOf(
            LyricsLookup("slow") { delay(1000); Result.success("[00:01]Late") },
            LyricsLookup("first") { Result.success("Hello") },
            LyricsLookup("second") { Result.success("World") },
        ), timeoutMillis = 100)
        assertEquals("first", result?.providerName)
    }

    @Test fun `cancellation is propagated without trying another provider`() = runTest {
        try {
            selectLyrics(listOf(
                LyricsLookup("cancel") { throw CancellationException("track changed") },
                LyricsLookup("unused") { fail("continued after cancellation"); Result.success("bad") },
            ))
            fail("cancellation swallowed")
        } catch (_: CancellationException) { }
    }
}
