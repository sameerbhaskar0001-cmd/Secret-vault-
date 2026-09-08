package com.example

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object GeckoEngine {
    private var runtime: GeckoRuntime? = null

    fun getRuntime(context: Context): GeckoRuntime {
        synchronized(this) {
            if (runtime == null) {
                val settings = GeckoRuntimeSettings.Builder()
                    .consoleOutput(false)
                    .crashHandler(null)
                    .build()
                runtime = GeckoRuntime.create(context.applicationContext, settings)
            }
            return runtime!!
        }
    }
}
