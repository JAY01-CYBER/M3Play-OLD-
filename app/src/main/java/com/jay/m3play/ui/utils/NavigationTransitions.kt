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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import com.jay.m3play.ui.screens.Screens

/** Native push/pop depth with a steady parallax surface; no competing zoom animations. */
object AppMotion {
    const val OpenDuration = 420
    const val CloseDuration = 340
    val Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    fun sheetEnter(): EnterTransition =
        slideInVertically(spring(dampingRatio = 0.95f, stiffness = 320f)) { it / 8 } +
            fadeIn(tween(180))

    fun sheetExit(): ExitTransition =
        slideOutVertically(tween(240, easing = Easing)) { it / 8 } + fadeOut(tween(200))
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
    if (isPeerChange()) return fadeIn(tween(220, delayMillis = 60))
    val duration = if (isBack) AppMotion.CloseDuration else AppMotion.OpenDuration
    val direction = if (isBack) AnimatedContentTransitionScope.SlideDirection.End
        else AnimatedContentTransitionScope.SlideDirection.Start
    return slideIntoContainer(direction, tween(duration, easing = AppMotion.Easing)) {
        if (isBack) it / 4 else it
    } + fadeIn(tween(duration), initialAlpha = if (isBack) 0.65f else 1f)
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExitTransition(
    isBack: Boolean = false,
): ExitTransition {
    if (isPeerChange()) return fadeOut(tween(160))
    val duration = if (isBack) AppMotion.CloseDuration else AppMotion.OpenDuration
    val direction = if (isBack) AnimatedContentTransitionScope.SlideDirection.End
        else AnimatedContentTransitionScope.SlideDirection.Start
    return slideOutOfContainer(direction, tween(duration, easing = AppMotion.Easing)) {
        if (isBack) it else it / 4
    } + fadeOut(tween(duration), targetAlpha = if (isBack) 1f else 0.65f)
}
