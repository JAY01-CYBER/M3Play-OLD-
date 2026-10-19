/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jay.m3play.ui.component.Lyrics

/** A full screen surface above the player and navigation, with shared playback controls. */
@Composable
fun LyricsScreen(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Lyrics(sliderPositionProvider = { null }, onNavigateBack = onDismiss)
    }
}
