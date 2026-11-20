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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
    if (isPeerChange()) return fadeIn(tween(220, delayMillis = 40))

    // A restrained return motion: short glide, soft fade and subtle settling scale.
    if (isBack) {
        return slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.End,
            tween(AppMotion.CloseDuration, easing = AppMotion.Easing),
        ) { it / 7 } +
            fadeIn(tween(AppMotion.CloseDuration, easing = AppMotion.Easing), initialAlpha = 0.82f) +
            scaleIn(
                initialScale = 0.985f,
                animationSpec = tween(AppMotion.CloseDuration, easing = AppMotion.Easing),
            )
    }

    return slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        tween(AppMotion.OpenDuration, easing = AppMotion.Easing),
    ) { it } + fadeIn(tween(AppMotion.OpenDuration), initialAlpha = 1f)
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExitTransition(
    isBack: Boolean = false,
): ExitTransition {
    if (isPeerChange()) return fadeOut(tween(160))

    if (isBack) {
        return slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.End,
            tween(AppMotion.CloseDuration, easing = AppMotion.Easing),
        ) { it / 7 } +
            fadeOut(tween(AppMotion.CloseDuration, easing = AppMotion.Easing), targetAlpha = 0.9f) +
            scaleOut(
                targetScale = 0.99f,
                animationSpec = tween(AppMotion.CloseDuration, easing = AppMotion.Easing),
            )
    }

    return slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        tween(AppMotion.OpenDuration, easing = AppMotion.Easing),
    ) { it / 4 } + fadeOut(tween(AppMotion.OpenDuration), targetAlpha = 0.65f)
}
