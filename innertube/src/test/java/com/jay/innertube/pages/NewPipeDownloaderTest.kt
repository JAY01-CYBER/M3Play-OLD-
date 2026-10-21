/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.innertube.pages

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection

class NewPipeDownloaderTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val downloader = NewPipeDownloaderImpl(null, null)

    @After fun tearDown() = server.stop(0)

    private fun request(status: Int = 200, body: String = "music"): Request {
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(status, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        return Request.newBuilder().get("http://127.0.0.1:${server.address.port}/").build()
    }

    @Test fun downloadReadsResponse() {
        assertEquals("music", downloader.execute(request()).responseBody())
    }

    @Test fun rateLimitReportsCaptcha() {
        assertThrows(ReCaptchaException::class.java) { downloader.execute(request(429)) }
    }

    @Test fun httpFailurePreservesStatusAndBody() {
        val response = downloader.execute(request(503, "unavailable"))
        assertEquals(503, response.responseCode())
        assertEquals("unavailable", response.responseBody())
    }

    @Test fun initializationPreservesTlsVerification() {
        val verifier = HttpsURLConnection.getDefaultHostnameVerifier()
        val socketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        NewPipe.init(downloader)
        assertSame(verifier, HttpsURLConnection.getDefaultHostnameVerifier())
        assertSame(socketFactory, HttpsURLConnection.getDefaultSSLSocketFactory())
        assertSame(downloader, NewPipe.getDownloader())
    }
    @Test fun decodesUtf8ByteOrderMark() {
        assertEquals("music", downloader.execute(request(body = "\uFEFFmusic")).responseBody())
    }

    private fun proxyRequest(auth: String?): Int {
        val attempts = AtomicInteger()
        server.createContext("/") { exchange ->
            attempts.incrementAndGet()
            exchange.responseHeaders.add("Proxy-Authenticate", "Basic realm=\"test\"")
            exchange.sendResponseHeaders(407, -1)
            exchange.close()
        }
        server.start()
        val proxyDownloader = NewPipeDownloaderImpl(Proxy(Proxy.Type.HTTP, server.address), auth)
        val response = proxyDownloader.execute(Request.newBuilder().get("http://example.invalid/").build())
        assertEquals(407, response.responseCode())
        return attempts.get()
    }

    @Test fun proxyWithoutCredentialsDoesNotRetry() {
        assertEquals(1, proxyRequest(null))
    }

    @Test fun rejectedProxyCredentialsAreOnlySentOnce() {
        assertEquals(2, proxyRequest("Basic synthetic"))
    }

}
