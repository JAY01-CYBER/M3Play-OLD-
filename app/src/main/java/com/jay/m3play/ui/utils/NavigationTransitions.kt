/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.ui.utils

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import com.jay.m3play.ui.screens.Screens

/** Shared timing for navigation and player sheets. Compose respects system animation scale. */
object AppMotion {
    const val OpenDuration = 380
    const val CloseDuration = 300
    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun sheetEnter(): EnterTransition =
        slideInVertically(tween(OpenDuration, easing = Easing)) { it / 5 } +
            fadeIn(tween(220, easing = Easing)) +
            scaleIn(tween(OpenDuration, easing = Easing), initialScale = 0.98f)

    fun sheetExit(): ExitTransition =
        slideOutVertically(tween(CloseDuration, easing = Easing)) { it / 5 } +
            fadeOut(tween(180, easing = Easing)) +
            scaleOut(tween(CloseDuration, easing = Easing), targetScale = 0.98f)
}

private val mainRoutes = Screens.MainScreens.map { it.route }.toSet()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isPeerChange(): Boolean {
    val from = initialState.destination.route
    val to = targetState.destination.route
    return (from in mainRoutes && to in mainRoutes) ||
        (from?.startsWith("search/") == true && to?.startsWith("search/") == true)
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.screenEnterTransition(
    isBack: Boolean = false,
): EnterTransition {
    if (isPeerChange()) {
        // Fade through: the previous tab leaves before the new tab appears.
        return fadeIn(tween(180, delayMillis = 90, easing = AppMotion.Easing)) +
            scaleIn(tween(300, delayMillis = 60, easing = AppMotion.Easing), initialScale = 0.985f)
    }
    val duration = if (isBack) AppMotion.CloseDuration else AppMotion.OpenDuration
    val direction = if (isBack) AnimatedContentTransitionScope.SlideDirection.End
        else AnimatedContentTransitionScope.SlideDirection.Start
    return slideIntoContainer(direction, tween(duration, easing = AppMotion.Easing)) {
        if (isBack) it / 12 else it / 4
    } + fadeIn(tween(220, delayMillis = if (isBack) 0 else 50, easing = AppMotion.Easing)) +
        scaleIn(tween(duration, easing = AppMotion.Easing), initialScale = if (isBack) 0.985f else 0.965f)
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExitTransition(
    isBack: Boolean = false,
): ExitTransition {
    if (isPeerChange()) return fadeOut(tween(90, easing = AppMotion.Easing))
    val duration = if (isBack) AppMotion.CloseDuration else AppMotion.OpenDuration
    val direction = if (isBack) AnimatedContentTransitionScope.SlideDirection.End
        else AnimatedContentTransitionScope.SlideDirection.Start
    return slideOutOfContainer(direction, tween(duration, easing = AppMotion.Easing)) {
        if (isBack) it / 3 else it / 12
    } + fadeOut(tween(if (isBack) 180 else 140, easing = AppMotion.Easing)) +
        scaleOut(tween(duration, easing = AppMotion.Easing), targetScale = 0.985f)
}
