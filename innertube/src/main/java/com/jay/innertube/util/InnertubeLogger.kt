package com.jay.innertube.util

import java.util.logging.Level
import java.util.logging.Logger

object InnertubeLogger {
    private val logger = Logger.getLogger("M3Play-InnerTube")

    fun d(message: String) = logger.fine(message)

    fun w(message: String) = logger.warning(message)

    fun e(throwable: Throwable, message: String) = logger.log(Level.SEVERE, message, throwable)
}
