/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jay.m3play.constants.AnimateLyricsKey
import com.jay.m3play.constants.AppleMusicLyricsBlurKey
import com.jay.m3play.constants.WordByWordLyricsKey
import com.jay.m3play.lyrics.LyricsEntry
import com.jay.m3play.lyrics.LyricsUtils
import com.jay.m3play.ui.screens.settings.LyricsPosition
import com.jay.m3play.utils.rememberPreference

@Composable
fun LyricsLine(
    entry: LyricsEntry,
    isSynced: Boolean,
    isActive: Boolean,
    distanceFromCurrent: Int,
    lyricsTextPosition: LyricsPosition,
    textColor: Color,
    textSize: Float,
    lineSpacing: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    isAutoScrollActive: Boolean,
    position: Long,
    endTime: Long,
    modifier: Modifier = Modifier,
) {
    val (blurEnabled) = rememberPreference(AppleMusicLyricsBlurKey, true)
    val (animate) = rememberPreference(AnimateLyricsKey, true)
    val (wordAnimation) = rememberPreference(WordByWordLyricsKey, true)
    val animationDuration = if (animate) 350 else 0
    val blur by animateFloatAsState(
        if (blurEnabled && animate && isAutoScrollActive && !isActive && isSynced && !isSelectionModeActive) 4f else 0f,
        tween(animationDuration), label = "lyricBlur",
    )
    val scale by animateFloatAsState(
        if (animate && isActive) 1.03f else 1f,
        tween(animationDuration), label = "lyricScale",
    )
    val alpha by animateFloatAsState(
        when {
            !isSynced || isActive || isSelectionModeActive -> 1f
            distanceFromCurrent <= 1 -> 0.65f
            else -> 0.4f
        }, tween(animationDuration), label = "lyricAlpha",
    )
    val words = remember(entry, endTime) { LyricsUtils.wordsFor(entry, endTime) }
    val sweepEnabled = isActive && isSynced && animate && wordAnimation && !isSelectionModeActive
    var layout by remember(entry.text) { mutableStateOf<TextLayoutResult?>(null) }
    // Cache layout geometry; only the clip boundary changes on playback frames.
    val wordBounds = remember(layout, words) {
        val measured = layout
        if (measured == null) emptyList() else words.map { word ->
            val range = word.start.coerceAtLeast(0) until word.end.coerceAtMost(entry.text.length)
            range.groupBy { measured.getLineForOffset(it) }.values.map { offsets ->
                val boxes = offsets.map { measured.getBoundingBox(it) }
                Rect(boxes.minOf { it.left }, boxes.minOf { it.top },
                    boxes.maxOf { it.right }, boxes.maxOf { it.bottom })
            }
        }
    }
    Text(
        text = entry.text,
        color = if (sweepEnabled) textColor.copy(alpha = 0.35f) else textColor,
        onTextLayout = { layout = it },
        fontSize = textSize.sp,
        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
        textAlign = when (lyricsTextPosition) {
            LyricsPosition.LEFT -> TextAlign.Left
            LyricsPosition.CENTER -> TextAlign.Center
            LyricsPosition.RIGHT -> TextAlign.Right
        },
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (isSelected && isSelectionModeActive)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent)
            .padding(horizontal = 24.dp, vertical = lineSpacing.dp)
            .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
            .blur(blur.dp)
            .drawWithContent {
                drawContent()
                val measured = layout
                if (sweepEnabled && measured != null) {
                    words.forEachIndexed { index, word ->
                        val fallbackEnd = words.getOrNull(index + 1)?.time ?: endTime
                        val progress = LyricsUtils.wordProgress(word, position, fallbackEnd)
                        val bounds = wordBounds.getOrNull(index).orEmpty()
                        val width = bounds.sumOf { it.width.toDouble() }.toFloat()
                        var remaining = width * progress
                        val rtl = word.start in entry.text.indices &&
                            measured.getBidiRunDirection(word.start) == ResolvedTextDirection.Rtl
                        bounds.forEach { rect ->
                            val filled = remaining.coerceIn(0f, rect.width.coerceAtLeast(0f))
                            if (filled > 0f) {
                                clipRect(
                                    left = if (rtl) rect.right - filled else rect.left,
                                    top = rect.top,
                                    right = if (rtl) rect.right else rect.left + filled,
                                    bottom = rect.bottom,
                                ) {
                                    drawText(measured, color = textColor,
                                        shadow = if (progress < 1f)
                                            Shadow(textColor.copy(alpha = 0.45f), blurRadius = 10f)
                                        else Shadow.None)
                                }
                            }
                            remaining -= rect.width
                        }
                    }
                }
            },
    )
}
