/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.utils

import android.content.Context
import java.io.File

/** Folder for crash report files that the crash screen can share (inside filesDir, covered by FileProvider). */
object CrashFiles {
    fun dir(context: Context): File =
        File(context.filesDir, "crash_reports").apply {
            mkdirs()
            // keep only the newest few files
            listFiles()?.sortedByDescending { it.lastModified() }?.drop(8)?.forEach { it.delete() }
        }
}
