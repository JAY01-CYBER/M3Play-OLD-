/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressRepositoryTest {
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun noConsumersMeansNoPlayerReadsOrListener() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource()
        PlaybackProgressRepository(source, backgroundScope)
        runCurrent()
        advanceTimeBy(60_000)
        assertEquals(0, source.reads)
        assertEquals(0, source.listeners.size)
    }

    @Test fun subscribersShareOneTickerAndDetachWhenLastConsumerLeaves() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource().apply { isPlaying = true }
        val repository = PlaybackProgressRepository(source, backgroundScope)
        val first = backgroundScope.launch { repository.progress.collect {} }
        val second = backgroundScope.launch { repository.progress.collect {} }
        runCurrent()
        assertEquals(1, source.listeners.size)
        assertEquals(1, source.reads)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, source.reads)
        first.cancel()
        runCurrent()
        assertEquals(1, source.listeners.size)
        second.cancel()
        runCurrent()
        val reads = source.reads
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, source.listeners.size)
        assertEquals(reads, source.reads)
    }

    @Test fun pauseStopsPollingButSeekEventsStillUpdate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource().apply { isPlaying = true }
        val repository = PlaybackProgressRepository(source, backgroundScope)
        backgroundScope.launch { repository.progress.collect {} }
        runCurrent()
        source.isPlaying = false
        source.notifyChanged()
        runCurrent()
        val reads = source.reads
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(reads, source.reads)
        source.value = PlaybackProgress(12_000, 60_000, 20_000)
        source.notifyChanged()
        runCurrent()
        assertEquals(source.value, repository.progress.value)
    }

    @Test fun bufferingUsesSlowUpdatesAndResumeRestartsPlaybackUpdates() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource().apply { isLoading = true }
        val repository = PlaybackProgressRepository(source, backgroundScope)
        backgroundScope.launch { repository.progress.collect {} }
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, source.reads)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, source.reads)
        source.isLoading = false
        source.notifyChanged()
        runCurrent()
        source.isPlaying = true
        source.notifyChanged()
        runCurrent()
        val reads = source.reads
        advanceTimeBy(100)
        runCurrent()
        assertEquals(reads + 1, source.reads)
    }

    @Test fun unknownDurationAndNegativePositionsAreSafeForSliders() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource().apply { value = PlaybackProgress(-1, Long.MIN_VALUE + 1, -1) }
        val repository = PlaybackProgressRepository(source, backgroundScope)
        backgroundScope.launch { repository.progress.collect {} }
        runCurrent()
        assertEquals(PlaybackProgress(), repository.progress.value)
    }

    @Test fun resubscribeReadsFreshStateAfterBackgroundPause() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource()
        val repository = PlaybackProgressRepository(source, backgroundScope)
        val first = backgroundScope.launch { repository.progress.collect {} }
        runCurrent()
        first.cancel()
        runCurrent()
        source.value = PlaybackProgress(42_000, 60_000, 60_000)
        backgroundScope.launch { repository.progress.collect {} }
        runCurrent()
        assertEquals(source.value, repository.progress.value)
        assertEquals(1, source.listeners.size)
    }

    private class FakeSource : PlaybackProgressSource {
        override var isPlaying = false
        override var isLoading = false
        var value = PlaybackProgress()
        var reads = 0
        val listeners = mutableListOf<() -> Unit>()
        override fun snapshot(): PlaybackProgress { reads++; return value }
        override fun subscribe(onChange: () -> Unit): () -> Unit {
            listeners += onChange
            return { listeners -= onChange }
        }
        fun notifyChanged() = listeners.toList().forEach { it() }
    }
}
