/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.playback

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One consistent sample shared by the player, lyrics and other visible controls. */
data class PlaybackProgress(
    val position: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
)

/**
 * Reads Media3 only on its application thread (Main in MusicService).
 * No subscribers means no listener or timer. Paused playback is updated by events,
 * so seeking still updates immediately without a permanent polling loop.
 */
internal interface PlaybackProgressSource {
    val isPlaying: Boolean
    val isLoading: Boolean
    fun snapshot(): PlaybackProgress
    fun subscribe(onChange: () -> Unit): () -> Unit
}

private class Media3ProgressSource(private val player: Player) : PlaybackProgressSource {
    override val isPlaying get() = player.isPlaying
    override val isLoading get() = player.isLoading
    override fun snapshot() = PlaybackProgress(player.currentPosition, player.duration, player.bufferedPosition)
    override fun subscribe(onChange: () -> Unit): () -> Unit {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = onChange()
        }
        player.addListener(listener)
        return { player.removeListener(listener) }
    }
}

class PlaybackProgressRepository internal constructor(source: PlaybackProgressSource, scope: CoroutineScope) {
    constructor(player: Player, scope: CoroutineScope) : this(Media3ProgressSource(player), scope)
    val progress = callbackFlow {
        fun publish() {
            val sample = source.snapshot()
            trySend(sample.copy(
                position = sample.position.coerceAtLeast(0),
                duration = sample.duration.coerceAtLeast(0),
                bufferedPosition = sample.bufferedPosition.coerceAtLeast(0),
            ))
        }

        var ticker: Job? = null
        fun update() {
            publish()
            if (source.isPlaying || source.isLoading) {
                if (ticker?.isActive != true) {
                    ticker = launch {
                        while (isActive) {
                            // Lyrics need finer timing than the widget's one-second display.
                            delay(if (source.isPlaying) 100L else 1_000L)
                            publish()
                        }
                    }
                }
            } else {
                ticker?.cancel()
                ticker = null
            }
        }

        val unsubscribe = source.subscribe(::update)
        update()
        awaitClose {
            ticker?.cancel()
            unsubscribe()
        }
    }.conflate()
        .flowOn(Dispatchers.Main.immediate)
        .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), PlaybackProgress())
}
