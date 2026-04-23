package com.moonbench.bifrost.services

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder

class VideoLiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return VideoWallpaperEngine()
    }

    inner class VideoWallpaperEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val prefs = getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        private var mediaPlayer: MediaPlayer? = null
        private var isVisible = false
        private var isPlayerPrepared = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(this)
            if (!isPreview) {
                prefs.edit().putBoolean("live_wallpaper_is_applied", true).commit()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible && !isPreview) {
                prefs.edit().putBoolean("live_wallpaper_is_applied", true).commit()
            }
            if (visible) {
                if (mediaPlayer == null) {
                    startPlaybackIfPossible()
                    return
                }
                if (isPlayerPrepared) {
                    runCatching {
                        if (mediaPlayer?.isPlaying == false) {
                            mediaPlayer?.start()
                        }
                    }
                }
            } else {
                if (isPlayerPrepared) {
                    runCatching {
                        if (mediaPlayer?.isPlaying == true) {
                            mediaPlayer?.pause()
                        }
                    }
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startPlaybackIfPossible()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            releasePlayer()
        }

        override fun onDestroy() {
            super.onDestroy()
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            releasePlayer()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key?.startsWith("live_wallpaper_") != true) return
            if (isVisible) {
                startPlaybackIfPossible(forceRecreate = true)
            }
        }

        private fun startPlaybackIfPossible(forceRecreate: Boolean = false) {
            val holder = surfaceHolder ?: return
            val surface = holder.surface ?: return
            if (!surface.isValid) return
            val videoUri = LiveWallpaperSettingsManager.getVideoUri(prefs) ?: return

            if (mediaPlayer != null && !forceRecreate) {
                if (isVisible && isPlayerPrepared) {
                    runCatching {
                        if (mediaPlayer?.isPlaying == false) {
                            mediaPlayer?.start()
                        }
                    }
                }
                return
            }

            releasePlayer()

            val fps = LiveWallpaperSettingsManager.getTargetFps(prefs)
            val mode = LiveWallpaperSettingsManager.getPerformanceMode(prefs)
            applyFrameRate(holder, fps, mode)

            mediaPlayer = MediaPlayer().apply {
                isPlayerPrepared = false
                isLooping = true
                setVolume(0f, 0f)
                // `setDisplay` calls `setKeepScreenOn` on SurfaceHolder, unsupported for wallpapers.
                setSurface(surface)
                setScreenOnWhilePlaying(false)

                setOnPreparedListener {
                    isPlayerPrepared = true
                    runCatching {
                        it.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    }.onFailure { scalingError ->
                        Log.w(TAG, "Video scaling mode not supported on this device", scalingError)
                    }
                    if (isVisible) {
                        it.start()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    isPlayerPrepared = false
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    releasePlayer()
                    true
                }

                try {
                    setDataSource(this@VideoLiveWallpaperService, videoUri)
                    prepareAsync()
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to load wallpaper video", e)
                    releasePlayer()
                }
            }
        }

        private fun applyFrameRate(
            holder: SurfaceHolder,
            fps: Int,
            mode: LiveWallpaperSettingsManager.PerformanceMode
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

            val strategy = when (mode) {
                LiveWallpaperSettingsManager.PerformanceMode.PERFORMANCE -> {
                    Surface.CHANGE_FRAME_RATE_ALWAYS
                }

                LiveWallpaperSettingsManager.PerformanceMode.ECO,
                LiveWallpaperSettingsManager.PerformanceMode.BALANCED -> {
                    Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                }
            }

            runCatching {
                holder.surface.setFrameRate(
                    fps.toFloat(),
                    Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                    strategy
                )
            }
        }

        private fun releasePlayer() {
            val player = mediaPlayer ?: return
            mediaPlayer = null
            isPlayerPrepared = false
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            runCatching { player.reset() }
            player.release()
        }
    }

    companion object {
        private const val TAG = "VideoWallpaper"
    }
}

