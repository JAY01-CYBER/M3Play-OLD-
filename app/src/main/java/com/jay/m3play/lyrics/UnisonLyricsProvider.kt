/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

import android.content.Context
import com.jay.m3play.constants.EnableUnisonLyricsKey
import com.jay.m3play.utils.dataStore
import com.jay.m3play.utils.get

object UnisonLyricsProvider : LyricsProvider {
    override val name = "Unison"
    private val client = RemoteLyricsClient()
    override fun isEnabled(context: Context) = context.dataStore[EnableUnisonLyricsKey] ?: true

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int, album: String?): Result<String> =
        lyricsResult { client.unisonLyrics(LyricsQuery(id, title, artist, duration, album)) }
}
