package com.zerointerrupt.piano.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.zerointerrupt.piano.model.KeyboardMode
import com.zerointerrupt.piano.model.PianoSettings
import com.zerointerrupt.piano.overlay.PianoOverlayService
import com.zerointerrupt.piano.player.OverlayMode
import com.zerointerrupt.piano.player.PianoPlayerEngine
import com.zerointerrupt.piano.ui.theme.ZeroInterruptPianoTheme
import com.zerointerrupt.piano.ui.theme.resolveIsDarkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var overlayPermGranted by mutableStateOf(false)
    private lateinit var snackbarHostState: SnackbarHostState
    private var pendingImportMode: KeyboardMode = KeyboardMode.KEYS_21

    private val overlayPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayPermGranted = Settings.canDrawOverlays(this)
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val openDocLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handleImportUri(uri, pendingImportMode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PianoPlayerEngine.initialize(this)
        overlayPermGranted = Settings.canDrawOverlays(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val scores by PianoPlayerEngine.scores.collectAsState()
            val selectedScore by PianoPlayerEngine.selectedScore.collectAsState()
            val settings by PianoPlayerEngine.settings.collectAsState()
            val calibration by PianoPlayerEngine.calibration.collectAsState()
            val playback by PianoPlayerEngine.playback.collectAsState()
            val probe by PianoPlayerEngine.probeState.collectAsState()
            val overlayMode by PianoPlayerEngine.overlayMode.collectAsState()

            val scope = rememberCoroutineScope()
            snackbarHostState = remember { SnackbarHostState() }

            val callbacks = remember(scope) {
                PianoUiCallbacks(
                    onSelectScore = { id -> PianoPlayerEngine.selectScore(id) },
                    onDeleteScore = { id ->
                        PianoPlayerEngine.deleteImportedScore(id)
                        scope.launch { snackbarHostState.showSnackbar("已删除导入乐谱") }
                    },
                    onImportFile = { targetMode ->
                        pendingImportMode = targetMode
                        openDocLauncher.launch(arrayOf("*/*"))
                    },
                    onTogglePlayPause = { PianoPlayerEngine.togglePlayPause() },
                    onStopPlayback = { PianoPlayerEngine.stop() },
                    onSeek = { pos -> PianoPlayerEngine.seekTo(pos) },
                    onSpeedChange = { sp -> PianoPlayerEngine.setSpeed(sp) },
                    onOpenCalibrationOverlay = { launchOverlayAction(PianoOverlayService.ACTION_SHOW_CALIBRATION) },
                    onTogglePlayerOverlay = {
                        if (PianoPlayerEngine.overlayMode.value == OverlayMode.OFF) {
                            launchOverlayAction(PianoOverlayService.ACTION_SHOW_PLAYER)
                        } else {
                            startService(
                                Intent(this, PianoOverlayService::class.java).apply {
                                    action = PianoOverlayService.ACTION_STOP_ALL
                                }
                            )
                        }
                    },
                    onExportBundleJson = {
                        val summary = PianoPlayerEngine.exportSelectedScoreBundle()
                        if (summary == null) {
                            scope.launch { snackbarHostState.showSnackbar("请先选择一首乐谱") }
                        } else {
                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("ZeroInterruptPiano Bundle", summary.json))
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "已复制连点器 Bundle JSON（${summary.stepCount} 步 · ${summary.subPresetCount} 个复用和弦子方案），可在连点器「批量导入 JSON」粘贴"
                                )
                            }
                        }
                    },
                    onProbeRpc = {
                        PianoPlayerEngine.probeRpcAsync { res ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (res.ok) "RPC 正常 (${res.latencyMs}ms)" else res.error
                                )
                            }
                        }
                    },
                    onUpdateToken = { token ->
                        PianoPlayerEngine.updateSettings { it.copy(rpcToken = token.trim()) }
                    },
                    onPasteToken = {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val pasted = cm?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
                        if (pasted.isNotEmpty()) {
                            PianoPlayerEngine.updateSettings { it.copy(rpcToken = pasted) }
                            PianoPlayerEngine.probeRpcAsync()
                            scope.launch { snackbarHostState.showSnackbar("已粘贴 Token 并开始探测") }
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("剪贴板为空") }
                        }
                    },
                    onOpenClickerApp = {
                        val launchIntent = packageManager.getLaunchIntentForPackage("com.zerointerrupt.clicker")
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("未检测到 ZeroInterruptClicker") }
                        }
                    },
                    onMinPressChange = { v -> PianoPlayerEngine.updateSettings { it.copy(minPressMs = v) } },
                    onMinGapChange = { v -> PianoPlayerEngine.updateSettings { it.copy(minRestrikeGapMs = v) } },
                    onMaxPolyChange = { v -> PianoPlayerEngine.updateSettings { it.copy(maxPolyphony = v) } },
                    onMaxHoldChange = { v -> PianoPlayerEngine.updateSettings { it.copy(maxTapHoldMs = v) } },
                    onResetDefaults = {
                        PianoPlayerEngine.updateSettings {
                            it.copy(
                                minPressMs = PianoSettings.DEFAULT_MIN_PRESS_MS,
                                minRestrikeGapMs = PianoSettings.DEFAULT_MIN_RESTRIKE_GAP_MS,
                                maxPolyphony = PianoSettings.DEFAULT_MAX_POLYPHONY,
                                maxTapHoldMs = PianoSettings.DEFAULT_MAX_TAP_HOLD_MS
                            )
                        }
                        scope.launch { snackbarHostState.showSnackbar("已恢复 M0 实测推荐默认参数") }
                    },
                    onResetCalibration = {
                        PianoPlayerEngine.resetCalibration()
                        scope.launch { snackbarHostState.showSnackbar("已重置为 M0 默认 3×7 布局") }
                    },
                    onThemeModeChange = { mode ->
                        PianoPlayerEngine.updateSettings { it.copy(themeMode = mode) }
                    },
                    onDynamicColorChange = { enabled ->
                        PianoPlayerEngine.updateSettings { it.copy(dynamicColor = enabled) }
                    },
                    onKeyboardModeChange = { mode ->
                        PianoPlayerEngine.setKeyboardMode(mode)
                        scope.launch { snackbarHostState.showSnackbar("已切换至 ${mode.label} 模式") }
                    }
                )
            }

            val darkTheme = resolveIsDarkTheme(settings.themeMode)
            SideEffect {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            ZeroInterruptPianoTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor
            ) {
                MainScreen(
                    scores = scores,
                    selectedScore = selectedScore,
                    settings = settings,
                    calibration = calibration,
                    playback = playback,
                    probe = probe,
                    overlayMode = overlayMode,
                    overlayPermGranted = overlayPermGranted,
                    snackbarHostState = snackbarHostState,
                    callbacks = callbacks
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayPermGranted = Settings.canDrawOverlays(this)
        if (PianoPlayerEngine.settings.value.rpcToken.isNotBlank()) {
            PianoPlayerEngine.probeRpcAsync()
        }
    }

    private fun launchOverlayAction(actionName: String) {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, PianoOverlayService::class.java).apply { action = actionName }
        )
    }

    private fun handleImportUri(uri: Uri, mode: KeyboardMode) {
        val name = resolveFileName(uri)
        val stream = contentResolver.openInputStream(uri)
        if (stream == null) {
            if (::snackbarHostState.isInitialized) {
                lifecycleScope.launch { snackbarHostState.showSnackbar("无法读取所选文件") }
            }
            return
        }
        PianoPlayerEngine.importScore(name, stream, mode) { res ->
            runOnUiThread {
                val msg = if (res.isSuccess) {
                    val s = res.getOrThrow()
                    val dirName = if (mode == KeyboardMode.KEYS_36) "36_keys" else "21_keys"
                    "导入成功 [${mode.label}]：「${s.title}」已存入 $dirName 目录，契合度 ${s.diatonicPct}% (${s.fitTierLabel})"
                } else {
                    "导入失败：${res.exceptionOrNull()?.message ?: "格式错误"}"
                }
                if (::snackbarHostState.isInitialized) {
                    lifecycleScope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            }
        }
    }

    private fun resolveFileName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val n = c.getString(idx)
                    if (!n.isNullOrBlank()) return n
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "score.mid"
    }
}
