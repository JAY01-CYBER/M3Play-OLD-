/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jay.m3play.db.MusicDatabase
import com.jay.m3play.db.entities.LyricsEntity
import com.jay.m3play.lyrics.LyricsHelper
import com.jay.m3play.lyrics.LyricsResult
import com.jay.m3play.models.MediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class LyricsMenuViewModel
@Inject
constructor(
    private val lyricsHelper: LyricsHelper,
    val database: MusicDatabase,
) : ViewModel() {
    private var job: Job? = null
    val results = MutableStateFlow(emptyList<LyricsResult>())
    val isLoading = MutableStateFlow(false)

    fun search(
        mediaId: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) {
        isLoading.value = true
        results.value = emptyList()
        job?.cancel()
        job =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    lyricsHelper.getAllLyrics(mediaId, title, artist, duration, album) { result ->
                        results.update { it + result }
                    }
                } finally {
                    if (currentCoroutineContext().isActive) isLoading.value = false
                }
            }
    }

    fun cancelSearch() {
        job?.cancel()
        job = null
        isLoading.value = false
    }

    fun refetchLyrics(
        mediaMetadata: MediaMetadata,
        lyricsEntity: LyricsEntity?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = lyricsHelper.getLyricsResult(mediaMetadata)
            database.query {
                lyricsEntity?.let(::delete)
                upsert(LyricsEntity(mediaMetadata.id, result.lyrics, result.providerName))
            }
        }
    }
}
