package com.moonbench.bifrost.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.abs

class MicrophoneAudioAnalyzer(
    private val context: Context,
    private val performanceProfile: PerformanceProfile,
    private val callback: (Float) -> Unit
) {
    companion object {
        private const val TAG = "InternalAudioAnalyzer"
    }

    private var visualizer: Visualizer? = null

    @Volatile
    private var running = false

    fun start() {
        if (running) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callback(0f)
            return
        }

        try {
            // Session 0 = mix de sortie global (musique/SFX), pas la voix micro.
            val internalOutputVisualizer = Visualizer(0)
            val captureSize = Visualizer.getCaptureSizeRange()[1]
            internalOutputVisualizer.captureSize = captureSize

            val preferredRate = when {
                performanceProfile.intervalMs <= 16L -> Visualizer.getMaxCaptureRate()
                performanceProfile.intervalMs <= 32L -> Visualizer.getMaxCaptureRate() / 2
                else -> Visualizer.getMaxCaptureRate() / 4
            }.coerceAtLeast(1000)

            internalOutputVisualizer.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer,
                        waveform: ByteArray,
                        samplingRate: Int
                    ) {
                        var max = 0
                        var i = 0
                        while (i < waveform.size) {
                            val centered = (waveform[i].toInt() and 0xFF) - 128
                            val level = abs(centered)
                            if (level > max) max = level
                            i++
                        }
                        val intensity = (max / 128f).coerceIn(0f, 1f)
                        callback(intensity)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer,
                        fft: ByteArray,
                        samplingRate: Int
                    ) = Unit
                },
                preferredRate,
                true,
                false
            )

            internalOutputVisualizer.enabled = true
            visualizer = internalOutputVisualizer
            running = true
        } catch (e: Exception) {
            Log.w(TAG, "Internal audio analyzer failed to start", e)
            running = false
            cleanup()
        }
    }

    fun stop() {
        running = false
        cleanup()
    }

    private fun cleanup() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
    }
}

