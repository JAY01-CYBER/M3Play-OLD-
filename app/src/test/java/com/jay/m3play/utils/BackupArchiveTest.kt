/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.utils

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private val sqlite = "SQLite format 3\u0000payload".toByteArray()

    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    @Test fun extractsCompleteBackup() {
        val directory = temporary.newFolder()
        BackupArchive.extract(archive(BackupArchive.DATABASE to sqlite, BackupArchive.SETTINGS to byteArrayOf()), directory)
        assertArrayEquals(sqlite, directory.resolve(BackupArchive.DATABASE).readBytes())
        assertTrue(directory.resolve(BackupArchive.SETTINGS).exists())
    }

    @Test fun rejectsEmptyArchive() {
        assertThrows(IllegalArgumentException::class.java) { BackupArchive.extract(archive(), temporary.newFolder()) }
    }

    @Test fun rejectsMissingSettings() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchive.extract(archive(BackupArchive.DATABASE to sqlite), temporary.newFolder())
        }
    }

    @Test fun rejectsNonDatabasePayload() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchive.extract(archive(BackupArchive.DATABASE to "garbage".toByteArray(), BackupArchive.SETTINGS to byteArrayOf()), temporary.newFolder())
        }
    }

    @Test fun rejectsTraversalBeforeWritingOutsideStaging() {
        val directory = temporary.newFolder()
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchive.extract(archive("../escaped" to sqlite), directory)
        }
        assertFalse(requireNotNull(directory.parentFile).resolve("escaped").exists())
    }

    @Test fun rejectsNonZipInput() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchive.extract(ByteArrayInputStream("not a backup".toByteArray()), temporary.newFolder())
        }
    }
}
