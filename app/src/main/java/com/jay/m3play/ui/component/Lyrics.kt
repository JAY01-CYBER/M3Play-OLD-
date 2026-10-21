/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.ui.component

import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.jay.m3play.ui.screens.settings.LyricsPosition
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.jay.m3play.LocalDatabase
import com.jay.m3play.LocalPlayerConnection
import com.jay.m3play.R
import com.jay.m3play.constants.*
import com.jay.m3play.db.entities.LyricsEntity
import com.jay.m3play.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.jay.m3play.di.LyricsHelperEntryPoint
import com.jay.m3play.lyrics.AccompanistLyrics
import com.jay.m3play.lyrics.millis
import com.jay.m3play.lyrics.text
import com.jay.m3play.lyrics.translation
import com.jay.m3play.models.MediaMetadata
import com.jay.m3play.ui.menu.LyricsMenu
import com.jay.m3play.ui.theme.PlayerColorExtractor
import com.jay.m3play.ui.utils.AppMotion
import com.jay.m3play.utils.makeTimeString
import com.jay.m3play.utils.rememberEnumPreference
import com.jay.m3play.utils.rememberPreference
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.ui.composable.list.rememberLyricsLazyListState
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLineText
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

/** Accompanist supplies the vocal model, rendering, follow spring, glow and syllable reveal. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    mediaMetadata: MediaMetadata? = null,
    onBackClick: () -> Unit = {},
    backgroundAlpha: () -> Float = { 1f },
) {
    val connection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val selectionLimitMessage = stringResource(R.string.max_selection_limit, 5)
    val menu = LocalMenuState.current
    val playingMetadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val metadata = mediaMetadata ?: playingMetadata
    val songId = metadata?.id
    val storedLyrics by connection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val entity = storedLyrics?.takeIf { it.id == songId }
    val progress by connection.progress.collectAsStateWithLifecycle()
    val playing by connection.isPlaying.collectAsStateWithLifecycle()
    val canPrevious by connection.canSkipPrevious.collectAsStateWithLifecycle()
    val canNext by connection.canSkipNext.collectAsStateWithLifecycle()
    val song by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val animated by rememberPreference(AnimateLyricsKey, true)
    val words by rememberPreference(WordByWordLyricsKey, true)
    val blur by rememberPreference(AppleMusicLyricsBlurKey, true)
    val follow by rememberPreference(LyricsScrollKey, true)
    val clickToSeek by rememberPreference(LyricsClickKey, true)
    val glass by rememberPreference(FrostedGlassKey, true)
    val background by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    val foreground = if (background == PlayerBackgroundStyle.DEFAULT) MaterialTheme.colorScheme.onBackground else Color.White
    var loading by remember(songId) { mutableStateOf(true) }
    var slider by remember(songId) { mutableStateOf<Long?>(null) }
    var sharedText by remember(songId) { mutableStateOf<String?>(null) }
    val selected = remember(songId, entity?.lyrics) { mutableStateListOf<Int>() }
    val selecting = selected.isNotEmpty()

    // Opening lyrics also fetches when the global ShowLyrics preference is off.
    LaunchedEffect(songId) {
        val track = metadata ?: return@LaunchedEffect
        loading = true
        try {
            withContext(Dispatchers.IO) {
                if (database.getLyrics(track.id) == null) {
                    val helper = EntryPointAccessors.fromApplication(context.applicationContext,
                        LyricsHelperEntryPoint::class.java).lyricsHelper()
                    val result = helper.getLyricsResult(track)
                    database.query { upsert(LyricsEntity(track.id, result.lyrics, result.providerName)) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The empty state keeps provider search/edit available for a retry.
        } finally {
            loading = false
        }
    }
    val document by produceState<SyncedLyrics?>(null, songId, entity?.lyrics, progress.duration) {
        value = null
        val raw = entity?.lyrics
        value = if (raw == null || raw == LYRICS_NOT_FOUND) SyncedLyrics(emptyList())
            else withContext(Dispatchers.Default) { AccompanistLyrics.parse(raw, progress.duration) }
    }
    val model = remember(document, words) {
        document?.let { if (words) AccompanistLyrics.withEstimatedTiming(it) else AccompanistLyrics.withoutWordTiming(it) }
    }
    val plainLines = remember(entity?.lyrics) {
        entity?.lyrics?.takeIf { it != LYRICS_NOT_FOUND && !it.trimStart().startsWith('<') }
            ?.lines()?.filter { it.isNotBlank() }.orEmpty()
    }
    val selectionLines = remember(document, plainLines) {
        document?.lines.orEmpty().flatMap { line -> listOf(line) +
            (line as? KaraokeLine.MainKaraokeLine)?.accompanimentLines.orEmpty() }.sortedBy { it.start }
            .ifEmpty { plainLines.map { SyncedLine(it, null, 0, 0) } }
    }
    val isTimed = document?.lines?.isNotEmpty() == true
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var framePosition by remember(songId) { mutableStateOf<Long?>(null) }
    LaunchedEffect(songId, playing, animated, follow, selecting, isTimed, lifecycle) {
        framePosition = null
        if (playing && animated && !selecting && isTimed) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                // Match Dev's frame-clock playback smoothing so lyric progress doesn't stutter
                // when the player's reported position advances in uneven millisecond steps.
                var smoothedPosition = connection.player.currentPosition.coerceAtLeast(0L).toDouble()
                var previousFrameNanos = 0L
                while (isActive) {
                    val seekPosition = slider ?: sliderPositionProvider()
                    val rawPosition = (seekPosition ?: connection.player.currentPosition).coerceAtLeast(0L)
                    if (seekPosition != null || !connection.player.isPlaying) {
                        smoothedPosition = rawPosition.toDouble()
                        previousFrameNanos = 0L
                        framePosition = rawPosition
                        if (seekPosition == null) delay(80L) else withFrameNanos { }
                        continue
                    }
                    val frameNanos = withFrameNanos { it }
                    if (previousFrameNanos == 0L) {
                        previousFrameNanos = frameNanos
                        smoothedPosition = rawPosition.toDouble()
                    } else {
                        val elapsedMs = (frameNanos - previousFrameNanos).coerceAtLeast(0L) / 1_000_000.0
                        previousFrameNanos = frameNanos
                        smoothedPosition += elapsedMs * connection.player.playbackParameters.speed.toDouble()
                        val drift = rawPosition - smoothedPosition
                        if (kotlin.math.abs(drift) > 250.0) {
                            smoothedPosition = rawPosition.toDouble()
                        } else {
                            smoothedPosition += (drift * 0.08).coerceIn(-2.0, 2.0)
                        }
                        framePosition = smoothedPosition.roundToLong().coerceAtLeast(0L)
                    }
                }
            } finally { framePosition = null }
        }
    }
    BackHandler(selecting || onNavigateBack != null) {
        if (selecting) selected.clear() else onNavigateBack?.invoke()
    }
    val fallback = MaterialTheme.colorScheme.surface.toArgb()
    val fallbackPalette = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary)
    val colors by produceState(fallbackPalette, metadata?.thumbnailUrl, background, fallback, resources) {
        value = fallbackPalette
        if (background != PlayerBackgroundStyle.DEFAULT) {
            value = withContext(Dispatchers.IO) {
                try {
                    val drawable = context.imageLoader.execute(ImageRequest.Builder(context)
                        .data(metadata?.thumbnailUrl).size(128).allowHardware(false).build())
                        .image?.asDrawable(resources) as? BitmapDrawable
                    drawable?.bitmap?.let { PlayerColorExtractor.extractGradientColors(
                        Palette.from(it).maximumColorCount(8).generate(), fallback) } ?: fallbackPalette
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { fallbackPalette }
            }
        }
    }

    fun selectLine(line: ISyncedLine) {
        val index = selectionLines.indexOfFirst { it === line || (it.start == line.start && it.text() == line.text()) }
        if (index < 0) return
        if (index in selected) selected.remove(index)
        else if (selected.size < 5) selected.add(index)
        else Toast.makeText(context, selectionLimitMessage, Toast.LENGTH_SHORT).show()
    }
    fun openMenu() {
        if (metadata != null) menu.show {
            LyricsMenu(lyricsProvider = { entity }, mediaMetadataProvider = { metadata }, onDismiss = menu::dismiss)
        }
    }

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = backgroundAlpha() }) {
            when (background) {
                PlayerBackgroundStyle.APPLE_MUSIC -> AnimatedPlayerBackground(metadata?.thumbnailUrl, colors, playing)
                PlayerBackgroundStyle.BLUR -> {
                    AsyncImage(metadata?.thumbnailUrl, null, Modifier.fillMaxSize().blur(48.dp), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                }
                PlayerBackgroundStyle.GRADIENT -> Box(Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(colors)).background(Color.Black.copy(alpha = 0.3f)))
                PlayerBackgroundStyle.DEFAULT -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (selecting) selected.clear() else (onNavigateBack ?: onBackClick)()
                }) { Icon(painterResource(if (selecting) R.drawable.close else R.drawable.arrow_back),
                    stringResource(if (selecting) R.string.cancel else R.string.back), tint = foreground) }
                Text(stringResource(R.string.lyrics), style = MaterialTheme.typography.titleLarge,
                    color = foreground, modifier = Modifier.weight(1f))
                IconButton(onClick = ::openMenu, enabled = metadata != null) {
                    Icon(painterResource(R.drawable.more_horiz), stringResource(R.string.more), tint = foreground)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (glass) MaterialTheme.colorScheme.surface.copy(alpha = 0.13f) else Color.Transparent)
                .then(if (glass) Modifier.border(1.dp, foreground.copy(alpha = 0.1f), RoundedCornerShape(28.dp)) else Modifier)) {
                when {
                    loading || (entity != null && document == null) -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        ContainedLoadingIndicator()
                    }
                    (selecting || !animated) && (selectionLines.isNotEmpty() || plainLines.isNotEmpty()) -> {
                        StaticLyrics(selectionLines, plainLines, selected, foreground,
                            progress.position, follow && !selecting,
                            onClick = { line -> if (selecting) selectLine(line) else if (isTimed && clickToSeek) connection.player.seekTo(line.start.toLong()) },
                            onLongClick = ::selectLine)
                    }
                    !follow && model != null && model.lines.isNotEmpty() -> ManualKaraokeLyrics(
                        model, foreground,
                        position = { (slider ?: sliderPositionProvider() ?: framePosition ?: progress.position).millis() },
                        onClick = { if (clickToSeek) connection.player.seekTo(it.start.toLong()) },
                        onLongClick = ::selectLine,
                    )
                    model != null && model.lines.isNotEmpty() -> key(songId) {
                        val list = rememberLyricsLazyListState()
                        KaraokeLyricsView(
                            listState = list,
                            lyrics = model,
                            currentPosition = { (slider ?: sliderPositionProvider() ?: framePosition ?: progress.position).millis() },
                            onLineClicked = { line ->
                                if (clickToSeek) {
                                    connection.player.seekTo(line.start.toLong())
                                    list.resumeAutoScroll(line.start)
                                }
                            },
                            onLinePressed = ::selectLine,
                            modifier = Modifier.fillMaxSize(),
                            normalLineTextStyle = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = if (onNavigateBack != null) 34.sp else 27.sp,
                                fontWeight = FontWeight.Bold,
                                textMotion = androidx.compose.ui.text.style.TextMotion.Animated,
                            ),
                            accompanimentLineTextStyle = MaterialTheme.typography.titleLarge.copy(
                                fontSize = (if (onNavigateBack != null) 34f else 27f).times(0.82f).sp,
                                fontWeight = FontWeight.Bold,
                                textMotion = androidx.compose.ui.text.style.TextMotion.Animated,
                            ),
                            phoneticTextStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (if (onNavigateBack != null) 34f else 27f).times(0.55f).sp,
                            ),
                            textColor = foreground,
                            blendMode = BlendMode.SrcOver,
                            useBlurEffect = blur,
                            showTranslation = true,
                            showPhonetic = true,
                            itemSpacing = 16.dp,
                            scrollAnimationSpec = tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            autoScrollResumeDelayMillis = 2500L,
                        )
                        if (list.isManualScrolling) FilledTonalButton(
                            onClick = { list.resumeAutoScroll() },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        ) { Text(stringResource(R.string.auto_scroll)) }
                    }
                    plainLines.isNotEmpty() -> StaticLyrics(selectionLines, plainLines, selected, foreground,
                        progress.position, false, {}, ::selectLine)
                    else -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                        TextButton(onClick = ::openMenu) { Text(stringResource(R.string.lyrics_not_found), color = foreground) }
                    }
                }
            }
            if (selecting) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { selected.clear() }) { Text(stringResource(R.string.cancel), color = foreground) }
                FilledTonalButton(onClick = { sharedText = selected.sorted().mapNotNull { selectionLines.getOrNull(it)?.text() }.joinToString("\n") }) {
                    Text(stringResource(R.string.share_selected))
                }
            }
            entity?.provider?.takeIf { it.isNotBlank() && it != "Unknown" }?.let { provider ->
                Text(if (provider == "Unison") "Lyrics from Unison (https://unison.boidu.dev)" else provider,
                    style = MaterialTheme.typography.labelSmall, color = foreground.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(metadata?.thumbnailUrl, null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(metadata?.title.orEmpty(), color = foreground, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(metadata?.artists?.joinToString { it.name }.orEmpty(), color = foreground.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { connection.toggleLike() }) {
                    Icon(painterResource(if (song?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border),
                        stringResource(R.string.action_like), tint = foreground)
                }
            }
            val length = progress.duration.coerceAtLeast(0)
            Column(Modifier.padding(horizontal = 24.dp)) {
                val value = (slider ?: progress.position).coerceIn(0, length).toFloat()
                val colors = SliderDefaults.colors(thumbColor = foreground, activeTrackColor = foreground,
                    inactiveTrackColor = foreground.copy(alpha = 0.2f))
                val seekDone = { slider?.let { connection.player.seekTo(it) }; slider = null }
                if (sliderStyle == SliderStyle.SQUIGGLY) WavyPlayerSlider(value, { slider = it.toLong() },
                    enabled = length > 0, valueRange = 0f..length.coerceAtLeast(1).toFloat(),
                    onValueChangeFinished = seekDone, colors = colors)
                else if (sliderStyle == SliderStyle.SLIM) Slider(
                    state = rememberPlayerSliderState(value, 0f..length.coerceAtLeast(1).toFloat()),
                    onValueChange = { slider = it.toLong() }, enabled = length > 0,
                    onValueChangeFinished = seekDone, colors = colors, thumb = { Spacer(Modifier.size(0.dp)) },
                ) else Slider(state = rememberPlayerSliderState(value, 0f..length.coerceAtLeast(1).toFloat()),
                    onValueChange = { slider = it.toLong() }, enabled = length > 0,
                    onValueChangeFinished = seekDone, colors = colors,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(makeTimeString(slider ?: progress.position), style = MaterialTheme.typography.labelSmall, color = foreground)
                    Text(makeTimeString(length), style = MaterialTheme.typography.labelSmall, color = foreground.copy(alpha = 0.6f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { connection.player.seekToPreviousMediaItem() }, enabled = canPrevious, modifier = Modifier.size(64.dp)) {
                    Icon(painterResource(R.drawable.skip_previous), stringResource(R.string.previous_action), tint = foreground.copy(alpha = if (canPrevious) 1f else 0.3f))
                }
                FilledTonalIconButton(onClick = { if (playing) connection.player.pause() else connection.player.play() }, modifier = Modifier.size(64.dp)) {
                    Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play),
                        stringResource(if (playing) R.string.pause_action else R.string.play), modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { connection.player.seekToNextMediaItem() }, enabled = canNext, modifier = Modifier.size(64.dp)) {
                    Icon(painterResource(R.drawable.skip_next), stringResource(R.string.next_action), tint = foreground.copy(alpha = if (canNext) 1f else 0.3f))
                }
            }
        }
    }
    sharedText?.let { text -> ShareLyricsDialog(text, metadata?.title.orEmpty(),
        metadata?.artists?.joinToString { it.name }.orEmpty(), metadata, onDismiss = { sharedText = null; selected.clear() }) }
}

/** Accessible, still presentation for reduced motion, manual scrolling and share selection. */
@Composable
private fun StaticLyrics(
    lines: List<ISyncedLine>, plain: List<String>, selected: List<Int>, color: Color,
    position: Long, follow: Boolean, onClick: (ISyncedLine) -> Unit, onLongClick: (ISyncedLine) -> Unit,
) {
    val list = rememberLazyListState()
    val textPosition by rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.CENTER)
    val alignment = when (textPosition) {
        LyricsPosition.LEFT -> TextAlign.Start
        LyricsPosition.CENTER -> TextAlign.Center
        LyricsPosition.RIGHT -> TextAlign.End
    }
    val active = lines.indexOfFirst { position >= it.start && position < it.end }
    LaunchedEffect(active, follow) { if (follow && active >= 0) list.scrollToItem(active) }
    LazyColumn(state = list, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxSize()) {
        if (lines.isEmpty()) itemsIndexed(plain) { _, text ->
            Text(text, style = MaterialTheme.typography.headlineSmall, color = color, textAlign = alignment, modifier = Modifier.fillMaxWidth())
        } else itemsIndexed(lines) { index, line ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (index in selected) color.copy(alpha = 0.18f) else Color.Transparent)
                .combinedClickable(onClick = { onClick(line) }, onLongClick = { onLongClick(line) }).padding(8.dp)) {
                Text(line.text(), color = color.copy(alpha = if (index == active || selected.isNotEmpty()) 1f else 0.65f),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    textAlign = alignment, modifier = Modifier.fillMaxWidth())
                line.translation()?.let { Text(it, color = color.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge,
                    textAlign = alignment, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** Browsing stays animated when automatic follow is switched off. */
@Composable
private fun ManualKaraokeLyrics(
    lyrics: SyncedLyrics, color: Color, position: () -> Int,
    onClick: (ISyncedLine) -> Unit, onLongClick: (ISyncedLine) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lyrics.lines) { _, line ->
            Column(Modifier.fillMaxWidth().combinedClickable(onClick = { onClick(line) }, onLongClick = { onLongClick(line) })) {
                if (line is KaraokeLine) KaraokeLineText(line, position,
                    normalLineTextStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    accompanimentLineTextStyle = MaterialTheme.typography.titleLarge,
                    phoneticTextStyle = MaterialTheme.typography.bodyMedium,
                    activeColor = color)
                else Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(line.text(), color = color, style = MaterialTheme.typography.headlineMedium)
                    line.translation()?.let { Text(it, color = color.copy(alpha = 0.6f)) }
                }
            }
        }
    }
}
