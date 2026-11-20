/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/** Shared tonal hero treatment for albums, artists and local/online playlists. */
@Composable
fun Modifier.expressiveCollectionHeader(): Modifier {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomEnd = 36.dp, bottomStart = 12.dp)
    return this.padding(horizontal = 16.dp, vertical = 12.dp)
        .clip(shape)
        .background(Brush.linearGradient(listOf(colors.secondaryContainer, colors.surfaceContainerLow,
            colors.tertiaryContainer.copy(alpha = 0.45f))))
        .border(1.dp, colors.outlineVariant.copy(alpha = 0.45f), shape)
        .padding(16.dp)
}
