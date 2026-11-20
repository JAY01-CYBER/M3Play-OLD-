package com.jay.innertube.models.response

import com.jay.innertube.models.ResponseContext
import com.jay.innertube.models.Thumbnails
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PlayerResponse with [com.jay.innertube.models.YouTubeClient.WEB_REMIX] client
 */
@Serializable
data class PlayerResponse(
    val responseContext: ResponseContext? = null,
    val playabilityStatus: PlayabilityStatus,
    val playerConfig: PlayerConfig? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
    @SerialName("playbackTracking")
    val playbackTracking: PlaybackTracking? = null,
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String = "UNKNOWN",
        val reason: String? = null,
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig? = null,
    ) {
        @Serializable
        data class AudioConfig(
            val loudnessDb: Double? = null,
            val perceptualLoudnessDb: Double? = null,
        )
    }

    @Serializable
    data class StreamingData(
        val formats: List<Format>? = null,
        val adaptiveFormats: List<Format> = emptyList(),
        val expiresInSeconds: Int = 21540,
    ) {
        @Serializable
        data class Format(
            val itag: Int,
            val url: String? = null,
            val mimeType: String = "",
            val bitrate: Int = 0,
            val width: Int? = null,
            val height: Int? = null,
            val contentLength: Long? = null,
            val quality: String = "",
            val fps: Int? = null,
            val qualityLabel: String? = null,
            val averageBitrate: Int? = null,
            val audioQuality: String? = null,
            val approxDurationMs: String? = null,
            val audioSampleRate: Int? = null,
            val audioChannels: Int? = null,
            val loudnessDb: Double? = null,
            val lastModified: Long? = null,
            val signatureCipher: String? = null,
            val cipher: String? = null,
            val audioTrack: AudioTrack? = null
        ) {
            val isAudio: Boolean
                get() = width == null
            val isOriginal: Boolean
                get() = audioTrack?.isAutoDubbed == null

            @Serializable
            data class AudioTrack(
                val displayName: String? = null,
                val id: String? = null,
                val isAutoDubbed: Boolean? = null,
            )
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String,
        val title: String? = null,
        val author: String? = null,
        val channelId: String = "",
        val lengthSeconds: String = "0",
        val musicVideoType: String? = null,
        val viewCount: String? = null,
        val thumbnail: Thumbnails = Thumbnails(emptyList()),
    )

    @Serializable
    data class PlaybackTracking(
        @SerialName("videostatsPlaybackUrl")
        val videostatsPlaybackUrl: VideostatsPlaybackUrl? = null,
        @SerialName("videostatsWatchtimeUrl")
        val videostatsWatchtimeUrl: VideostatsWatchtimeUrl? = null,
        @SerialName("atrUrl")
        val atrUrl: AtrUrl? = null,
    ) {
        @Serializable
        data class VideostatsPlaybackUrl(
            @SerialName("baseUrl")
            val baseUrl: String? = null,
        )
        @Serializable
        data class VideostatsWatchtimeUrl(
            @SerialName("baseUrl")
            val baseUrl: String? = null,
        )
        @Serializable
        data class AtrUrl(
            @SerialName("baseUrl")
            val baseUrl: String? = null,
        )
    }
}
