/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 */

package com.jay.m3play.utils

import android.util.Log
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LogEntry(
    val time: Long,
    val level: Int,
    val tag: String?,
    val message: String,
)

object GlobalLog {
    private const val MAX_ENTRIES = 1000

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun append(level: Int, tag: String?, message: String) {
        val entry = LogEntry(
            time = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
        )
        _logs.value = (_logs.value + entry).takeLast(MAX_ENTRIES)
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun format(entry: LogEntry): String {
        val ts = timeFormat.format(Date(entry.time))
        val levelText = when (entry.level) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "?"
        }
        return "[$ts] $levelText/${entry.tag ?: "M3Play"}: ${entry.message}"
    }

    fun allAsText(): String =
        _logs.value.joinToString(separator = "\n") { format(it) }
}

class GlobalLogTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        runCatching {
            val finalMessage = if (t == null) {
                message
            } else {
                "$message\n${t.stackTraceToString()}"
            }
            GlobalLog.append(priority, tag, finalMessage)
        }
    }
}

private class GlobalJulHandler : Handler() {
    override fun publish(record: LogRecord?) {
        if (record == null) return
        runCatching {
            val level = when {
                record.level.intValue() >= Level.SEVERE.intValue() -> Log.ERROR
                record.level.intValue() >= Level.WARNING.intValue() -> Log.WARN
                record.level.intValue() >= Level.INFO.intValue() -> Log.INFO
                else -> Log.DEBUG
            }

            val throwableText = record.thrown?.let {
                val writer = StringWriter()
                it.printStackTrace(PrintWriter(writer))
                "\n${writer}"
            }.orEmpty()

            GlobalLog.append(
                level = level,
                tag = record.loggerName ?: "JavaLog",
                message = (record.message ?: "") + throwableText,
            )
        }
    }

    override fun flush() = Unit
    override fun close() = Unit
}

private val globalJulHandler = GlobalJulHandler()

fun installGlobalLogging() {
    runCatching {
        if (!Timber.forest().any { it is GlobalLogTree }) {
            Timber.plant(GlobalLogTree())
        }

        val rootLogger = Logger.getLogger("")
        if (!rootLogger.handlers.contains(globalJulHandler)) {
            rootLogger.addHandler(globalJulHandler)
        }
        Logger.getLogger("M3Play-InnerTube").level = Level.ALL
    }
}
