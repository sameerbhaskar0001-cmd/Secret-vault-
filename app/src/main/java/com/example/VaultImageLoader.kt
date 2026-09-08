package com.example

import android.content.Context
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Shared, singleton ImageLoader instance for the Secret Vault application.
 * Reusing a single ImageLoader instance with VideoFrameDecoder prevents
 * thread pool duplication, memory churn, and GC pauses during grid scrolling.
 */
object VaultImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.applicationContext.cacheDir.resolve("image_cache"))
                        .maxSizePercent(0.02)
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(true)
                .build()
                .also { instance = it }
        }
    }
}
