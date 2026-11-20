/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.ui.component

internal data class SliderInput(
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val hasRange: Boolean,
)

/** Unknown duration (including Media3 TIME_UNSET) must not create a seekable/NaN track. */
internal fun normalizeSliderInput(value: Float, range: ClosedFloatingPointRange<Float>): SliderInput {
    val hasRange = range.start.isFinite() && range.endInclusive.isFinite() &&
        range.endInclusive > range.start && (range.endInclusive - range.start).isFinite()
    if (!hasRange) return SliderInput(0f, 0f..1f, false)
    return SliderInput(
        value = if (value.isFinite()) value.coerceIn(range) else range.start,
        range = range,
        hasRange = true,
    )
}
