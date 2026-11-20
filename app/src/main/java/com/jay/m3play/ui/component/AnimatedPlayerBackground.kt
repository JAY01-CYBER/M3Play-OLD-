/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.jay.m3play.constants.AnimatedBackgroundKey
import com.jay.m3play.constants.FrostedGlassKey
import com.jay.m3play.utils.rememberPreference
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.sin

/** Soft moving color fields; frame updates stop when paused or off screen. */
@Composable
fun AnimatedPlayerBackground(
    artwork: String?,
    colors: List<Color>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val (motion) = rememberPreference(AnimatedBackgroundKey, true)
    val (frosted) = rememberPreference(FrostedGlassKey, true)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motion, isPlaying, lifecycle) {
        val motionScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (motion && isPlaying && motionScale > 0f) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (isActive) withFrameNanos { now ->
                if (previous != 0L) phase = (phase + (now - previous) / 1_000_000_000f * 0.12f / motionScale) % (2f * Math.PI.toFloat())
                previous = now
            }
        }
    }
    val palette = colors.ifEmpty { listOf(MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary) }
    Box(modifier.fillMaxSize().background(palette.first())) {
        if (frosted && artwork != null) AsyncImage(
            model = artwork, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(48.dp), alpha = 0.35f,
        )
        Canvas(Modifier.fillMaxSize()) {
            palette.take(3).forEachIndexed { index, color ->
                val angle = phase + index * 2.1f
                val center = Offset(size.width * (0.5f + 0.35f * sin(angle)),
                    size.height * (0.5f + 0.3f * cos(angle)))
                drawRect(Brush.radialGradient(listOf(color.copy(alpha = 0.85f), Color.Transparent),
                    center, size.maxDimension * 0.8f))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f),
                Color.Black.copy(alpha = 0.65f))))
        }
    }
}
