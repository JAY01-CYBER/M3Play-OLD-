/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.viewmodels

import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.edit
import com.jay.m3play.constants.VisitorDataKey
import com.jay.m3play.utils.BackupArchive
import com.jay.m3play.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.jay.m3play.MainActivity
import com.jay.m3play.R
import com.jay.m3play.db.InternalDatabase
import com.jay.m3play.db.MusicDatabase
import com.jay.m3play.db.entities.ArtistEntity
import com.jay.m3play.db.entities.Song
import com.jay.m3play.db.entities.SongEntity
import com.jay.m3play.extensions.zipOutputStream
import com.jay.m3play.playback.MusicService
import com.jay.m3play.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import com.jay.m3play.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import java.io.FileInputStream
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val database: MusicDatabase,
) : ViewModel() {
    private val operationMutex = Mutex()

    suspend fun backup(context: Context, uri: Uri): Boolean = operationMutex.withLock {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                // Serializing through DataStore also handles a fresh installation with no file yet.
                val settings = context.dataStore.data.first()
                requireNotNull(context.contentResolver.openOutputStream(uri)) { "Cannot open backup destination" }.use {
                    it.buffered().zipOutputStream().use { output ->
                        output.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                        PreferencesFileSerializer.writeTo(settings, output)
                        output.closeEntry()
                        database.checkpoint()
                        FileInputStream(database.openHelper.writableDatabase.path).use { input ->
                            output.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                            input.copyTo(output)
                            output.closeEntry()
                        }
                    }
                }
            }
        }
        result.exceptionOrNull()?.let { reportException(it) }
        Toast.makeText(context, if (result.isSuccess) R.string.backup_create_success else R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
        result.isSuccess
    }

    // Once restoration starts, navigation must not cancel it between preference and database writes.
    suspend fun restore(context: Context, uri: Uri): Unit = operationMutex.withLock {
        withContext(kotlinx.coroutines.NonCancellable) {
            var databaseClosed = false
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val directory = java.io.File(context.cacheDir, "restore-${java.util.UUID.randomUUID()}")
                    check(directory.mkdir()) { "Cannot create restore staging directory" }
                    try {
                        requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open backup" }.use {
                            BackupArchive.extract(it, directory)
                        }
                        val settings = directory.resolve(SETTINGS_FILENAME).inputStream().use {
                            PreferencesFileSerializer.readFrom(it)
                        }
                        val stagedDb = directory.resolve(InternalDatabase.DB_NAME)
                        SQLiteDatabase.openDatabase(stagedDb.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                                require(cursor.moveToFirst() && cursor.getString(0) == "ok") { "Corrupt database backup" }
                            }
                            db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'song'", null).use {
                                require(it.moveToFirst()) { "Not a music library backup" }
                            }
                        }
                        val validationDatabase = androidx.room.Room.databaseBuilder(
                            context, InternalDatabase::class.java, stagedDb.absolutePath,
                        ).addMigrations(com.jay.m3play.db.MIGRATION_1_2).build()
                        try {
                            validationDatabase.openHelper.writableDatabase
                        } finally {
                            validationDatabase.close()
                        }
                        // Prepare on the destination filesystem, before closing Room or changing preferences.
                        val destination = context.getDatabasePath(InternalDatabase.DB_NAME)
                        val replacement = java.io.File.createTempFile("restore-", ".db", destination.parentFile)
                        try {
                            stagedDb.copyTo(replacement, overwrite = true)
                            val previousSettings = context.dataStore.data.first()
                            context.dataStore.updateData { settings }
                            try {
                                database.checkpoint()
                                database.close()
                                databaseClosed = true
                                android.system.Os.rename(replacement.path, destination.path)
                                java.io.File(destination.path + "-wal").delete()
                                java.io.File(destination.path + "-shm").delete()
                            } catch (e: Exception) {
                                context.dataStore.updateData { previousSettings }
                                throw e
                            }
                        } finally {
                            replacement.delete()
                        }
                    } finally {
                        directory.deleteRecursively()
                    }
                }
            }
            result.onSuccess {
                context.stopService(Intent(context, MusicService::class.java))
                context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                exitProcess(0)
            }.onFailure {
                reportException(it)
                Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                if (databaseClosed) {
                    // The original database is intact if atomic replacement fails; reopen it in a fresh process.
                    com.jay.m3play.ui.screens.settings.restartApp(context)
                }
            }
        }
    }

    fun importPlaylistFromCsv(context: Context, uri: Uri): ArrayList<Song> {
        val songs = arrayListOf<Song>()
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                lines.forEachIndexed { _, line ->
                    val parts = line.split(",").map { it.trim() }
                    if (parts.size < 2) return@forEachIndexed
                    val title = parts[0]
                    val artistStr = parts[1]

                    val artists = artistStr.split(";").map { it.trim() }.map {
                        ArtistEntity(
                            id = "",
                            name = it,
                        )
                    }
                    val mockSong = Song(
                        song = SongEntity(
                            id = "",
                            title = title,
                        ),
                        artists = artists,
                    )
                    songs.add(mockSong)
                }
            }
        }

        if (songs.isEmpty()) {
            Toast.makeText(
                context,
                "No songs found. Invalid file, or perhaps no song matches were found.",
                Toast.LENGTH_SHORT
            ).show()
        }
        return songs
    }

    fun loadM3UOnline(
        context: Context,
        uri: Uri,
    ): ArrayList<Song> {
        val songs = ArrayList<Song>()

        runCatching {
            context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                if (lines.firstOrNull()?.startsWith("#EXTM3U") == true) {
                    lines.forEachIndexed { _, rawLine ->
                        if (rawLine.startsWith("#EXTINF:")) {
                            // maybe later write this to be more efficient
                            val artists =
                                rawLine.substringAfter("#EXTINF:").substringAfter(',').substringBefore(" - ").split(';')
                            val title = rawLine.substringAfter("#EXTINF:").substringAfter(',').substringAfter(" - ")

                            val mockSong = Song(
                                song = SongEntity(
                                    id = "",
                                    title = title,
                                ),
                                artists = artists.map { ArtistEntity("", it) },
                            )
                            songs.add(mockSong)

                        }
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            Toast.makeText(
                context,
                "No songs found. Invalid file, or perhaps no song matches were found.",
                Toast.LENGTH_SHORT
            ).show()
        }
        return songs
    }

    suspend fun resetVisitorData(context: Context) {
        context.dataStore.edit { it.remove(VisitorDataKey) }
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
    }
}

