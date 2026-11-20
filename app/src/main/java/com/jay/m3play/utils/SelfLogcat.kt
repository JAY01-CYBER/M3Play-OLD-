/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.utils

import android.content.Context
import java.io.File
import java.io.FileWriter

/**
 * Writes this app's own logcat lines to a file while it runs. An app may always read its own log
 * (no permission needed), so after a crash the last lines of the previous run are still available
 * and can be shown on the crash screen, with no computer and no adb.
 */
object SelfLogcat {
    private const val CURRENT = "logcat_current.txt"
    private const val PREVIOUS = "logcat_previous.txt"
    private const val MAX_BYTES = 400_000L
    private const val KEEP_BYTES = 150_000

    /** Moves the previous run's log aside, then starts recording this run. Safe to call once per process. */
    fun start(context: Context) {
        val dir = context.filesDir
        val current = File(dir, CURRENT)
        val previous = File(dir, PREVIOUS)
        runCatching {
            if (current.exists()) {
                previous.delete()
                current.renameTo(previous)
            }
        }
        Thread({ record(current) }, "self-logcat").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    /** Last [maxLines] lines of the previous run's log, or null if there is none. */
    fun previousTail(context: Context, maxLines: Int = 150): String? = runCatching {
        val file = File(context.filesDir, PREVIOUS)
        if (!file.exists() || file.length() == 0L) return null
        file.readLines().takeLast(maxLines).joinToString("\n")
    }.getOrNull()

    /** Last lines of the log of the run that is crashing right now (used by the crash handler). */
    fun currentTailForCrash(context: Context, maxLines: Int = 60): String? = runCatching {
        val file = File(context.filesDir, CURRENT)
        if (!file.exists() || file.length() == 0L) return null
        file.readLines().takeLast(maxLines).joinToString("\n").takeLast(12_000)
    }.getOrNull()

    private fun record(file: File) {
        runCatching {
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-v", "threadtime", "--pid=${android.os.Process.myPid()}")
            )
            var writer = FileWriter(file, true).buffered()
            var lines = 0
            process.inputStream.bufferedReader().useLines { sequence ->
                for (line in sequence) {
                    writer.write(line)
                    writer.newLine()
                    writer.flush() // the process may die at any moment
                    if (++lines % 200 == 0 && file.length() > MAX_BYTES) {
                        writer.close()
                        val tail = runCatching { file.readBytes().takeLast(KEEP_BYTES).toByteArray() }
                            .getOrDefault(ByteArray(0))
                        file.writeBytes(tail)
                        writer = FileWriter(file, true).buffered()
                    }
                }
            }
        }
    }
}
