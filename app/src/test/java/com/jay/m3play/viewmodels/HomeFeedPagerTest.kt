/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.viewmodels

import com.jay.innertube.pages.HomePage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedPagerTest {
    private fun page(title: String, token: String? = null) = HomePage(null,
        listOf(HomePage.Section(title, null, null, null, emptyList())), token)

    @Test fun `loads all continuation pages without a section cap`() = runTest {
        val calls = mutableListOf<String?>()
        val pager = HomeFeedPager { token ->
            calls += token
            val index = token?.toInt() ?: 0
            Result.success(page("Section $index", if (index < 12) (index + 1).toString() else null))
        }
        pager.refresh()
        repeat(12) { pager.loadMore() }
        assertEquals(13, pager.state.value.page?.sections?.size)
        assertEquals(13, calls.size)
        assertNull(pager.state.value.page?.continuation)
        pager.loadMore()
        assertEquals(13, calls.size)
    }

    @Test fun `failure keeps loaded sections and retries the same continuation`() = runTest {
        var fail = true
        val pager = HomeFeedPager { token ->
            if (token == null) Result.success(page("First", "next"))
            else if (fail) Result.failure(IllegalStateException("offline"))
            else Result.success(page("Second"))
        }
        pager.refresh(); pager.loadMore()
        assertTrue(pager.state.value.failed)
        assertFalse(pager.state.value.loadingMore)
        assertEquals("next", pager.state.value.page?.continuation)
        fail = false; pager.loadMore()
        assertFalse(pager.state.value.failed)
        assertEquals(listOf("First", "Second"), pager.state.value.page?.sections?.map { it.title })
    }

    @Test fun `new refresh rejects an old account continuation response`() = runTest {
        val old = CompletableDeferred<Result<HomePage>>()
        var initialCalls = 0
        val pager = HomeFeedPager { token ->
            if (token == null) Result.success(page("Account ${++initialCalls}", "next")) else old.await()
        }
        pager.refresh()
        val pending = launch { pager.loadMore() }; runCurrent()
        pager.refresh(clear = true)
        old.complete(Result.success(page("Stale account")))
        pending.join()
        assertEquals(listOf("Account 2"), pager.state.value.page?.sections?.map { it.title })
    }

    @Test fun `duplicate requests and repeated continuation tokens cannot loop`() = runTest {
        val reply = CompletableDeferred<Result<HomePage>>()
        var calls = 0
        val pager = HomeFeedPager { token ->
            calls++
            if (token == null) Result.success(page("First", "next")) else reply.await()
        }
        pager.refresh()
        val pending = launch { pager.loadMore() }; runCurrent()
        pager.loadMore()
        assertEquals(2, calls)
        reply.complete(Result.success(page("First", "next")))
        pending.join()
        assertEquals(1, pager.state.value.page?.sections?.size)
        assertNull(pager.state.value.page?.continuation)
    }

    @Test fun `cancelled request clears the loading state without becoming a failure`() = runTest {
        val reply = CompletableDeferred<Result<HomePage>>()
        val pager = HomeFeedPager { reply.await() }
        val pending = launch { pager.refresh() }; runCurrent()
        pending.cancel(); pending.join()
        assertFalse(pager.state.value.refreshing)
        assertFalse(pager.state.value.failed)
    }

    @Test fun `failed refresh keeps old content and records that retry must refresh`() = runTest {
        var fail = false
        val pager = HomeFeedPager {
            if (fail) Result.failure(IllegalStateException("offline")) else Result.success(page("Saved"))
        }
        pager.refresh(); fail = true; pager.refresh()
        assertTrue(pager.state.value.failedRefresh)
        assertEquals("Saved", pager.state.value.page?.sections?.single()?.title)
    }
    @Test fun `preserves same-title shelves with different context labels`() = runTest {
        var count = 0
        val pager = HomeFeedPager {
            count++
            Result.success(HomePage(null, listOf(HomePage.Section("Listen again", "Artist $count",
                null, null, emptyList())), if (count == 1) "next" else null))
        }
        pager.refresh(); pager.loadMore()
        assertEquals(2, pager.state.value.page?.sections?.size)
    }
}
