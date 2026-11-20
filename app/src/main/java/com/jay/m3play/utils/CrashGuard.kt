/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Counts consecutive startup crashes. After [DISABLE_AFTER] of them SeaGlass is switched off
 * (plain Material surfaces are drawn instead) so the app can still open, and the crash screen
 * can show what went wrong. The flag is stored synchronously because the process is about to die.
 */
object CrashGuard {
    private const val PREFS = "m3play_crash_guard"
    private const val KEY_STARTUP_CRASHES = "startup_crashes"
    private const val KEY_GLASS_DISABLED = "glass_disabled"
    private const val KEY_GPU_BACKDROP_DISABLED = "gpu_backdrop_disabled"
    private const val KEY_GPU_SESSION_STARTED_AT = "gpu_session_started_at"
    private const val KEY_GPU_TEST_VERSION = "gpu_test_version"
    // Bump with the test release. It re-enables AGSL_FULL exactly once; a native crash
    // in this version is then persisted and the next launch stays on the safe fallback.
    private const val GPU_TEST_VERSION_CODE = 3
    private const val DISABLE_AFTER = 2

    /** False once SeaGlass has been disabled; read by seaGlassSurface and MainActivity. */
    @Volatile
    var glassEnabled: Boolean = true
        private set

    /** Full AGSL backdrop path is independently guarded from lightweight SeaGlass surfaces. */
    @Volatile
    var gpuBackdropEnabled: Boolean = true
        private set

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        glassEnabled = runCatching { !prefs(context).getBoolean(KEY_GLASS_DISABLED, false) }
            .getOrDefault(true)
        gpuBackdropEnabled = runCatching { !prefs(context).getBoolean(KEY_GPU_BACKDROP_DISABLED, false) }
            .getOrDefault(true)
        // One-time opt-in for this GPU test build. A crash is not force-overridden:
        // onNativeExit persists the disable flag and later launches use LIGHTWEIGHT_TINT.
        runCatching {
            val p = prefs(context)
            val testedVersion = p.getInt(KEY_GPU_TEST_VERSION, 0)
            if (testedVersion < GPU_TEST_VERSION_CODE) {
                p.edit()
                    .putBoolean(KEY_GPU_BACKDROP_DISABLED, false)
                    .putInt(KEY_GPU_TEST_VERSION, GPU_TEST_VERSION_CODE)
                    .putLong(KEY_GPU_SESSION_STARTED_AT, System.currentTimeMillis())
                    .commit()
                gpuBackdropEnabled = true
            } else if (p.getLong(KEY_GPU_SESSION_STARTED_AT, 0L) == 0L) {
                p.edit().putLong(KEY_GPU_SESSION_STARTED_AT, System.currentTimeMillis()).commit()
            }
        }
    }

    /** Mark the start of a GPU-backdrop test session; a later native exit can roll it back. */
    fun markGpuSessionStarted(context: Context) {
        runCatching {
            prefs(context).edit().putLong(KEY_GPU_SESSION_STARTED_AT, System.currentTimeMillis()).commit()
        }
    }

    /** Disable only full GPU backdrop after a native crash newer than the current test session. */
    fun onNativeExit(context: Context, exitTimestamp: Long) {
        runCatching {
            val p = prefs(context)
            val startedAt = p.getLong(KEY_GPU_SESSION_STARTED_AT, Long.MAX_VALUE)
            if (exitTimestamp > startedAt) {
                gpuBackdropEnabled = false
                p.edit().putBoolean(KEY_GPU_BACKDROP_DISABLED, true).commit()
            }
        }
    }

    /** Call from the crash handler when the process dies shortly after starting. */
    fun onStartupCrash(context: Context) {
        runCatching {
            val p = prefs(context)
            val count = p.getInt(KEY_STARTUP_CRASHES, 0) + 1
            p.edit()
                .putInt(KEY_STARTUP_CRASHES, count)
                .putBoolean(KEY_GLASS_DISABLED, count >= DISABLE_AFTER || p.getBoolean(KEY_GLASS_DISABLED, false))
                .commit()
        }
    }

    /** Call once the app has stayed alive for a while. */
    fun onHealthy(context: Context) {
        runCatching { prefs(context).edit().putInt(KEY_STARTUP_CRASHES, 0).apply() }
    }

    fun setGpuBackdropEnabled(context: Context, enabled: Boolean) {
        gpuBackdropEnabled = enabled
        runCatching {
            prefs(context).edit()
                .putBoolean(KEY_GPU_BACKDROP_DISABLED, !enabled)
                .putLong(KEY_GPU_SESSION_STARTED_AT, System.currentTimeMillis())
                .commit()
        }
    }

    fun setGlassEnabled(context: Context, enabled: Boolean) {
        glassEnabled = enabled
        runCatching {
            prefs(context).edit()
                .putBoolean(KEY_GLASS_DISABLED, !enabled)
                .putInt(KEY_STARTUP_CRASHES, 0)
                .commit()
        }
    }
}
