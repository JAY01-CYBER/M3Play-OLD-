/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jay.m3play.R

val GeomFont = FontFamily(
    Font(R.font.geom, FontWeight.Normal)
)

private fun expressiveType(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = GeomFont, fontSize = size.sp, lineHeight = height.sp,
    fontWeight = weight, letterSpacing = 0.sp,
)

val Typography = Typography(
    displayLarge = expressiveType(57, 64, FontWeight.Bold),
    displayMedium = expressiveType(45, 52, FontWeight.Bold),
    displaySmall = expressiveType(36, 44, FontWeight.Bold),
    headlineLarge = expressiveType(32, 40, FontWeight.Bold),
    headlineMedium = expressiveType(28, 36, FontWeight.Bold),
    headlineSmall = expressiveType(24, 32, FontWeight.SemiBold),
    titleLarge = expressiveType(22, 28, FontWeight.SemiBold),
    titleMedium = expressiveType(18, 24, FontWeight.Medium),
    titleSmall = expressiveType(14, 20, FontWeight.Medium),
    bodyLarge = expressiveType(16, 24),
    bodyMedium = expressiveType(14, 20),
    bodySmall = expressiveType(12, 16),
    labelLarge = expressiveType(14, 20, FontWeight.SemiBold),
    labelMedium = expressiveType(12, 16, FontWeight.Medium),
    labelSmall = expressiveType(11, 16, FontWeight.Medium),
)
