/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.playback

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.PowerManager
import com.jay.m3play.MusicWidget
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.Timeline
import com.jay.m3play.MusicWidget.Companion.ACTION_STATE_CHANGED
import com.jay.m3play.MusicWidget.Companion.ACTION_UPDATE_PROGRESS
import com.jay.m3play.db.MusicDatabase
import com.jay.m3play.extensions.currentMetadata
import com.jay.m3play.extensions.getCurrentQueueIndex
import com.jay.m3play.extensions.getQueueWindows
import com.jay.m3play.extensions.metadata
import com.jay.m3play.playback.MusicService.MusicBinder
import com.jay.m3play.playback.queues.Queue
import com.jay.m3play.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    scope: CoroutineScope,
) : Player.Listener {
    private val context: android.app.Application = context.applicationContext as android.app.Application

    companion object {
        private const val TAG = "PlayerConnection"
        private const val PROGRESS_UPDATE_INTERVAL = 1000L
        private const val WIDGET_UPDATE_DEBOUNCE = 100L

        @Volatile
        var instance: PlayerConnection? = null
            private set
    }

    val service = binder.service
    val player = service.player

    // Estados básicos del reproductor
    private val _playbackState = MutableStateFlow(player.playbackState)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _playWhenReady = MutableStateFlow(player.playWhenReady)
    val playWhenReady: StateFlow<Boolean> = _playWhenReady.asStateFlow()

    // Estado combinado de reproducción
    val isPlaying = combine(playbackState, playWhenReady) { playbackState, playWhenReady ->
        playWhenReady && (playbackState == STATE_READY || playbackState == Player.STATE_BUFFERING)
    }.stateIn(
        scope,
        SharingStarted.Lazily,
        player.playWhenReady && player.playbackState == STATE_READY
    )

    // Estados de conexión y salud del reproductor
    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Metadatos y información de la canción actual
    private val _mediaMetadata = MutableStateFlow(player.currentMetadata)
    val mediaMetadata: StateFlow<com.jay.m3play.models.MediaMetadata?> =
        _mediaMetadata.asStateFlow()

    val currentSong = mediaMetadata.flatMapLatest { metadata ->
        database.song(metadata?.id)
    }

    val currentLyrics = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.lyrics(mediaMetadata?.id)
    }

    val currentFormat = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.format(mediaMetadata?.id)
    }

    // Estados de la cola de reproducción
    private val _queueTitle = MutableStateFlow<String?>(service.queueTitle)
    val queueTitle: StateFlow<String?> = _queueTitle.asStateFlow()

    private val _queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val queueWindows: StateFlow<List<Timeline.Window>> = _queueWindows.asStateFlow()

    /** Queue windows without entries lacking metadata, so UI code never hits a null metadata. */
    private fun safeQueueWindows(): List<Timeline.Window> =
        runCatching { player.getQueueWindows().filter { it.mediaItem.metadata != null } }
            .getOrDefault(emptyList())

    private val _currentMediaItemIndex = MutableStateFlow(-1)
    val currentMediaItemIndex: StateFlow<Int> = _currentMediaItemIndex.asStateFlow()

    private val _currentWindowIndex = MutableStateFlow(-1)
    val currentWindowIndex: StateFlow<Int> = _currentWindowIndex.asStateFlow()

    // Estados de control
    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // Estados de navegación
    private val _canSkipPrevious = MutableStateFlow(true)
    val canSkipPrevious: StateFlow<Boolean> = _canSkipPrevious.asStateFlow()

    private val _canSkipNext = MutableStateFlow(true)
    val canSkipNext: StateFlow<Boolean> = _canSkipNext.asStateFlow()

    // Estados de progreso y posición
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    // Estados de audio y volumen
    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Estados de favoritos y valoraciones
    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked.asStateFlow()

    // Estados de error
    private val _error = MutableStateFlow<PlaybackException?>(null)
    val error: StateFlow<PlaybackException?> = _error.asStateFlow()

    // Control de actualizaciones
    private val updateScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]) + Dispatchers.Main.immediate)
    val progress = PlaybackProgressRepository(player, updateScope).progress
    private val progressUpdateHandler = Handler(Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null
    private val isUpdatingProgress = AtomicBoolean(false)
    private val widgetUpdateHandler = Handler(Looper.getMainLooper())
    private var pendingWidgetUpdate: Runnable? = null

    // Estados previos para detectar cambios
    private var lastPlaybackState: Int = player.playbackState
    private var lastPlayWhenReady: Boolean = player.playWhenReady
    private var lastMediaItemIndex: Int = player.currentMediaItemIndex
    private var lastPosition: Long = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            startProgressUpdates()
        }
    }

    init {
        Timber.tag(TAG).d("%s", "Initializing PlayerConnection")

        context.registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        player.addListener(this)
        initializeStates()
        startProgressUpdates()

        instance = this

        Timber.tag(TAG).d("%s", "PlayerConnection initialized successfully")
    }

    override fun onEvents(player: Player, events: Player.Events) {
        handlePlayerEvents(player, events)
        updateConnectionState(player.playbackState)
        updateProgressStates()
        startProgressUpdates()
    }

    private fun initializeStates() {
        try {
            _playbackState.value = player.playbackState
            _playWhenReady.value = player.playWhenReady
            _mediaMetadata.value = player.currentMetadata
            _queueTitle.value = service.queueTitle
            _queueWindows.value = safeQueueWindows()
            _currentWindowIndex.value = player.getCurrentQueueIndex()
            _currentMediaItemIndex.value = player.currentMediaItemIndex
            _shuffleModeEnabled.value = player.shuffleModeEnabled
            _repeatMode.value = player.repeatMode
            _currentPosition.value = player.currentPosition
            _duration.value = player.duration
            _bufferedPosition.value = player.bufferedPosition
            _volume.value = player.volume

            // Inicializar estado de like
            updateScope.launch {
                updateLikeStatusForCurrentSong()
            }

            updateCanSkipPreviousAndNext()
            Timber.tag(TAG).d("%s", "States initialized")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error initializing states")
            reportException(e)
        }
    }


    fun refreshLikeStatus() {
        updateScope.launch {
            updateLikeStatusForCurrentSong()
        }

    }

    private fun handlePlayerEvents(player: Player, events: Player.Events) {
        var shouldUpdateWidget = false

        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            shouldUpdateWidget = true
        }

        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            shouldUpdateWidget = true
        }

        if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
            events.contains(Player.EVENT_TIMELINE_CHANGED)
        ) {
            shouldUpdateWidget = true
        }

        if (shouldUpdateWidget) {
            scheduleWidgetUpdate()
        }
    }

    private fun updateConnectionState(playbackState: Int) {
        val newConnectionState = when (playbackState) {
            Player.STATE_IDLE -> ConnectionState.IDLE
            Player.STATE_BUFFERING -> ConnectionState.BUFFERING
            Player.STATE_READY -> ConnectionState.CONNECTED
            Player.STATE_ENDED -> ConnectionState.ENDED
            else -> ConnectionState.ERROR
        }

        _connectionState.value = newConnectionState
        _isConnected.value = newConnectionState == ConnectionState.CONNECTED ||
                newConnectionState == ConnectionState.BUFFERING
    }

    fun refreshWidgetProgressUpdates() = startProgressUpdates()

    private fun startProgressUpdates() {
        stopProgressUpdates()

        if (!player.isPlaying || !context.getSystemService(PowerManager::class.java).isInteractive ||
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MusicWidget::class.java)).isEmpty()
        ) return

        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (isUpdatingProgress.compareAndSet(false, true)) {
                    try {
                        updateProgressStates()

                        if (player.isPlaying && context.getSystemService(PowerManager::class.java).isInteractive) {
                            progressUpdateHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
                        }
                    } finally {
                        isUpdatingProgress.set(false)
                    }
                }
            }
        }

        progressUpdateRunnable?.let {
            progressUpdateHandler.post(it)
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let { runnable ->
            progressUpdateHandler.removeCallbacks(runnable)
            progressUpdateRunnable = null
        }
    }

    private fun updateProgressStates() {
        try {
            val currentPos = player.currentPosition
            val totalDuration = player.duration
            val buffered = player.bufferedPosition

            // Solo actualizar si hay cambios significativos
            if (kotlin.math.abs(currentPos - lastPosition) > 500L ||
                _duration.value != totalDuration
            ) {

                _currentPosition.value = currentPos
                _duration.value = totalDuration
                _bufferedPosition.value = buffered

                lastPosition = currentPos

                // Enviar broadcast para actualización de progreso
                sendProgressUpdateBroadcast()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error updating progress states")
        }
    }

    private fun scheduleWidgetUpdate() {
        // Cancelar actualización pendiente
        pendingWidgetUpdate?.let { widgetUpdateHandler.removeCallbacks(it) }

        // Programar nueva actualización con debounce
        pendingWidgetUpdate = Runnable {
            sendStateChangedBroadcast()
        }

        widgetUpdateHandler.postDelayed(pendingWidgetUpdate!!, WIDGET_UPDATE_DEBOUNCE)
    }


    fun playQueue(queue: Queue) {
        service.playQueue(queue)
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        try {
            Timber.tag(TAG).d("%s", "Adding ${items.size} items to play next")
            service.playNext(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error adding items to play next")
            reportException(e)
        }
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        try {
            Timber.tag(TAG).d("%s", "Adding ${items.size} items to queue")
            service.addToQueue(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error adding items to queue")
            reportException(e)
        }
    }

    fun toggleLike() {
        try {
            Timber.tag(TAG).d("Toggling like for current track. Current state: ${_isLiked.value}")

            // Llamar al servicio para cambiar el estado en la base de datos
            service.toggleLike()

            // Actualizar estado local
            _isLiked.value = !_isLiked.value

            // Notificar cambios al widget
            scheduleWidgetUpdate()

            Timber.tag(TAG).d("%s", "Like toggled to: ${_isLiked.value}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error toggling like")
            reportException(e)
        }
    }


    fun isCurrentSongLiked(): Boolean {
        return _isLiked.value
    }

    fun seekToNext() {
        try {
            Timber.tag(TAG).d("%s", "Seeking to next track")
            if (player.hasNextMediaItem()) {
                player.seekToNext()
                player.prepare()
                player.playWhenReady = true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error seeking to next")
            reportException(e)
        }
    }

    fun seekToPrevious() {
        try {
            Timber.tag(TAG).d("%s", "Seeking to previous track")
            if (player.hasPreviousMediaItem() || player.currentPosition > 3000) {
                player.seekToPrevious()
                player.prepare()
                player.playWhenReady = true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error seeking to previous")
            reportException(e)
        }
    }

    fun togglePlayPause() {
        try {
            val newPlayWhenReady = !player.playWhenReady
            Timber.tag(TAG).d("%s", "Toggling play/pause to: $newPlayWhenReady")
            player.playWhenReady = newPlayWhenReady
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error toggling play/pause")
            reportException(e)
        }
    }

    fun toggleShuffle() {
        try {
            val newShuffleMode = !player.shuffleModeEnabled
            Timber.tag(TAG).d("%s", "Toggling shuffle to: $newShuffleMode")
            player.shuffleModeEnabled = newShuffleMode
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error toggling shuffle")
            reportException(e)
        }
    }

    fun toggleReplayMode() {
        try {
            val newRepeatMode = if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                REPEAT_MODE_OFF
            } else {
                Player.REPEAT_MODE_ONE
            }
            Timber.tag(TAG).d("%s", "Toggling repeat mode to: $newRepeatMode")
            player.repeatMode = newRepeatMode
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error toggling repeat mode")
            reportException(e)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            Timber.tag(TAG).d("%s", "Seeking to position: ${positionMs}ms")
            player.seekTo(positionMs.coerceIn(0, player.duration.coerceAtLeast(0)))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error seeking to position")
            reportException(e)
        }
    }

    fun setVolume(volume: Float) {
        try {
            val clampedVolume = volume.coerceIn(0.0f, 1.0f)
            Timber.tag(TAG).d("%s", "Setting volume to: $clampedVolume")
            player.volume = clampedVolume
            _volume.value = clampedVolume
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error setting volume")
            reportException(e)
        }
    }

    fun toggleMute() {
        try {
            val newMuteState = !_isMuted.value
            Timber.tag(TAG).d("%s", "Toggling mute to: $newMuteState")

            if (newMuteState) {
                player.volume = 0.0f
            } else {
                player.volume = _volume.value
            }

            _isMuted.value = newMuteState
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error toggling mute")
            reportException(e)
        }
    }

    // Listeners del reproductor sobrescritos
    override fun onPlaybackStateChanged(state: Int) {
        Timber.tag(TAG).d("%s", "Playback state changed: $lastPlaybackState -> $state")
        _playbackState.value = state
        _error.value = player.playerError

        if (lastPlaybackState != state) {
            lastPlaybackState = state
            scheduleWidgetUpdate()
        }
    }

    override fun onPlayWhenReadyChanged(newPlayWhenReady: Boolean, reason: Int) {
        Timber.tag(TAG).d("%s", "PlayWhenReady changed: $lastPlayWhenReady -> $newPlayWhenReady (reason: $reason)")
        _playWhenReady.value = newPlayWhenReady

        if (lastPlayWhenReady != newPlayWhenReady) {
            lastPlayWhenReady = newPlayWhenReady
            scheduleWidgetUpdate()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        Timber.tag(TAG).d("Media item transition: ${mediaItem?.mediaId} (reason: $reason)")
        _mediaMetadata.value = mediaItem?.metadata
        _currentMediaItemIndex.value = player.currentMediaItemIndex
        _currentWindowIndex.value = player.getCurrentQueueIndex()

        // Actualizar estado de like cuando cambia la canción
        updateScope.launch {
            updateLikeStatusForCurrentSong()
        }

        if (lastMediaItemIndex != player.currentMediaItemIndex) {
            lastMediaItemIndex = player.currentMediaItemIndex
            updateCanSkipPreviousAndNext()
            scheduleWidgetUpdate()
        }
    }

    private suspend fun updateLikeStatusForCurrentSong() {
        try {
            val currentSongId = player.currentMediaItem?.mediaId
            if (currentSongId != null) {
                // Consultar la base de datos para obtener el estado actual del like
                // Similar a como lo hace Player.kt con currentSong?.song?.liked
                val songWithInfo = database.song(currentSongId).first()
                _isLiked.value = songWithInfo?.song?.liked ?: false
                Timber.tag(TAG).d("Like status updated for song $currentSongId: ${_isLiked.value}")
            } else {
                _isLiked.value = false
                Timber.tag(TAG).d("No current song, setting like status to false")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating like status for current song")
            _isLiked.value = false
        }
    }
    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        Timber.tag(TAG).d("%s", "Timeline changed (reason: $reason)")
        _queueWindows.value = safeQueueWindows()
        _queueTitle.value = service.queueTitle
        _currentMediaItemIndex.value = player.currentMediaItemIndex
        _currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        Timber.tag(TAG).d("%s", "Shuffle mode changed: $enabled")
        _shuffleModeEnabled.value = enabled
        _queueWindows.value = safeQueueWindows()
        _currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
        scheduleWidgetUpdate()
    }

    override fun onRepeatModeChanged(mode: Int) {
        Timber.tag(TAG).d("%s", "Repeat mode changed: $mode")
        _repeatMode.value = mode
        updateCanSkipPreviousAndNext()
        scheduleWidgetUpdate()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        Timber.tag(TAG).e(playbackError, "%s", "Player error changed")
        if (playbackError != null) {
            reportException(playbackError)
        }
        _error.value = playbackError
        updateConnectionState(Player.STATE_IDLE)
    }

    override fun onVolumeChanged(volume: Float) {
        Timber.tag(TAG).d("%s", "Volume changed: $volume")
        _volume.value = volume
        _isMuted.value = volume == 0.0f
    }

    private fun updateCanSkipPreviousAndNext() {
        try {
            if (!player.currentTimeline.isEmpty) {
                val window = player.currentTimeline.getWindow(
                    player.currentMediaItemIndex,
                    Timeline.Window()
                )

                val canPrevious = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                        !window.isLive ||
                        player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) ||
                        player.currentPosition > 3000

                val canNext = (window.isLive && window.isDynamic) ||
                        player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)

                _canSkipPrevious.value = canPrevious
                _canSkipNext.value = canNext
            } else {
                _canSkipPrevious.value = false
                _canSkipNext.value = false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error updating skip states")
            _canSkipPrevious.value = false
            _canSkipNext.value = false
        }
    }

    private fun sendStateChangedBroadcast() {
        try {
            val intent = Intent(ACTION_STATE_CHANGED).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error sending state changed broadcast")
        }
    }

    private fun sendProgressUpdateBroadcast() {
        try {
            val intent = Intent(ACTION_UPDATE_PROGRESS).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error sending progress update broadcast")
        }
    }

    fun dispose() {
        Timber.tag(TAG).d("%s", "Disposing PlayerConnection")

        stopProgressUpdates()
        updateScope.cancel()
        context.unregisterReceiver(screenReceiver)

        // Cancelar actualizaciones pendientes
        pendingWidgetUpdate?.let { widgetUpdateHandler.removeCallbacks(it) }

        try {
            player.removeListener(this)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "%s", "Error removing player listener")
        }

        instance = null

        Timber.tag(TAG).d("%s", "PlayerConnection disposed")
    }

    // Estados de conexión
    enum class ConnectionState {
        IDLE,
        CONNECTING,
        CONNECTED,
        BUFFERING,
        ENDED,
        ERROR
    }
}