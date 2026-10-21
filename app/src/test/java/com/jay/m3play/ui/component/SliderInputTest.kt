/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SliderInputTest {
    @Test fun unknownOrInvalidDurationsDisableSeekingAndUseFiniteTrack() {
        for (end in listOf(0f, -9223372036854775807L.toFloat(), -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val input = normalizeSliderInput(1000f, 0f..end)
            assertFalse(input.hasRange)
            assertEquals(0f, input.value, 0f)
            assertEquals(0f..1f, input.range)
        }
    }

    @Test fun positionIsClampedWhenSwitchingToAShorterTrack() {
        val input = normalizeSliderInput(240_000f, 0f..90_000f)
        assertTrue(input.hasRange)
        assertEquals(90_000f, input.value, 0f)
        assertEquals(0f, normalizeSliderInput(-100f, 0f..90_000f).value, 0f)
    }

    @Test fun validSeekingKeepsPositionAndRange() {
        val input = normalizeSliderInput(42_000f, 0f..180_000f)
        assertTrue(input.hasRange)
        assertEquals(42_000f, input.value, 0f)
        assertEquals(0f..180_000f, input.range)
    }

    @Test fun nonFinitePositionsResetToRangeStart() {
        for (position in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(5f, normalizeSliderInput(position, 5f..120f).value, 0f)
        }
    }
}
