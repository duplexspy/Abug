package com.frameforge.ai

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FrameForgeApplication : Application() {
    companion object {
        const val TAG = "FrameForgeApp"
        lateinit var instance: FrameForgeApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "FrameForge Application Initialized. Standalone Offline AI Video Engine ready.")

        // Asynchronously ensure bundled AI models are unpacked and ready
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.frameforge.ai.model_manager.ModelManager(this@FrameForgeApplication).unpackAllBundledModels()
                Log.i(TAG, "All bundled AI super-resolution and RIFE FlowNet models verified.")
            } catch (e: Exception) {
                Log.w(TAG, "Background model unpack: ${e.message}")
            }
        }
    }
}
