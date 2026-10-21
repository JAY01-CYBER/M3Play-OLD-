/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

import com.jay.m3play.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

internal data class LyricsLookup(val name: String, val fetch: suspend () -> Result<String>)

internal fun validLyrics(lyrics: String) = lyrics.isNotBlank() && lyrics.trim() != LYRICS_NOT_FOUND

/** Bound each provider independently; user cancellation always stops the lookup. */
internal suspend fun <T> boundedLyricsCall(
    timeoutMillis: Long = 15_000,
    onFailure: (Throwable) -> Unit = {},
    block: suspend () -> T,
): T? = try {
    withTimeoutOrNull(timeoutMillis) {
        block().also { currentCoroutineContext().ensureActive() }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    onFailure(error)
    null
}

/** Keep plain lyrics as a fallback while checking remaining sources for synced lyrics. */
internal suspend fun selectLyrics(
    lookups: List<LyricsLookup>,
    timeoutMillis: Long = 15_000,
    onFailure: (Throwable) -> Unit = {},
): LyricsResult? {
    var plain: LyricsResult? = null
    for (lookup in lookups) {
        val result = boundedLyricsCall(timeoutMillis, onFailure) { lookup.fetch().getOrThrow() } ?: continue
        if (!validLyrics(result)) continue
        val candidate = LyricsResult(lookup.name, result)
        if (LyricsUtils.parseLyrics(result).isNotEmpty()) return candidate
        if (plain == null) plain = candidate
    }
    return plain
}
