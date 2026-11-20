/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.jay.m3play.di

import timber.log.Timber
import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.jay.m3play.constants.MaxSongCacheSizeKey
import com.jay.m3play.db.InternalDatabase
import com.jay.m3play.db.MusicDatabase
import com.jay.m3play.utils.dataStore
import com.jay.m3play.utils.get
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MusicDatabase = InternalDatabase.newInstance(context)

    @Singleton
    @Provides
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider {
        return try {
            val provider = StandaloneDatabaseProvider(context)
            // Force open to check for SQLite database corruption early
            provider.writableDatabase 
            provider
        } catch (e: Exception) {
            Timber.tag("AppModule").e(e, "%s", "ExoPlayer internal database corrupted. Deleting...")
            try {
                // Yehi tha wo hidden villain jo crash karwa raha tha!
                context.deleteDatabase("exoplayer_internal.db")
            } catch (ex: Exception) {
                Timber.tag("AppModule").e(ex, "%s", "Failed to delete corrupted DB")
            }
            StandaloneDatabaseProvider(context)
        }
    }

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        val cacheDir = context.filesDir.resolve("exoplayer")
        
        fun createCache() = SimpleCache(
            cacheDir,
            when (val cacheSize = try { context.dataStore[MaxSongCacheSizeKey] ?: 1024 } catch(e: Exception) { 1024 }) {
                -1 -> NoOpCacheEvictor()
                else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
            },
            databaseProvider,
        )
        
        return try {
            val cache = createCache()
            cache.release()
            createCache()
        } catch (e: Exception) {
            Timber.tag("AppModule").e(e, "%s", "Player cache corrupted, creating a new one")
            cacheDir.deleteRecursively()
            try {
                context.deleteDatabase("exoplayer_internal.db")
            } catch (ex: Exception) {}
            
            try {
                createCache()
            } catch (e2: Exception) {
                // Absolute fallback to prevent splash screen crash
                val tempDir = context.filesDir.resolve("exoplayer_fallback_${System.currentTimeMillis()}")
                SimpleCache(tempDir, NoOpCacheEvictor(), databaseProvider)
            }
        }
    }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        val cacheDir = context.filesDir.resolve("download")
        
        fun createCache() = SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
        
        return try {
            val cache = createCache()
            cache.release()
            createCache()
        } catch (e: Exception) {
            Timber.tag("AppModule").e(e, "%s", "Download cache corrupted, creating a new one")
            cacheDir.deleteRecursively()
            try {
                createCache()
            } catch (e2: Exception) {
                val tempDir = context.filesDir.resolve("download_fallback_${System.currentTimeMillis()}")
                SimpleCache(tempDir, NoOpCacheEvictor(), databaseProvider)
            }
        }
    }
}
