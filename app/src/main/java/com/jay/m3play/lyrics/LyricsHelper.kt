/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

import android.content.Context
import com.jay.m3play.constants.PreferredLyricsProvider
import com.jay.m3play.constants.PreferredLyricsProviderKey
import com.jay.m3play.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.jay.m3play.extensions.toEnum
import com.jay.m3play.models.MediaMetadata
import com.jay.m3play.utils.dataStore
import com.jay.m3play.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

class LyricsHelper @Inject constructor(@ApplicationContext private val context: Context) {
    private suspend fun providers(): List<LyricsProvider> {
        val preferred = context.dataStore.data.first()[PreferredLyricsProviderKey]
            .toEnum(PreferredLyricsProvider.UNISON)
        val first = when (preferred) {
            PreferredLyricsProvider.UNISON -> UnisonLyricsProvider
            PreferredLyricsProvider.BETTER_LYRICS -> BetterLyricsProvider
            PreferredLyricsProvider.LRCLIB -> LrcLibLyricsProvider
            PreferredLyricsProvider.KUGOU -> KuGouLyricsProvider
        }
        return (listOf(first) + listOf(UnisonLyricsProvider, BetterLyricsProvider,
            LrcLibLyricsProvider, KuGouLyricsProvider, YouTubeLyricsProvider,
            YouTubeSubtitleLyricsProvider)).distinct().filter { it.isEnabled(context) }
    }

    suspend fun getLyrics(mediaMetadata: MediaMetadata): String = getLyricsResult(mediaMetadata).lyrics

    suspend fun getLyricsResult(mediaMetadata: MediaMetadata): LyricsResult =
        selectLyrics(providers().map { provider ->
            LyricsLookup(provider.name) {
                provider.getLyrics(mediaMetadata.id, mediaMetadata.title,
                    mediaMetadata.artists.joinToString { it.name }, mediaMetadata.duration,
                    mediaMetadata.album?.title)
            }
        }, onFailure = ::reportException) ?: LyricsResult("Unknown", LYRICS_NOT_FOUND)

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        providers().forEach { provider ->
            val seen = mutableSetOf<String>()
            boundedLyricsCall(onFailure = ::reportException) {
                val lookupContext = currentCoroutineContext()
                provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                    lookupContext.ensureActive()
                    if (validLyrics(lyrics) && seen.add(lyrics)) callback(LyricsResult(provider.name, lyrics))
                }
            }
        }
    }
}

data class LyricsResult(val providerName: String, val lyrics: String) {
    val attribution: String
        get() = if (providerName == "Unison") "Lyrics from Unison (https://unison.boidu.dev)" else providerName
}
