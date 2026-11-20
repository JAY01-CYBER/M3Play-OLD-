/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class LyricsQuery(val id: String, val title: String, val artist: String, val duration: Int, val album: String? = null)

/** Public read endpoints only. No extension keys, credentials, writes, or private APIs. */
internal class RemoteLyricsClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS).build(),
    private val betterBaseUrl: String = "https://api.betterlyrics.org",
    private val unisonBaseUrl: String = "https://unison.boidu.dev",
) {
    suspend fun betterLyrics(query: LyricsQuery): String {
        val url = betterBaseUrl.toHttpUrl().newBuilder().addPathSegment("getLyrics")
            .addQueryParameter("s", query.title).addQueryParameter("a", query.artist)
        query.album?.takeIf { it.isNotBlank() }?.let { url.addQueryParameter("al", it) }
        if (query.duration > 0) url.addQueryParameter("d", query.duration.toString())
        val body = get(url.build().toString())
        return validated(body["ttml"]?.jsonPrimitive?.contentOrNull, "ttml")
    }

    suspend fun unisonLyrics(query: LyricsQuery): String {
        if (query.id.matches(Regex("[A-Za-z0-9_-]{11}"))) {
            val url = unisonBaseUrl.toHttpUrl().newBuilder().addPathSegment("lyrics")
                .addQueryParameter("v", query.id).build()
            try { return unisonBody(get(url.toString())) } catch (error: LyricsHttpException) {
                if (error.status != 404) throw error
            }
        }
        val url = unisonBaseUrl.toHttpUrl().newBuilder().addPathSegment("lyrics")
            .addQueryParameter("song", query.title).addQueryParameter("artist", query.artist)
        query.album?.takeIf { it.isNotBlank() }?.let { url.addQueryParameter("album", it) }
        if (query.duration > 0) url.addQueryParameter("duration", query.duration.toString())
        return unisonBody(get(url.build().toString()))
    }

    private fun unisonBody(body: JsonObject): String {
        check(body["success"]?.jsonPrimitive?.booleanOrNull == true) { "Unison lyrics unavailable" }
        val data = body["data"]?.jsonObject ?: error("Unison response has no lyrics")
        return validated(data["lyrics"]?.jsonPrimitive?.contentOrNull,
            data["format"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }

    private fun validated(text: String?, format: String): String {
        check(!text.isNullOrBlank() && text != "LYRICS_NOT_FOUND") { "Empty lyrics response" }
        check(format in setOf("ttml", "lrc", "plain")) { "Unsupported lyrics format" }
        if (format != "plain") check(LyricsUtils.parseLyrics(text).isNotEmpty()) { "Invalid timed lyrics response" }
        return text
    }

    private suspend fun get(url: String): JsonObject = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "M3Play/1.0 (https://github.com/fluxx-pro/M3Play-OLD-)").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        if (!it.isSuccessful) throw LyricsHttpException(it.code)
                        val source = checkNotNull(it.body) { "Empty lyrics response" }.source()
                        source.request(2_000_001)
                        check(source.buffer.size <= 2_000_000) { "Lyrics response too large" }
                        Json.parseToJsonElement(source.readUtf8()).jsonObject
                    }
                    continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}

internal class LyricsHttpException(val status: Int) : IOException("Lyrics provider returned HTTP $status")

internal suspend fun <T> lyricsResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
