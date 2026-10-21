/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.utils

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Extracts only the expected files into an empty staging directory before any live data changes. */
internal object BackupArchive {
    const val DATABASE = "song.db"
    const val SETTINGS = "settings.preferences_pb"
    private const val MAX_DATABASE_BYTES = 512L * 1024 * 1024
    private const val MAX_SETTINGS_BYTES = 16L * 1024 * 1024

    fun extract(input: InputStream, directory: File) {
        require(directory.isDirectory && directory.listFiles()?.isEmpty() == true)
        val seen = mutableSetOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in setOf(DATABASE, SETTINGS)) { "Unexpected backup entry" }
                require(seen.add(entry.name)) { "Duplicate backup entry" }
                val limit = if (entry.name == DATABASE) MAX_DATABASE_BYTES else MAX_SETTINGS_BYTES
                directory.resolve(entry.name).outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= limit) { "Backup entry is too large" }
                        output.write(buffer, 0, count)
                    }
                }
                zip.closeEntry()
            }
        }
        require(DATABASE in seen && SETTINGS in seen) { "Incomplete backup" }
        directory.resolve(DATABASE).inputStream().use {
            val header = ByteArray(16)
            require(it.read(header) == 16 && header.contentEquals("SQLite format 3\u0000".toByteArray())) {
                "Invalid database backup"
            }
        }
    }
}
