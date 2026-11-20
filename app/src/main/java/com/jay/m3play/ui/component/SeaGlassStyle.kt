/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jay.m3play.utils.CrashGuard
import dev.seaglass.compose.LocalSeaGlassStageState
import dev.seaglass.compose.SeaGlassContentRole
import dev.seaglass.compose.seaGlass
import dev.seaglass.compose.toSeaGlassMaterials
import dev.seaglass.core.SeaGlassMaterial

/** SeaGlass material derived from the current Material theme (follows dynamic color / dark mode). */
@Composable
fun rememberSeaGlassMaterial(
    cornerRadiusDp: Float = 28f,
    elevated: Boolean = false,
): SeaGlassMaterial {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme, cornerRadiusDp, elevated) {
        val materials = scheme.toSeaGlassMaterials()
        (if (elevated) materials.elevatedGlass else materials.surfaceGlass).copy(
            // Keep the optical model authored by SeaGlass itself. Only adapt geometry to
            // M3 Play's component shape; do not replace the repository's frost/refraction math.
            cornerRadiusDp = cornerRadiusDp,
        )
    }
}

/**
 * Turns a composable into a SeaGlass surface. Put it on a container whose own colour is transparent.
 *
 * @param useStage when true the glass refracts the app-wide backdrop provided by MainActivity.
 * Use false inside separate windows (dialogs, bottom sheets) and over the player's own background:
 * there the backdrop is not behind the element, so a lightweight tinted glass is drawn instead.
 */
@Composable
fun Modifier.seaGlassSurface(
    cornerRadiusDp: Float = 28f,
    elevated: Boolean = false,
    useStage: Boolean = true,
    contentRole: SeaGlassContentRole = SeaGlassContentRole.TEXT_AND_ICONS,
): Modifier {
    // Native-crash recovery disables backdrop capture, not the lightweight SeaGlass material.
    // MainActivity pins the renderer to LIGHTWEIGHT_TINT and provides no stage in this mode.
    // This keeps SDF/tint/bevel surfaces available without RenderNode backdrop readback.
    val material = rememberSeaGlassMaterial(cornerRadiusDp, elevated).let { base ->
        if (CrashGuard.glassEnabled) base
        else base.copy(
            tintDensity = 0.42f,
            transparency = 0.26f,
            chromaticDispersion = 0f,
            frostRadiusDp = 16f,
            thicknessDp = 14f,
            rimChalkiness = 0.76f,
        )
    }
    val stage = if (useStage && CrashGuard.glassEnabled && CrashGuard.gpuBackdropEnabled) LocalSeaGlassStageState.current else null
    return this.seaGlass(
        stageState = stage,
        material = material,
        contentRole = contentRole,
        smoothRadiusDp = 22f,
    )
}
