package com.example

import android.content.Context
import androidx.camera.core.CameraXConfig
import androidx.camera.camera2.Camera2Config
import androidx.camera.lifecycle.ProcessCameraProvider
import android.util.Log

object CameraInitializer {
    private var initialized = false

    @androidx.annotation.OptIn(androidx.camera.lifecycle.ExperimentalCameraProviderConfiguration::class)
    fun initAndGetProvider(context: Context): com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> {
        if (!initialized) {
            try {
                ProcessCameraProvider.configureInstance(
                    CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
                        .setMinimumLoggingLevel(Log.ERROR)
                        .build()
                )
                initialized = true
            } catch (e: Exception) {
                Log.e("CameraInitializer", "Failed to configure CameraX", e)
                initialized = true // Don't try again if it fails
            }
        }
        return ProcessCameraProvider.getInstance(context)
    }
}
