/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class RemoteLyricsClientTest {
    private val query = LyricsQuery("abcdefghijk", "Hello & 世界", "A/B", 123, "Test Album")
    private val ttml = "<tt><body><p begin='1s' end='3s'><span begin='1s' end='2s'>Hello</span></p></body></tt>"
    private fun client(reply: (Request) -> Pair<Int, String>) = RemoteLyricsClient(
        client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val (code, body) = reply(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture").body(body.toResponseBody()).build()
        }).build(), betterBaseUrl = "https://better.test", unisonBaseUrl = "https://unison.test")

    @Test fun `Better Lyrics encodes complete metadata and preserves TTML`() = runBlocking {
        val api = client { request ->
            assertEquals("/getLyrics", request.url.encodedPath)
            assertEquals(query.title, request.url.queryParameter("s"))
            assertEquals(query.artist, request.url.queryParameter("a"))
            assertEquals("Test Album", request.url.queryParameter("al"))
            assertEquals("123", request.url.queryParameter("d"))
            200 to buildJsonObject { put("ttml", ttml) }.toString()
        }
        assertEquals(ttml, api.betterLyrics(query))
    }

    @Test fun `Unison retries video miss using metadata`() = runBlocking {
        val requests = mutableListOf<Request>()
        val api = client {
            requests += it
            if (requests.size == 1) 404 to "{}" else 200 to """{"success":true,"data":{"lyrics":"[00:01]Hello","format":"lrc"}}"""
        }
        assertEquals("[00:01]Hello", api.unisonLyrics(query))
        assertEquals(2, requests.size)
        assertEquals(query.id, requests[0].url.queryParameter("v"))
        assertEquals(query.title, requests[1].url.queryParameter("song"))
        assertEquals("123", requests[1].url.queryParameter("duration"))
        assertEquals(query.album, requests[1].url.queryParameter("album"))
    }

    @Test fun `Unison accepts TTML and plain lyrics without sending unknown metadata`() = runBlocking {
        for ((format, lyrics) in listOf("ttml" to ttml, "plain" to "Hello world")) {
            val api = client { request ->
                assertNull(request.url.queryParameter("v"))
                assertNull(request.url.queryParameter("duration"))
                assertNull(request.url.queryParameter("album"))
                200 to buildJsonObject {
                    put("success", true)
                    put("data", buildJsonObject { put("format", format); put("lyrics", lyrics) })
                }.toString()
            }
            assertEquals(lyrics, api.unisonLyrics(query.copy(id = "local", duration = -1, album = null)))
        }
    }

    @Test fun `rate limits do not cause a second Unison request`() = runBlocking {
        var count = 0
        val result = lyricsResult { client { count++; 429 to "{}" }.unisonLyrics(query) }
        assertEquals(429, (result.exceptionOrNull() as LyricsHttpException).status)
        assertEquals(1, count)
    }

    @Test fun `rejects empty invalid oversized and unsuccessful responses`() = runBlocking {
        for (body in listOf("{}", """{"ttml":""}""", """{"ttml":"<tt/>"}""", " ".repeat(2_000_001))) {
            assertTrue(lyricsResult { client { 200 to body }.betterLyrics(query) }.isFailure)
        }
        assertTrue(lyricsResult { client { 200 to """{"success":false}""" }.unisonLyrics(query) }.isFailure)
    }

    @Test fun `cancelling lookup cancels HTTP call`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Boolean>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            entered.complete(Unit)
            val deadline = System.nanoTime() + 2_000_000_000
            while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(5)
            cancelled.complete(chain.call().isCanceled())
            throw java.io.IOException("cancelled")
        }.build()
        val job = launch { RemoteLyricsClient(http).betterLyrics(query) }
        withTimeout(5000) { entered.await() }
        job.cancelAndJoin()
        assertTrue(withTimeout(5000) { cancelled.await() })
    }
}
