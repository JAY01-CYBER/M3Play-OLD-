/*
 * Copyright (c) 2026 JAY01-CYBER
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jay.m3play

import android.app.Application
import android.content.Context
import android.os.Build
import com.jay.m3play.utils.CrashFiles
import com.jay.m3play.utils.CrashGuard
import com.jay.m3play.utils.ExitInfoReporter
import com.jay.m3play.utils.SelfLogcat
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

import com.jay.innertube.YouTube
import com.jay.innertube.models.YouTubeLocale
import com.jay.kugou.KuGou 

import com.jay.m3play.constants.AccountChannelHandleKey
import com.jay.m3play.constants.AccountEmailKey
import com.jay.m3play.constants.AccountNameKey
import com.jay.m3play.constants.ContentCountryKey
import com.jay.m3play.constants.ContentLanguageKey
import com.jay.m3play.constants.CountryCodeToName
import com.jay.m3play.constants.DataSyncIdKey
import com.jay.m3play.constants.InnerTubeCookieKey
import com.jay.m3play.constants.LanguageCodeToName
import com.jay.m3play.constants.MaxImageCacheSizeKey
import com.jay.m3play.constants.ProxyEnabledKey
import com.jay.m3play.constants.ProxyTypeKey
import com.jay.m3play.constants.ProxyUrlKey
import com.jay.m3play.constants.SYSTEM_DEFAULT
import com.jay.m3play.constants.UseLoginForBrowse
import com.jay.m3play.constants.VisitorDataKey
import com.jay.m3play.extensions.toEnum
import com.jay.m3play.extensions.toInetSocketAddress
import com.jay.m3play.utils.dataStore
import com.jay.m3play.utils.get
import com.jay.m3play.utils.installGlobalLogging
import com.jay.m3play.utils.reportException

import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.Proxy
import java.util.Locale

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // The crash screen runs in its own ":crash" process. Skip all app setup there.
        if (isCrashProcess()) return
        instance = this
        CrashGuard.init(this)
        SelfLogcat.start(this)
        ExitInfoReporter.reportPreviousExit(this)
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        installGlobalLogging()
        installCrashLogger()

        applicationScope.launch(Dispatchers.IO) {
            try {
                val locale = Locale.getDefault()
                val languageTag = locale.toLanguageTag().replace("-Hant", "") 
                
                YouTube.locale = YouTubeLocale(
                    gl = dataStore.data.first()[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                        ?: locale.country.takeIf { it in CountryCodeToName }
                        ?: "US",
                    hl = dataStore.data.first()[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                        ?: locale.language.takeIf { it in LanguageCodeToName }
                        ?: languageTag.takeIf { it in LanguageCodeToName }
                        ?: "en"
                )
                
                if (languageTag == "zh-TW") {
                    KuGou.useTraditionalChinese = true
                }

                if (dataStore.data.first()[ProxyEnabledKey] == true) {
                    try {
                        val proxyUrl = dataStore.data.first()[ProxyUrlKey]
                        if (!proxyUrl.isNullOrBlank()) {
                            YouTube.proxy = Proxy(
                                dataStore.data.first()[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                                proxyUrl.toInetSocketAddress()
                            )
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@App, "Failed to parse proxy url.", LENGTH_SHORT).show()
                        }
                        reportException(e)
                    }
                }

                if (dataStore.data.first()[UseLoginForBrowse] != false) {
                    YouTube.useLoginForBrowse = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        applicationScope.launch {
            try {
                dataStore.data
                    .map { it[VisitorDataKey] }
                    .distinctUntilChanged()
                    .collect { visitorData ->
                        YouTube.visitorData = visitorData
                            ?.takeIf { it != "null" } 
                            ?: YouTube.visitorData().onFailure {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@App, "Failed to get visitorData.", LENGTH_SHORT).show()
                                }
                                reportException(it)
                            }.getOrNull()?.also { newVisitorData ->
                                dataStore.edit { settings ->
                                    settings[VisitorDataKey] = newVisitorData
                                }
                            }
                    }
            } catch (e: Exception) {}
        }
        
        applicationScope.launch {
            try {
                dataStore.data
                    .map { it[DataSyncIdKey] }
                    .distinctUntilChanged()
                    .collect { dataSyncId ->
                        YouTube.dataSyncId = dataSyncId?.let {
                            it.takeIf { !it.contains("||") }
                                ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                                ?: it.substringAfter("||")
                        }
                    }
            } catch (e: Exception) {}
        }
        
        applicationScope.launch {
            try {
                dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .distinctUntilChanged()
                    .collect { cookie ->
                        try {
                            YouTube.cookie = cookie
                        } catch (e: Exception) {
                            Timber.e("Could not parse cookie. Clearing existing cookie. %s", e.message)
                            forgetAccount(this@App)
                        }
                    }
            } catch (e: Exception) {}
        }
    }

    /**
     * Saves the stack trace of any crash to filesDir/last_crash.txt so MainActivity can show it
     * on the next launch. If the app dies within a few seconds of starting (a startup crash loop),
     * the saved playback queue files are deleted, because a corrupted queue is the most common
     * cause of "opens and closes immediately until data is cleared".
     */
    private fun installCrashLogger() {
        val startTime = System.currentTimeMillis()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        // After the app has stayed alive for a while the startup-crash counter is reset.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            { CrashGuard.onHealthy(this) },
            STARTUP_CRASH_WINDOW_MS
        )
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            var shownCrashScreen = false
            try {
                val trace = android.util.Log.getStackTraceString(throwable)
                val report = "M3 Play ${BuildConfig.VERSION_NAME}\n" +
                    "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "Thread: ${thread.name}\n\n$trace"
                runCatching { filesDir.resolve(CRASH_LOG_FILE).writeText(report) }

                val isStartupCrash = System.currentTimeMillis() - startTime < STARTUP_CRASH_WINDOW_MS
                if (isStartupCrash) {
                    CrashGuard.onStartupCrash(this)
                    listOf("persistent_queue.data", "persistent_automix.data", "persistent_player_state.data")
                        .forEach { name ->
                            runCatching { filesDir.resolve(name).delete() }
                            runCatching { filesDir.resolve("$name.new").delete() }
                            runCatching { filesDir.resolve("$name.bak").delete() }
                        }
                }

                // Show the crash text in a separate process (works even if MainActivity is what crashed).
                ExitInfoReporter.markJavaCrashShown(this)
                val fullReport = report + (SelfLogcat.currentTailForCrash(this, 150)?.let { "\n\nLast log lines:\n$it" } ?: "")
                val reportPath = runCatching {
                    java.io.File(CrashFiles.dir(this), "java_crash_${System.currentTimeMillis()}.txt")
                        .also { it.writeText(fullReport) }.absolutePath
                }.getOrNull()
                shownCrashScreen = runCatching {
                    startActivity(
                        android.content.Intent(this, CrashActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .putExtra(CrashActivity.EXTRA_FILES, listOfNotNull(reportPath).toTypedArray())
                            .putExtra(
                                CrashActivity.EXTRA_TRACE,
                                (report + (SelfLogcat.currentTailForCrash(this)?.let { "\n\nLast log lines:\n$it" } ?: "")).take(60_000)
                            )
                    )
                }.isSuccess
            } catch (_: Throwable) {
            }
            if (shownCrashScreen) {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            } else {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun isCrashProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching {
                java.io.File("/proc/self/cmdline").readText().trim { it <= ' ' || it == '\u0000' }
            }.getOrNull()
        }
        return name?.endsWith(":crash") == true
    }

    override fun newImageLoader(context: Context): ImageLoader {
        // Safe read from DataStore with fallback
        val cacheSize = try { runBlocking { dataStore.data.first()[MaxImageCacheSizeKey] ?: 512 } } catch (e: Exception) { 512 }

        if (cacheSize == 0) {
            return ImageLoader.Builder(this)
                .crossfade(true)
                .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }

        return ImageLoader.Builder(this)
            .crossfade(true)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .diskCache(
                try {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil").toOkioPath())
                        .maxSizeBytes((cacheSize) * 1024 * 1024L)
                        .build()
                } catch (e: Exception) {
                    // Coil Cache Corruption Handler
                    // Agar image cache corrupt hoga toh app crash nahi karega, 
                    // folder ko safely delete karke naya bana lega!
                    try { cacheDir.resolve("coil").deleteRecursively() } catch (ex: Exception) {}
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil").toOkioPath())
                        .maxSizeBytes((cacheSize) * 1024 * 1024L)
                        .build()
                }
            )
            .build()
    }

    companion object {
        const val CRASH_LOG_FILE = "last_crash.txt"
        private const val STARTUP_CRASH_WINDOW_MS = 15_000L

        lateinit var instance: App
            private set

        fun forgetAccount(context: Context) {
            runBlocking {
                try {
                    context.dataStore.edit { settings ->
                        settings.remove(InnerTubeCookieKey)
                        settings.remove(VisitorDataKey)
                        settings.remove(DataSyncIdKey)
                        settings.remove(AccountNameKey)
                        settings.remove(AccountEmailKey)
                        settings.remove(AccountChannelHandleKey)
                    }
                } catch (e: Exception) {}
            }
        }
    }
}
