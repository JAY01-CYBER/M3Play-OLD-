/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.utils

import android.net.ConnectivityManager
import androidx.media3.common.PlaybackException
import com.jay.innertube.models.response.PlayerResponse
import com.jay.innertube.NewPipeUtils
import com.jay.innertube.NewPipeExtractor
import com.jay.m3play.constants.AudioQuality
import com.jay.innertube.YouTube
import com.jay.innertube.models.YouTubeClient
import com.jay.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.jay.innertube.models.YouTubeClient.Companion.IOS
import com.jay.innertube.models.YouTubeClient.Companion.MOBILE
import com.jay.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.jay.innertube.models.YouTubeClient.Companion.VISIONOS
import com.jay.innertube.models.YouTubeClient.Companion.WEB
import com.jay.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.jay.innertube.models.YouTubeClient.Companion.WEB_REMIX
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"

    /**
     * googlevideo rejects (403) stream URLs fetched with a User-Agent that does not match the
     * client that issued them. The issuing client is encoded in the URL's `c` query parameter,
     * so set the matching User-Agent on every googlevideo request.
     */
    val userAgentInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        if (request.url.host.endsWith("googlevideo.com")) {
            val ua = request.url.queryParameter("c")?.let { name ->
                (listOf(MAIN_CLIENT) + STREAM_FALLBACK_CLIENTS)
                    .firstOrNull { it.clientName.equals(name, ignoreCase = true) }
                    ?.userAgent
            }
            if (ua != null) {
                return@Interceptor chain.proceed(request.newBuilder().header("User-Agent", ua).build())
            }
        }
        chain.proceed(request)
    }

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(userAgentInterceptor)
        .build()
    /**
     * The main client is used for metadata and initial streams.
     * Do not use other clients for this because it can result in inconsistent metadata.
     * For example other clients can have different normalization targets (loudnessDb).
     *
     * [com.jay.innertube.models.YouTubeClient.WEB_REMIX] should be preferred here because currently it is the only client which provides:
     * - the correct metadata (like loudnessDb)
     * - premium formats
     */
    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX
    /**
     * Clients used for fallback streams in case the streams of the main client do not work.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        // VISIONOS gives cipher-free, PoToken-free anonymous streams that accept open-ended ranges.
        VISIONOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        IOS,
        WEB,
        WEB_CREATOR
    )
    private const val DEFAULT_EXPIRES_IN_SECONDS = 21540

    /** Order in which clients are asked for a stream: keyless clients first, [MAIN_CLIENT] after them. */
    private val STREAM_CLIENT_ORDER: List<YouTubeClient> =
        (STREAM_FALLBACK_CLIENTS.take(2) + MAIN_CLIENT + STREAM_FALLBACK_CLIENTS.drop(2)).distinct()

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    ) {
        /** Content length from the format, falling back to the `clen` parameter of the stream URL. */
        val contentLength: Long
            get() = format.contentLength
                ?: streamUrl.toHttpUrlOrNull()?.queryParameter("clen")?.toLongOrNull()
                ?: 0L

        /** Codec string from the mime type, e.g. `opus`; empty if the mime type has none. */
        val codecs: String
            get() = format.mimeType.substringAfter("codecs=", "").removeSurrounding("\"")
    }

    private class Candidate(
        val client: YouTubeClient,
        val response: PlayerResponse,
        val format: PlayerResponse.StreamingData.Format,
        val url: String,
        val expiresInSeconds: Int,
    ) {
        fun copyUrl(newUrl: String) = Candidate(client, response, format, newUrl, expiresInSeconds)
    }

    /**
     * Every client is tried in order until one yields a stream that actually answers a real range
     * request. If none validates, the first stream we managed to build is used anyway (a failed
     * validation must never be worse than no attempt at all).
     *
     * Metadata (audioConfig, videoDetails) comes from [MAIN_CLIENT] when available, otherwise from
     * whichever client produced the stream.
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        Timber.tag(logTag).d("Fetching player response for videoId: $videoId, playlistId: $playlistId")

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        val isLoggedIn = YouTube.cookie != null

        // The main client is only needed for metadata. A failure here must not block playback.
        val mainPlayerResponse = YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp)
            .onFailure { Timber.tag(logTag).w(it, "MAIN_CLIENT player request failed") }
            .getOrNull()

        var firstCandidate: Candidate? = null
        var winner: Candidate? = null
        var lastProblem: String? = null
        var lastPlayabilityReason: String? = null

        for (client in STREAM_CLIENT_ORDER) {
            if (client.loginRequired && !isLoggedIn) continue

            val response: PlayerResponse? = if (client == MAIN_CLIENT) {
                mainPlayerResponse
            } else {
                YouTube.player(videoId, playlistId, client, signatureTimestamp)
                    .onFailure {
                        lastProblem = "${client.clientName}: ${it.message}"
                        Timber.tag(logTag).w(it, "Player request failed for ${client.clientName}")
                    }
                    .getOrNull()
            }
            if (response == null) continue

            if (response.playabilityStatus.status != "OK") {
                lastPlayabilityReason = response.playabilityStatus.reason
                lastProblem = "${client.clientName}: ${response.playabilityStatus.status} ${response.playabilityStatus.reason.orEmpty()}"
                Timber.tag(logTag).d("Playability not OK for ${client.clientName}: $lastProblem")
                continue
            }

            val format = findFormat(response, audioQuality, connectivityManager)
            if (format == null) {
                lastProblem = "${client.clientName}: no audio format"
                continue
            }

            // First resolve the URL through the InnerTube response/cipher path.
            // If YouTube returns a stream that still cannot be resolved or does not answer
            // a real range request, fall back to NewPipe's stream resolver for the same itag.
            // This keeps InnerTube as the primary metadata/client source while giving playback
            // a second, independent URL resolution path when YouTube changes its cipher.
            val url = findUrlOrNull(format, videoId)
                ?: findNewPipeUrlOrNull(format, videoId)
            if (url == null) {
                lastProblem = "${client.clientName}: no stream url"
                continue
            }

            val candidate = Candidate(
                client = client,
                response = response,
                format = format,
                url = url,
                expiresInSeconds = response.streamingData?.expiresInSeconds ?: DEFAULT_EXPIRES_IN_SECONDS,
            )
            if (firstCandidate == null) firstCandidate = candidate

            if (validateStatus(url)) {
                winner = candidate
                Timber.tag(logTag).d("Stream validated with ${client.clientName}")
                break
            }

            // The InnerTube URL can be valid syntactically but rejected by googlevideo.
            // Try a fully resolved NewPipe URL for the exact same format before abandoning
            // this candidate.
            val newPipeUrl = findNewPipeUrlOrNull(format, videoId)
            if (newPipeUrl != null && newPipeUrl != url && validateStatus(newPipeUrl)) {
                winner = candidate.copyUrl(newPipeUrl)
                Timber.tag(logTag).d("Stream validated with NewPipe fallback for ${client.clientName}")
                break
            }

            lastProblem = "${client.clientName}: stream validation failed"
        }

        val chosen = winner ?: firstCandidate
        if (chosen == null) {
            throw PlaybackException(
                lastPlayabilityReason ?: lastProblem ?: "No playable stream found",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        Timber.tag(logTag).d("Using ${chosen.client.clientName}: ${chosen.format.mimeType}, bitrate: ${chosen.format.bitrate}")
        PlaybackData(
            audioConfig = mainPlayerResponse?.playerConfig?.audioConfig ?: chosen.response.playerConfig?.audioConfig,
            videoDetails = mainPlayerResponse?.videoDetails ?: chosen.response.videoDetails,
            playbackTracking = mainPlayerResponse?.playbackTracking ?: chosen.response.playbackTracking,
            format = chosen.format,
            streamUrl = chosen.url,
            streamExpiresInSeconds = chosen.expiresInSeconds,
        )
    }
    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).d("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX) // ANDROID_VR does not work with history
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        Timber.tag(logTag).d("Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")

        val format = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus stream
            }

        if (format != null) {
            Timber.tag(logTag).d("Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        } else {
            Timber.tag(logTag).d("No suitable audio format found")
        }

        return format
    }
    /**
     * Checks that the stream url answers a real (tiny) range request. HEAD is not used because
     * googlevideo answers it differently from the GET that the player will actually send.
     */
    private fun validateStatus(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .get()
                .url(url)
                .header("Range", "bytes=0-1")
                .build()
            httpClient.newCall(request).execute().use { response ->
                Timber.tag(logTag).d("Stream validation: ${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Timber.tag(logTag).w(e, "Stream URL validation failed with exception")
            false
        }
    }
    /**
     * Independent stream URL fallback. NewPipe returns already-resolved URLs, so this is useful
     * when YouTube changes the signature/throttling parameters on the InnerTube URL.
     */
    private fun findNewPipeUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
    ): String? = runCatching {
        NewPipeExtractor.newPipePlayer(videoId)
            .firstOrNull { (itag, _) -> itag == format.itag }
            ?.second
    }.onFailure {
        Timber.tag(logTag).w(it, "NewPipe stream URL fallback failed")
    }.getOrNull()

    /**
     * Wrapper around the [NewPipeUtils.getSignatureTimestamp] function which reports exceptions
     */
    private fun getSignatureTimestampOrNull(
        videoId: String
    ): Int? {
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        return NewPipeUtils.getSignatureTimestamp(videoId)
            .onSuccess { Timber.tag(logTag).d("Signature timestamp obtained: $it") }
            .onFailure {
                Timber.tag(logTag).e(it, "Failed to get signature timestamp")
                reportException(it)
            }
            .getOrNull()
    }
    /**
     * Wrapper around the [NewPipeUtils.getStreamUrl] function which reports exceptions
     */
    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        Timber.tag(logTag).d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId")
        return NewPipeUtils.getStreamUrl(format, videoId)
            .onSuccess { Timber.tag(logTag).d("Stream URL obtained successfully") }
            .onFailure {
                Timber.tag(logTag).e(it, "Failed to get stream URL")
                reportException(it)
            }
            .getOrNull()
    }
}