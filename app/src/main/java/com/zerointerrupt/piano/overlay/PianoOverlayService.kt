package com.zerointerrupt.piano.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.zerointerrupt.piano.R
import com.zerointerrupt.piano.model.CalibrationGrid
import com.zerointerrupt.piano.model.PianoScore
import com.zerointerrupt.piano.model.PianoSettings
import com.zerointerrupt.piano.player.OverlayMode
import com.zerointerrupt.piano.player.PianoPlayerEngine
import com.zerointerrupt.piano.player.PlaybackSnapshot
import com.zerointerrupt.piano.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PianoOverlayService : Service() {

    private data class OverlayUiState(
        val scores: List<PianoScore>,
        val selectedScore: PianoScore?,
        val snapshot: PlaybackSnapshot,
        val settings: PianoSettings
    )

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var playerBarView: FloatingPlayerBarView? = null
    private var playerBarLp: WindowManager.LayoutParams? = null

    private var calibrationView: CalibrationGridView? = null
    private var calibrationLp: WindowManager.LayoutParams? = null

    private var currentPalette: OverlayMd3Palette? = null
    private var stateCollectJob: Job? = null
    private var restoreTouchRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        PianoPlayerEngine.initialize(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()

        stateCollectJob = serviceScope.launch {
            combine(
                PianoPlayerEngine.scores,
                PianoPlayerEngine.selectedScore,
                PianoPlayerEngine.playback,
                PianoPlayerEngine.settings
            ) { scores, selected, snapshot, settings ->
                OverlayUiState(scores, selected, snapshot, settings)
            }.collect { ui ->
                val nextPalette = resolveCurrentPalette(ui.settings)
                if (nextPalette != currentPalette) {
                    currentPalette = nextPalette
                    playerBarView?.applyPalette(nextPalette)
                    calibrationView?.applyPalette(nextPalette)
                }
                calibrationView?.setKeyboardMode(ui.settings.keyboardMode)
                playerBarView?.updateState(ui.scores, ui.selectedScore, ui.snapshot, ui.settings.keyboardMode)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALL -> {
                PianoPlayerEngine.stop()
                PianoPlayerEngine.setOverlayMode(OverlayMode.OFF)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_CALIBRATION -> {
                showCalibrationOverlay()
            }
            ACTION_SHOW_PLAYER -> {
                showPlayerBarOverlay()
            }
            else -> {
                showPlayerBarOverlay()
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val nextPalette = resolveCurrentPalette()
        if (nextPalette != currentPalette) {
            currentPalette = nextPalette
            playerBarView?.applyPalette(nextPalette)
            calibrationView?.applyPalette(nextPalette)
        }
        clampPlayerBarIntoBounds()
        calibrationView?.invalidate()
    }

    private fun resolveCurrentPalette(
        settings: PianoSettings = PianoPlayerEngine.settings.value
    ): OverlayMd3Palette = OverlayMd3Palette.resolve(this, settings)

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        stateCollectJob?.cancel()
        removeCalibrationOverlay()
        removePlayerBarOverlay()
        PianoPlayerEngine.setOverlayMode(OverlayMode.OFF)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(ch)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PianoOverlayService::class.java).apply { action = ACTION_STOP_ALL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭悬浮窗", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    private fun createOverlayParams(
        width: Int,
        height: Int,
        fullscreenLayout: Boolean
    ): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (fullscreenLayout) {
            flags = flags or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun showPlayerBarOverlay() {
        removeCalibrationOverlay()
        if (playerBarView != null) {
            PianoPlayerEngine.setOverlayMode(OverlayMode.PLAYER_BAR)
            return
        }
        val palette = resolveCurrentPalette().also { currentPalette = it }
        val (dispW, dispH) = PianoPlayerEngine.getDisplayPixels()
        val lp = createOverlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            fullscreenLayout = false
        ).apply {
            x = ((dispW * 0.5f) - 440).toInt().coerceAtLeast(16)
            y = (dispH * 0.08f).toInt().coerceAtLeast(24)
        }
        val bar = FloatingPlayerBarView(
            context = this,
            initialPalette = palette,
            onDragMove = { dx, dy ->
                val curLp = playerBarLp ?: return@FloatingPlayerBarView
                val curView = playerBarView ?: return@FloatingPlayerBarView
                val (w, h) = PianoPlayerEngine.getDisplayPixels()
                curLp.x = (curLp.x + dx).coerceIn(0, (w - curView.width).coerceAtLeast(0))
                curLp.y = (curLp.y + dy).coerceIn(0, (h - curView.height).coerceAtLeast(0))
                try {
                    windowManager.updateViewLayout(curView, curLp)
                } catch (_: Throwable) {
                }
            },
            onTogglePlayPause = { PianoPlayerEngine.togglePlayPause() },
            onStop = { PianoPlayerEngine.stop() },
            onAdjustSpeed = { sp -> PianoPlayerEngine.setSpeed(sp) },
            onSelectScore = { scoreId -> PianoPlayerEngine.selectScore(scoreId) },
            onOpenCalibration = { showCalibrationOverlay() },
            onCloseOverlay = {
                PianoPlayerEngine.stop()
                stopSelf()
            },
            onSizeChangedHint = {
                mainHandler.post { clampPlayerBarIntoBounds() }
            },
            onToggleKeyboardMode = {
                val cur = PianoPlayerEngine.settings.value.keyboardMode
                val next = if (cur == com.zerointerrupt.piano.model.KeyboardMode.KEYS_36) {
                    com.zerointerrupt.piano.model.KeyboardMode.KEYS_21
                } else {
                    com.zerointerrupt.piano.model.KeyboardMode.KEYS_36
                }
                PianoPlayerEngine.setKeyboardMode(next)
            }
        )
        bar.updateState(
            PianoPlayerEngine.scores.value,
            PianoPlayerEngine.selectedScore.value,
            PianoPlayerEngine.playback.value,
            PianoPlayerEngine.settings.value.keyboardMode
        )
        windowManager.addView(bar, lp)
        playerBarView = bar
        playerBarLp = lp
        PianoPlayerEngine.setOverlayMode(OverlayMode.PLAYER_BAR)
    }

    private fun showCalibrationOverlay() {
        removePlayerBarOverlay()
        if (calibrationView != null) {
            PianoPlayerEngine.setOverlayMode(OverlayMode.CALIBRATION)
            return
        }
        val palette = resolveCurrentPalette().also { currentPalette = it }
        val lp = createOverlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            fullscreenLayout = true
        )
        val view = CalibrationGridView(
            context = this,
            initialGrid = PianoPlayerEngine.calibration.value,
            initialPalette = palette,
            initialKeyboardMode = PianoPlayerEngine.settings.value.keyboardMode,
            onAuditionKey = { keyId, liveGrid ->
                triggerAuditionTapPassThrough(keyId, liveGrid)
            },
            onSaveGrid = { saved ->
                PianoPlayerEngine.saveCalibration(saved)
                Toast.makeText(this, "3×7 校准坐标已保存", Toast.LENGTH_SHORT).show()
                showPlayerBarOverlay()
            },
            onResetGrid = {
                val def = CalibrationGrid()
                PianoPlayerEngine.saveCalibration(def)
                Toast.makeText(this, "已重置为默认 3×7 布局", Toast.LENGTH_SHORT).show()
                def
            },
            onCancel = {
                showPlayerBarOverlay()
            }
        )
        windowManager.addView(view, lp)
        calibrationView = view
        calibrationLp = lp
        PianoPlayerEngine.setOverlayMode(OverlayMode.CALIBRATION)
    }

    /**
     * Temporarily marks CalibrationGridView as FLAG_NOT_TOUCHABLE so the injected uinput tap
     * passes straight through to the game window underneath instead of hitting this overlay.
     */
    private fun triggerAuditionTapPassThrough(keyId: String, liveGrid: CalibrationGrid) {
        val view = calibrationView ?: return
        val lp = calibrationLp ?: return
        restoreTouchRunnable?.let { mainHandler.removeCallbacks(it) }

        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(view, lp)
        } catch (_: Throwable) {
        }

        mainHandler.postDelayed({
            PianoPlayerEngine.testTapKey(keyId, liveGrid) { res ->
                if (res.isFailure) {
                    mainHandler.post {
                        Toast.makeText(
                            this,
                            res.exceptionOrNull()?.message ?: "试音失败，请检查连点器 RPC",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }, 24L)

        val restore = Runnable {
            val v = calibrationView ?: return@Runnable
            val curLp = calibrationLp ?: return@Runnable
            curLp.flags = curLp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            try {
                windowManager.updateViewLayout(v, curLp)
            } catch (_: Throwable) {
            }
        }
        restoreTouchRunnable = restore
        mainHandler.postDelayed(restore, 190L)
    }

    private fun clampPlayerBarIntoBounds() {
        val view = playerBarView ?: return
        val lp = playerBarLp ?: return
        val (w, h) = PianoPlayerEngine.getDisplayPixels()
        val vw = if (view.width > 0) view.width else 360
        val vh = if (view.height > 0) view.height else 140
        lp.x = lp.x.coerceIn(0, (w - vw).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (h - vh).coerceAtLeast(0))
        try {
            windowManager.updateViewLayout(view, lp)
        } catch (_: Throwable) {
        }
    }

    private fun removePlayerBarOverlay() {
        playerBarView?.let { v ->
            try {
                windowManager.removeViewImmediate(v)
            } catch (_: Throwable) {
            }
        }
        playerBarView = null
        playerBarLp = null
    }

    private fun removeCalibrationOverlay() {
        restoreTouchRunnable?.let { mainHandler.removeCallbacks(it) }
        restoreTouchRunnable = null
        calibrationView?.let { v ->
            try {
                windowManager.removeViewImmediate(v)
            } catch (_: Throwable) {
            }
        }
        calibrationView = null
        calibrationLp = null
    }

    companion object {
        private const val CHANNEL_ID = "zero_interrupt_piano_overlay"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_SHOW_PLAYER = "com.zerointerrupt.piano.SHOW_PLAYER"
        const val ACTION_SHOW_CALIBRATION = "com.zerointerrupt.piano.SHOW_CALIBRATION"
        const val ACTION_STOP_ALL = "com.zerointerrupt.piano.STOP_ALL"
    }
}
