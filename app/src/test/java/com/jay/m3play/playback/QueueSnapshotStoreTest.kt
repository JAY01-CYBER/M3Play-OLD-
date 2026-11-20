/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.playback

import com.jay.m3play.models.PersistPlayerState
import com.jay.m3play.models.PersistQueue
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueSnapshotStoreTest {
    @Test fun burstsWriteOnlyTheLatestPendingSnapshot() = runTest {
        val writes = mutableListOf<QueueSnapshot?>()
        val store = QueueSnapshotStore({ writes += it }, backgroundScope)
        repeat(20) { store.save(snapshot(it.toLong())) }
        runCurrent()
        assertEquals(listOf(snapshot(19)), writes)
    }

    @Test fun clearSupersedesPendingSaveAndLaterSaveStillWorks() = runTest {
        val writes = mutableListOf<QueueSnapshot?>()
        val store = QueueSnapshotStore({ writes += it }, backgroundScope)
        store.save(snapshot(1))
        store.clear()
        runCurrent()
        assertEquals(listOf<QueueSnapshot?>(null), writes)
        store.save(snapshot(2))
        runCurrent()
        assertEquals(listOf(null, snapshot(2)), writes)
    }

    @Test fun failedWriteDoesNotKillTheWriter() = runTest {
        var attempts = 0
        val writes = mutableListOf<QueueSnapshot?>()
        val store = QueueSnapshotStore({
            if (attempts++ == 0) throw IOException("synthetic disk failure")
            writes += it
        }, backgroundScope)
        store.save(snapshot(1))
        runCurrent()
        store.save(snapshot(2))
        runCurrent()
        assertEquals(listOf(snapshot(2)), writes)
    }

    private fun snapshot(position: Long) = QueueSnapshot(
        PersistQueue("queue", emptyList(), 0, position),
        PersistQueue("automix", emptyList(), 0, 0),
        PersistPlayerState(false, 0, false, 1f, position, 0, 3),
    )
}
