/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.ui.screens.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.jay.m3play.R
import com.jay.m3play.constants.AnimatedBackgroundKey
import com.jay.m3play.constants.AnimateLyricsKey
import com.jay.m3play.constants.AppleMusicLyricsBlurKey
import com.jay.m3play.constants.FrostedGlassKey
import com.jay.m3play.constants.LyricsScrollKey
import com.jay.m3play.constants.WordByWordLyricsKey
import com.jay.m3play.constants.fullScreenLyricsKey
import com.jay.m3play.ui.component.SettingsGeneralCategory
import com.jay.m3play.ui.component.SettingsPage
import com.jay.m3play.ui.component.SwitchPreference
import com.jay.m3play.constants.EnableBetterLyricsKey
import com.jay.m3play.constants.EnableUnisonLyricsKey
import com.jay.m3play.constants.PreferredLyricsProvider
import com.jay.m3play.constants.PreferredLyricsProviderKey
import com.jay.m3play.ui.component.EnumListPreference
import com.jay.m3play.utils.rememberEnumPreference
import com.jay.m3play.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettings(navController: NavController, scrollBehavior: TopAppBarScrollBehavior) {
    val (fullscreen, setFullscreen) = rememberPreference(fullScreenLyricsKey, true)
    val (words, setWords) = rememberPreference(WordByWordLyricsKey, true)
    val (animated, setAnimated) = rememberPreference(AnimateLyricsKey, true)
    val (blur, setBlur) = rememberPreference(AppleMusicLyricsBlurKey, true)
    val (glass, setGlass) = rememberPreference(FrostedGlassKey, true)
    val (motion, setMotion) = rememberPreference(AnimatedBackgroundKey, true)
    val (scroll, setScroll) = rememberPreference(LyricsScrollKey, true)
    val (better, setBetter) = rememberPreference(EnableBetterLyricsKey, true)
    val (unison, setUnison) = rememberPreference(EnableUnisonLyricsKey, true)
    val (preferred, setPreferred) = rememberEnumPreference(PreferredLyricsProviderKey, PreferredLyricsProvider.UNISON)
    SettingsPage(stringResource(R.string.lyrics), navController, scrollBehavior) {
        SettingsGeneralCategory(title = stringResource(R.string.lyrics_providers), items = listOf(
            { EnumListPreference(title = { Text(stringResource(R.string.preferred_lyrics_provider)) },
                icon = null, selectedValue = preferred, onValueSelected = setPreferred, valueText = {
                    when (it) {
                        PreferredLyricsProvider.UNISON -> "Unison"
                        PreferredLyricsProvider.BETTER_LYRICS -> "Better Lyrics"
                        PreferredLyricsProvider.LRCLIB -> "LRCLIB"
                        PreferredLyricsProvider.KUGOU -> "KuGou"
                    }
                }) },
            { SwitchPreference(title = { Text("Unison") },
                description = stringResource(R.string.unison_lyrics_desc),
                checked = unison, onCheckedChange = setUnison) },
            { SwitchPreference(title = { Text("Better Lyrics") },
                description = stringResource(R.string.better_lyrics_desc),
                checked = better, onCheckedChange = setBetter) },
        ))
        SettingsGeneralCategory(title = stringResource(R.string.lyrics), items = listOf(
            { SwitchPreference(title = { Text(stringResource(R.string.separate_lyrics_screen)) },
                description = stringResource(R.string.separate_lyrics_screen_desc),
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = fullscreen, onCheckedChange = setFullscreen) },
            { SwitchPreference(title = { Text(stringResource(R.string.word_by_word_lyrics)) },
                description = stringResource(R.string.word_by_word_lyrics_desc),
                checked = words, onCheckedChange = setWords, isEnabled = animated) },
            { SwitchPreference(title = { Text(stringResource(R.string.animate_lyrics)) },
                checked = animated, onCheckedChange = setAnimated) },
            { SwitchPreference(title = { Text(stringResource(R.string.lyrics_inactive_blur)) },
                checked = blur, onCheckedChange = setBlur, isEnabled = animated) },
            { SwitchPreference(title = { Text(stringResource(R.string.auto_scroll)) },
                checked = scroll, onCheckedChange = setScroll) },
        ))
        SettingsGeneralCategory(title = stringResource(R.string.player_background_style), items = listOf(
            { SwitchPreference(title = { Text(stringResource(R.string.animated_player_background)) },
                description = stringResource(R.string.animated_player_background_desc),
                checked = motion, onCheckedChange = setMotion) },
            { SwitchPreference(title = { Text(stringResource(R.string.frosted_glass)) },
                description = stringResource(R.string.frosted_glass_desc),
                checked = glass, onCheckedChange = setGlass) },
        ))
    }
}
