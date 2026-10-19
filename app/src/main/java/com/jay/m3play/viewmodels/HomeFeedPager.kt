/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.viewmodels

import com.jay.innertube.pages.HomePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class HomeFeedState(
    val page: HomePage? = null,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val failed: Boolean = false,
    val failedRefresh: Boolean = false,
)

/** Keeps refreshes, account changes and continuations from overwriting newer feed data. */
internal class HomeFeedPager(private val fetch: suspend (String?) -> Result<HomePage>) {
    private val mutableState = MutableStateFlow(HomeFeedState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private val visited = mutableSetOf<String>()

    suspend fun refresh(clear: Boolean = false) {
        val revision: Long
        synchronized(this) {
            revision = ++generation
            visited.clear()
            mutableState.value = HomeFeedState(if (clear) null else state.value.page, refreshing = true)
        }
        request(null, revision)
    }

    suspend fun loadMore() {
        val revision: Long
        val token: String
        synchronized(this) {
            val current = state.value
            if (current.refreshing || current.loadingMore) return
            token = current.page?.continuation ?: return
            revision = generation
            mutableState.value = current.copy(loadingMore = true, failed = false)
        }
        request(token, revision)
    }

    private suspend fun request(token: String?, revision: Long) {
        try {
            val page = fetch(token).getOrThrow()
            currentCoroutineContext().ensureActive()
            synchronized(this) {
                if (generation != revision) return
                if (token != null) visited += token
                val next = page.continuation?.takeUnless { it in visited }
                val previous = if (token == null) null else state.value.page
                val sections = (previous?.sections.orEmpty() + page.sections).distinct()
                mutableState.value = HomeFeedState(page.copy(
                    chips = page.chips ?: previous?.chips,
                    sections = sections,
                    continuation = next,
                ))
            }
        } catch (cancelled: CancellationException) {
            synchronized(this) {
                if (generation == revision) mutableState.value = state.value.copy(refreshing = false, loadingMore = false)
            }
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            synchronized(this) {
                if (generation == revision) mutableState.value = state.value.copy(
                    refreshing = false, loadingMore = false, failed = true, failedRefresh = token == null)
            }
        } finally {
            synchronized(this) {
                if (generation == revision) mutableState.value = state.value.copy(refreshing = false, loadingMore = false)
            }
        }
    }
}
