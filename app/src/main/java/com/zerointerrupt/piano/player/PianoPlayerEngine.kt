package com.zerointerrupt.piano.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.zerointerrupt.piano.converter.ClickBundleExporter
import com.zerointerrupt.piano.converter.ExportBundleSummary
import com.zerointerrupt.piano.converter.ScoreConverter
import com.zerointerrupt.piano.model.CalibrationGrid
import com.zerointerrupt.piano.model.KeyboardMode
import com.zerointerrupt.piano.model.PianoConfigStore
import com.zerointerrupt.piano.model.PianoNoteEvent
import com.zerointerrupt.piano.model.PianoScore
import com.zerointerrupt.piano.model.PianoSettings
import com.zerointerrupt.piano.rpc.RpcClient
import com.zerointerrupt.piano.rpc.RpcConnectionProbe
import com.zerointerrupt.piano.rpc.RpcTapCmd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToLong

enum class PlaybackStatus {
    IDLE,
    PLAYING,
    PAUSED,
    ERROR
}

enum class OverlayMode {
    OFF,
    PLAYER_BAR,
    CALIBRATION
}

data class PlaybackSnapshot(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val eventIndex: Int = 0,
    val totalEvents: Int = 0,
    val activeKeys: Set<String> = emptySet(),
    val message: String = ""
)

object PianoPlayerEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rpcClient = RpcClient()

    private lateinit var appContext: Context
    private var playJob: Job? = null

    private val _scores = MutableStateFlow<List<PianoScore>>(emptyList())
    val scores: StateFlow<List<PianoScore>> = _scores.asStateFlow()

    private val _selectedScore = MutableStateFlow<PianoScore?>(null)
    val selectedScore: StateFlow<PianoScore?> = _selectedScore.asStateFlow()

    private val _settings = MutableStateFlow(PianoSettings())
    val settings: StateFlow<PianoSettings> = _settings.asStateFlow()

    private val _calibration = MutableStateFlow(CalibrationGrid())
    val calibration: StateFlow<CalibrationGrid> = _calibration.asStateFlow()

    private val _probeState = MutableStateFlow<RpcConnectionProbe?>(null)
    val probeState: StateFlow<RpcConnectionProbe?> = _probeState.asStateFlow()

    private val _overlayMode = MutableStateFlow(OverlayMode.OFF)
    val overlayMode: StateFlow<OverlayMode> = _overlayMode.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackSnapshot())
    val playback: StateFlow<PlaybackSnapshot> = _playback.asStateFlow()

    @Volatile
    private var liveSpeed: Float = 1.0f
    @Volatile
    private var seekRequestMs: Long? = null

    fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        PianoConfigStore.ensureMidiDirectories(appContext)
        val loadedSettings = PianoConfigStore.loadSettings(appContext)
        val loadedCal = PianoConfigStore.loadCalibration(appContext)
        val loadedScores = PianoConfigStore.loadAllScores(appContext, loadedSettings.keyboardMode)

        _settings.value = loadedSettings
        liveSpeed = loadedSettings.speedMultiplier
        _calibration.value = loadedCal
        _scores.value = loadedScores

        val match = loadedScores.firstOrNull { it.id == loadedSettings.selectedScoreId }
            ?: loadedScores.firstOrNull()
        selectScoreInternal(match)
    }

    fun selectScore(scoreId: String) {
        val match = _scores.value.firstOrNull { it.id == scoreId } ?: return
        stop()
        selectScoreInternal(match)
        updateSettings { it.copy(selectedScoreId = match.id) }
    }

    private fun selectScoreInternal(score: PianoScore?) {
        _selectedScore.value = score
        _playback.value = PlaybackSnapshot(
            status = PlaybackStatus.IDLE,
            positionMs = 0L,
            durationMs = score?.durationMs ?: 0L,
            speed = liveSpeed,
            eventIndex = 0,
            totalEvents = score?.events?.size ?: 0
        )
    }

    fun updateSettings(mutate: (PianoSettings) -> PianoSettings) {
        val next = mutate(_settings.value)
        _settings.value = next
        liveSpeed = next.speedMultiplier
        if (::appContext.isInitialized) {
            PianoConfigStore.saveSettings(appContext, next)
        }
        _playback.value = _playback.value.copy(speed = next.speedMultiplier)
    }

    fun setSpeed(speed: Float) {
        val clamped = ((speed.coerceIn(PianoSettings.MIN_SPEED, PianoSettings.MAX_SPEED) * 20f).toInt() / 20f)
        updateSettings { it.copy(speedMultiplier = clamped) }
    }

    fun setKeyboardMode(mode: KeyboardMode) {
        if (_settings.value.keyboardMode == mode) return
        stop()
        updateSettings { it.copy(keyboardMode = mode) }
        if (::appContext.isInitialized) {
            val reloaded = PianoConfigStore.loadAllScores(appContext, mode)
            _scores.value = reloaded
            val curId = _selectedScore.value?.id ?: _settings.value.selectedScoreId
            val match = reloaded.firstOrNull { it.id == curId } ?: reloaded.firstOrNull()
            selectScoreInternal(match)
        }
    }

    fun saveCalibration(grid: CalibrationGrid) {
        val clamped = grid.clamped()
        _calibration.value = clamped
        if (::appContext.isInitialized) {
            PianoConfigStore.saveCalibration(appContext, clamped)
        }
    }

    fun resetCalibration() {
        saveCalibration(CalibrationGrid())
    }

    fun setOverlayMode(mode: OverlayMode) {
        _overlayMode.value = mode
    }

    fun probeRpcAsync(onResult: ((RpcConnectionProbe) -> Unit)? = null) {
        val cfg = _settings.value
        scope.launch {
            val res = rpcClient.probe(cfg.rpcHost, cfg.rpcPort, cfg.rpcToken)
            _probeState.value = res
            onResult?.invoke(res)
        }
    }

    fun testTapKey(keyId: String, gridOverride: CalibrationGrid? = null, onDone: ((Result<Unit>) -> Unit)? = null) {
        val cfg = _settings.value
        val cal = gridOverride ?: _calibration.value
        val (w, h) = getDisplayPixels()
        val (px, py) = cal.toScreenPixel(keyId, w, h)
        scope.launch {
            val res = rpcClient.sendTapBatch(
                cfg.rpcHost,
                cfg.rpcPort,
                cfg.rpcToken,
                listOf(RpcTapCmd(px, py, max(cfg.minPressMs, 45L)))
            )
            onDone?.invoke(res)
        }
    }

    fun importScore(
        fileName: String,
        inputStream: InputStream,
        targetMode: KeyboardMode = _settings.value.keyboardMode,
        onDone: (Result<PianoScore>) -> Unit
    ) {
        scope.launch {
            try {
                val score = inputStream.use { ScoreConverter.importFromStream(fileName, it, targetMode) }
                PianoConfigStore.saveImportedScore(appContext, score, targetMode)
                if (_settings.value.keyboardMode != targetMode) {
                    updateSettings { it.copy(keyboardMode = targetMode) }
                }
                val updated = PianoConfigStore.loadAllScores(appContext, targetMode)
                _scores.value = updated
                stop()
                selectScoreInternal(score)
                updateSettings { it.copy(selectedScoreId = score.id) }
                onDone(Result.success(score))
            } catch (t: Throwable) {
                onDone(Result.failure(t))
            }
        }
    }

    fun deleteImportedScore(scoreId: String) {
        val currentMode = _settings.value.keyboardMode
        if (!PianoConfigStore.deleteImportedScore(appContext, scoreId, currentMode)) return
        val updated = PianoConfigStore.loadAllScores(appContext, currentMode)
        _scores.value = updated
        if (_selectedScore.value?.id == scoreId) {
            stop()
            val fallback = updated.firstOrNull()
            selectScoreInternal(fallback)
            if (fallback != null) {
                updateSettings { it.copy(selectedScoreId = fallback.id) }
            }
        }
    }

    fun exportSelectedScoreBundle(): ExportBundleSummary? {
        val score = _selectedScore.value ?: return null
        val (w, h) = getDisplayPixels()
        return ClickBundleExporter.exportBundle(
            score = score,
            grid = _calibration.value,
            settings = _settings.value,
            screenW = w,
            screenH = h,
            speedMultiplier = liveSpeed
        )
    }

    fun togglePlayPause() {
        when (_playback.value.status) {
            PlaybackStatus.PLAYING -> pause()
            PlaybackStatus.PAUSED -> resume()
            PlaybackStatus.IDLE, PlaybackStatus.ERROR -> startFrom(_playback.value.positionMs)
        }
    }

    fun pause() {
        if (_playback.value.status != PlaybackStatus.PLAYING) return
        playJob?.cancel()
        playJob = null
        _playback.value = _playback.value.copy(
            status = PlaybackStatus.PAUSED,
            activeKeys = emptySet(),
            message = "已暂停"
        )
    }

    fun resume() {
        if (_playback.value.status != PlaybackStatus.PAUSED) return
        startFrom(_playback.value.positionMs)
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        val score = _selectedScore.value
        _playback.value = PlaybackSnapshot(
            status = PlaybackStatus.IDLE,
            positionMs = 0L,
            durationMs = score?.durationMs ?: 0L,
            speed = liveSpeed,
            eventIndex = 0,
            totalEvents = score?.events?.size ?: 0,
            activeKeys = emptySet(),
            message = ""
        )
    }

    fun seekTo(positionMs: Long) {
        val score = _selectedScore.value ?: return
        val clamped = positionMs.coerceIn(0L, score.durationMs)
        if (_playback.value.status == PlaybackStatus.PLAYING) {
            seekRequestMs = clamped
        } else {
            val nextIdx = findEventIndexAt(score.events, clamped)
            _playback.value = _playback.value.copy(
                positionMs = clamped,
                eventIndex = nextIdx,
                activeKeys = emptySet()
            )
        }
    }

    private fun startFrom(startPosMs: Long) {
        val score = _selectedScore.value ?: return
        val cfg = _settings.value
        if (cfg.rpcToken.isBlank()) {
            _playback.value = _playback.value.copy(
                status = PlaybackStatus.ERROR,
                message = "请先在设置中填写连点器 Access Token"
            )
            return
        }

        playJob?.cancel()
        playJob = scope.launch {
            val probe = rpcClient.probe(cfg.rpcHost, cfg.rpcPort, cfg.rpcToken)
            _probeState.value = probe
            if (!probe.ok) {
                _playback.value = _playback.value.copy(
                    status = PlaybackStatus.ERROR,
                    message = probe.error
                )
                return@launch
            }
            if (!probe.shizukuConnected) {
                _playback.value = _playback.value.copy(
                    status = PlaybackStatus.ERROR,
                    message = "连点器 Shizuku 未连接，请先在连点器中完成授权与校准"
                )
                return@launch
            }

            val events = score.events
            if (events.isEmpty()) return@launch
            val nextSameKeyDelta = precomputeNextSameKeyScoreDelta(events)
            val startPos = if (startPosMs >= score.durationMs) 0L else startPosMs.coerceAtLeast(0L)
            var idx = findEventIndexAt(events, startPos)

            var songCursorMs = startPos.toDouble()
            var lastMonoNs = SystemClock.elapsedRealtimeNanos()
            val activeSlots = mutableListOf<Pair<String, Long>>() // (keyId, realReleaseMs)
            val lastReleaseRealMs = HashMap<String, Long>()

            _playback.value = _playback.value.copy(
                status = PlaybackStatus.PLAYING,
                positionMs = startPos,
                durationMs = score.durationMs,
                speed = liveSpeed,
                eventIndex = idx,
                totalEvents = events.size,
                message = ""
            )

            while (isActive && idx < events.size) {
                val pendingSeek = seekRequestMs
                if (pendingSeek != null) {
                    seekRequestMs = null
                    songCursorMs = pendingSeek.toDouble()
                    idx = findEventIndexAt(events, pendingSeek)
                    lastMonoNs = SystemClock.elapsedRealtimeNanos()
                    activeSlots.clear()
                    lastReleaseRealMs.clear()
                    continue
                }

                val nowNs = SystemClock.elapsedRealtimeNanos()
                val elapsedRealMs = max(0L, nowNs - lastMonoNs) / 1_000_000.0
                lastMonoNs = nowNs
                val curSpeed = liveSpeed.coerceIn(PianoSettings.MIN_SPEED, PianoSettings.MAX_SPEED)
                songCursorMs += elapsedRealMs * curSpeed

                val ev = events[idx]
                if (songCursorMs + 1.0 < ev.tMs) {
                    val nowReal = SystemClock.elapsedRealtime()
                    activeSlots.removeAll { it.second <= nowReal }
                    _playback.value = _playback.value.copy(
                        positionMs = songCursorMs.toLong().coerceIn(0L, score.durationMs),
                        speed = curSpeed,
                        eventIndex = idx,
                        activeKeys = activeSlots.map { it.first }.toSet()
                    )
                    val waitRealMs = ((ev.tMs - songCursorMs) / curSpeed).toLong()
                    val sleepMs = when {
                        waitRealMs > 18L -> 12L
                        waitRealMs > 3L -> waitRealMs - 2L
                        else -> 1L
                    }
                    Thread.sleep(sleepMs)
                    continue
                }

                // Dispatch all events due by current cursor (normally 1 event; avoids drift)
                val curCfg = _settings.value
                val cal = _calibration.value
                val (dispW, dispH) = getDisplayPixels()
                val minPress = curCfg.minPressMs.coerceAtLeast(PianoSettings.FLOOR_PRESS_MS)
                val minGap = curCfg.minRestrikeGapMs.coerceAtLeast(PianoSettings.FLOOR_RESTRIKE_GAP_MS)
                val maxPoly = curCfg.maxPolyphony.coerceIn(4, 14)

                while (idx < events.size && events[idx].tMs <= songCursorMs + 1.0) {
                    val curEv = events[idx]
                    val nextDeltaMap = nextSameKeyDelta[idx]
                    val nowReal = SystemClock.elapsedRealtime()
                    activeSlots.removeAll { it.second <= nowReal }

                    val batch = ArrayList<RpcTapCmd>(curEv.keys.size)
                    val dispatchedKeys = ArrayList<String>(curEv.keys.size)

                    for (k in curEv.keys) {
                        if (activeSlots.size + batch.size >= maxPoly) break
                        val prevRel = lastReleaseRealMs[k]
                        // Only drop if hardware gap is truly below safe unmerge threshold (prevents 2-3ms jitter false-drops)
                        val hardFloorGap = (minGap - 12L).coerceAtLeast(25L)
                        if (prevRel != null && nowReal < prevRel + hardFloorGap) {
                            continue
                        }
                        val baseRealDur = (curEv.durMs / curSpeed).roundToLong()
                            .coerceIn(minPress, curCfg.maxTapHoldMs)
                        val deltaScoreNext = nextDeltaMap[k]
                        val dur = if (deltaScoreNext != null) {
                            val maxAllowed = (deltaScoreNext / curSpeed).roundToLong() - (minGap + 6L)
                            minOf(baseRealDur, max(minPress, maxAllowed))
                        } else {
                            baseRealDur
                        }
                        val (px, py) = cal.toScreenPixel(k, dispW, dispH)
                        batch += RpcTapCmd(px, py, dur)
                        dispatchedKeys += k
                        val relReal = nowReal + dur
                        activeSlots += k to relReal
                        lastReleaseRealMs[k] = relReal
                    }

                    if (batch.isNotEmpty()) {
                        val sendRes = rpcClient.sendTapBatch(curCfg.rpcHost, curCfg.rpcPort, curCfg.rpcToken, batch)
                        if (sendRes.isFailure) {
                            _playback.value = _playback.value.copy(
                                status = PlaybackStatus.ERROR,
                                activeKeys = emptySet(),
                                message = sendRes.exceptionOrNull()?.message ?: "RPC 发送失败"
                            )
                            return@launch
                        }
                    }
                    idx++
                }

                val nowReal = SystemClock.elapsedRealtime()
                activeSlots.removeAll { it.second <= nowReal }
                _playback.value = _playback.value.copy(
                    positionMs = songCursorMs.toLong().coerceIn(0L, score.durationMs),
                    speed = curSpeed,
                    eventIndex = idx,
                    activeKeys = activeSlots.map { it.first }.toSet()
                )
            }

            if (isActive && idx >= events.size) {
                // Wait briefly for tail notes' visual indicator to settle, then return to IDLE
                Thread.sleep(160L)
                _playback.value = _playback.value.copy(
                    status = PlaybackStatus.IDLE,
                    positionMs = 0L,
                    eventIndex = 0,
                    activeKeys = emptySet(),
                    message = "演奏完成"
                )
            }
        }
    }

    private fun precomputeNextSameKeyScoreDelta(events: List<PianoNoteEvent>): List<Map<String, Long>> {
        val result = Array<HashMap<String, Long>>(events.size) { HashMap() }
        val nextSeenByKey = HashMap<String, Long>()
        for (i in events.indices.reversed()) {
            val ev = events[i]
            val map = result[i]
            for (k in ev.keys) {
                val nextT = nextSeenByKey[k]
                if (nextT != null) {
                    map[k] = (nextT - ev.tMs).coerceAtLeast(0L)
                }
                nextSeenByKey[k] = ev.tMs
            }
        }
        return result.toList()
    }

    private fun findEventIndexAt(events: List<PianoNoteEvent>, posMs: Long): Int {
        var lo = 0
        var hi = events.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (events[mid].tMs < posMs) lo = mid + 1 else hi = mid
        }
        return lo
    }

    @Suppress("DEPRECATION")
    fun getDisplayPixels(): Pair<Int, Int> {
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            Pair(b.width().coerceAtLeast(1), b.height().coerceAtLeast(1))
        } else {
            val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
            val m = DisplayMetrics()
            display?.getRealMetrics(m)
            Pair(m.widthPixels.coerceAtLeast(1), m.heightPixels.coerceAtLeast(1))
        }
    }
}
