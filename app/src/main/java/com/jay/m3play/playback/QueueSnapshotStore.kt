/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.playback

import android.content.Context
import android.util.AtomicFile
import com.jay.m3play.models.PersistPlayerState
import com.jay.m3play.models.PersistQueue
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.ObjectOutputStream
import java.io.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber

/** Player values are captured on Main before crossing the disk I/O boundary. */
data class QueueSnapshot(
    val queue: PersistQueue,
    val automix: PersistQueue,
    val playerState: PersistPlayerState,
)

/**
 * Process-scoped serial writer. Pending snapshots are conflated, avoiding concurrent
 * truncation and redundant writes during bursts of player events. Finishes queued
 * writes after the playback service is destroyed. Existing filenames/formats stay
 * compatible; AtomicFile protects each file (the three files are not a transaction).
 */
@Singleton
class QueueSnapshotStore internal constructor(
    private val writeSnapshot: (QueueSnapshot?) -> Unit,
    scope: CoroutineScope,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        writeSnapshot = { snapshot -> writeToDisk(context.filesDir, snapshot) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val commands = Channel<QueueSnapshot?>(Channel.CONFLATED)

    init {
        scope.launch {
            for (snapshot in commands) {
                try {
                    writeSnapshot(snapshot)
                } catch (error: Exception) {
                    Timber.e(error, "Unable to persist playback snapshot")
                }
            }
        }
    }

    fun save(snapshot: QueueSnapshot) { commands.trySend(snapshot) }
    fun clear() { commands.trySend(null) }

    private companion object {
        fun writeToDisk(directory: File, snapshot: QueueSnapshot?) {
            if (snapshot == null) {
                AtomicFile(directory.resolve(MusicService.PERSISTENT_QUEUE_FILE)).delete()
                AtomicFile(directory.resolve(MusicService.PERSISTENT_AUTOMIX_FILE)).delete()
                AtomicFile(directory.resolve(MusicService.PERSISTENT_PLAYER_STATE_FILE)).delete()
            } else {
                write(directory, MusicService.PERSISTENT_QUEUE_FILE, snapshot.queue)
                write(directory, MusicService.PERSISTENT_AUTOMIX_FILE, snapshot.automix)
                write(directory, MusicService.PERSISTENT_PLAYER_STATE_FILE, snapshot.playerState)
            }
        }

        fun write(directory: File, name: String, value: Serializable) {
            val atomicFile = AtomicFile(directory.resolve(name))
            val stream = atomicFile.startWrite()
            try {
                // finishWrite owns the stream; flush before syncing/closing it.
                ObjectOutputStream(stream).apply { writeObject(value); flush() }
                atomicFile.finishWrite(stream)
            } catch (error: Exception) {
                atomicFile.failWrite(stream)
                throw error
            }
        }
    }
}
