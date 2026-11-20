/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Compiled with the app's Material 3 version so its slider API cannot drift from ours.
 * Material 3 owns touch, keyboard and accessibility handling; only the track is custom.
 * The wave is stationary, so paused/hidden players never need an animation clock.
 */
@Composable
fun WavyPlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    amplitude: Dp = 4.dp,
) {
    val input = normalizeSliderInput(value, valueRange)
    val isEnabled = enabled && input.hasRange
    val state = rememberPlayerSliderState(input.value, input.range)
    val path = remember { Path() }
    Slider(
        state = state,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        enabled = isEnabled,
        colors = colors,
        track = { sliderState ->
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                val fraction = ((sliderState.value - input.range.start) /
                    (input.range.endInclusive - input.range.start)).coerceIn(0f, 1f)
                val activeWidth = size.width * fraction
                val isRtl = layoutDirection == LayoutDirection.Rtl
                fun x(distance: Float) = if (isRtl) size.width - distance else distance
                val strokeWidth = 3.dp.toPx()
                val waveHeight = amplitude.toPx().coerceIn(0f, (size.height - strokeWidth) / 2f)
                val wavelength = 36.dp.toPx()
                val activeColor = if (isEnabled) colors.activeTrackColor else colors.disabledActiveTrackColor
                val inactiveColor = if (isEnabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor
                drawLine(
                    color = inactiveColor,
                    start = Offset(x(activeWidth), center.y),
                    end = Offset(x(size.width), center.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                if (activeWidth > 0f) {
                    path.reset()
                    path.moveTo(x(0f), center.y)
                    var distance = 0f
                    val step = 2.dp.toPx()
                    while (distance < activeWidth) {
                        distance = (distance + step).coerceAtMost(activeWidth)
                        // Ease the wave to the baseline at both ends, including the thumb.
                        val envelope = (distance / wavelength).coerceAtMost(1f) *
                            ((activeWidth - distance) / wavelength).coerceAtMost(1f)
                        val y = center.y + sin(distance / wavelength * 2f * PI.toFloat()) * waveHeight * envelope
                        path.lineTo(x(distance), y)
                    }
                    drawPath(path, activeColor, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                }
            }
        },
    )
}
