/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.utils

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jay.m3play.CrashActivity
import com.jay.m3play.utils.CrashGuard

/**
 * Catches crashes that the Java crash handler can never see: native crashes (SIGSEGV), ANRs,
 * the system killing the app for memory, and so on. Android records why the previous process died
 * (API 30+); on the next launch we show that on the crash screen once.
 */
object ExitInfoReporter {
    private const val PREFS = "m3play_exit_info"
    private const val KEY_LAST_REPORTED = "last_reported_timestamp"

    private const val KEY_JAVA_CRASH_SHOWN_AT = "java_crash_shown_at"

    /** Called by the Java crash handler so the same crash is not reported twice at the next launch. */
    fun markJavaCrashShown(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_JAVA_CRASH_SHOWN_AT, System.currentTimeMillis()).commit()
        }
    }

    private val interestingReasons = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_SIGNALED,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_DEPENDENCY_DIED,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_UNKNOWN,
        ApplicationExitInfo.REASON_OTHER,
    )

    fun reportPreviousExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val last = am.getHistoricalProcessExitReasons(context.packageName, 0, 8)
                .firstOrNull { it.processName == context.packageName }
                ?: return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (last.timestamp <= prefs.getLong(KEY_LAST_REPORTED, 0L)) return
            if (last.reason !in interestingReasons) return
            if (last.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                // Preserve SeaGlass surfaces, but turn off the GPU backdrop on the next launch.
                CrashGuard.onNativeExit(context, last.timestamp)
            }
            // A Java crash that the crash handler already showed is not reported a second time.
            if (last.reason == ApplicationExitInfo.REASON_CRASH) {
                val shownAt = prefs.getLong(KEY_JAVA_CRASH_SHOWN_AT, 0L)
                if (kotlin.math.abs(last.timestamp - shownAt) < 30_000L) return
            }
            prefs.edit().putLong(KEY_LAST_REPORTED, last.timestamp).commit()

            val sb = StringBuilder()
            sb.append("The previous run of M3 Play ended without a normal Java crash.\n\n")
            sb.append("Reason: ").append(reasonName(last.reason)).append('\n')
            sb.append("Description: ").append(last.description ?: "-").append('\n')
            sb.append("Status: ").append(last.status).append('\n')
            sb.append("Importance: ").append(last.importance).append('\n')
            sb.append("RSS (KB): ").append(last.rss / 1024).append('\n')
            sb.append("PSS (KB): ").append(last.pss / 1024).append('\n')
            sb.append("Android ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append("), ")
                .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n\n")

            val files = mutableListOf<String>()
            if (last.reason == ApplicationExitInfo.REASON_ANR || last.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                runCatching {
                    last.traceInputStream?.use { stream ->
                        // Keep the complete raw tombstone as a file: it has the crashing thread's frames,
                        // which are easy to lose when the text is copied or cut.
                        val raw = stream.readNBytesCompat(3_000_000)
                        val dir = CrashFiles.dir(context)
                        val rawFile = java.io.File(dir, "tombstone_${last.timestamp}.bin")
                        rawFile.writeBytes(raw)
                        files += rawFile.absolutePath

                        // Tombstones are binary; keep printable text only (library names, signal info, frames).
                        val text = String(raw.copyOf(minOf(raw.size, 12_000)), Charsets.ISO_8859_1)
                            .replace(Regex("[^\\x20-\\x7E\\n]+"), " ")
                            .replace(Regex(" {3,}"), "  ")
                        sb.append("Trace (printable part, first 12 KB; the full tombstone is in the shared file):\n")
                            .append(text.take(6_000))
                    }
                }
            }

            SelfLogcat.previousTail(context)?.let { tail ->
                sb.append("\n\nLast log lines of that run:\n").append(tail.takeLast(30_000))
            }

            val report = sb.toString()
            runCatching {
                val reportFile = java.io.File(CrashFiles.dir(context), "report_${last.timestamp}.txt")
                reportFile.writeText(report)
                files.add(0, reportFile.absolutePath)
            }

            context.startActivity(
                Intent(context, CrashActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(CrashActivity.EXTRA_TRACE, report.take(60_000))
                    .putExtra(CrashActivity.EXTRA_FILES, files.toTypedArray())
            )
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val buffer = ByteArray(max)
        var total = 0
        while (total < max) {
            val n = read(buffer, total, max - total)
            if (n <= 0) break
            total += n
        }
        return buffer.copyOf(total)
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH (Java crash that was not caught by the app handler)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE (native crash)"
        ApplicationExitInfo.REASON_ANR -> "ANR (app not responding)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY (killed for memory)"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED (killed by a signal)"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "reason code $reason"
    }
}
