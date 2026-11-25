package com.zerointerrupt.piano.model

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PianoConfigStore {
    private const val PREF_NAME = "zero_interrupt_piano_prefs"
    private const val KEY_CAL_LEFT = "cal_left"
    private const val KEY_CAL_TOP = "cal_top"
    private const val KEY_CAL_RIGHT = "cal_right"
    private const val KEY_CAL_BOTTOM = "cal_bottom"
    private const val KEY_CAL_PROFILES_JSON = "cal_profiles_json"
    private const val KEY_ACTIVE_CAL_PROFILE_ID = "active_cal_profile_id"

    private const val KEY_RPC_HOST = "rpc_host"
    private const val KEY_RPC_PORT = "rpc_port"
    private const val KEY_RPC_TOKEN = "rpc_token"
    private const val KEY_SPEED = "speed_multiplier"
    private const val KEY_MIN_PRESS = "min_press_ms"
    private const val KEY_MIN_GAP = "min_restrike_gap_ms"
    private const val KEY_MAX_POLY = "max_polyphony"
    private const val KEY_MAX_HOLD = "max_tap_hold_ms"
    private const val KEY_SELECTED_SCORE = "selected_score_id"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_KEYBOARD_MODE = "keyboard_mode"

    private const val IMPORTED_DIR_ROOT = "imported_scores"
    const val SUBDIR_21 = "21_keys"
    const val SUBDIR_36 = "36_keys"

    fun getImportedDir(context: Context, mode: KeyboardMode): File {
        val sub = if (mode == KeyboardMode.KEYS_36) SUBDIR_36 else SUBDIR_21
        return File(File(context.filesDir, IMPORTED_DIR_ROOT), sub).apply { mkdirs() }
    }

    fun getExternalMidiDir(mode: KeyboardMode): File? {
        return try {
            val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val sub = if (mode == KeyboardMode.KEYS_36) SUBDIR_36 else SUBDIR_21
            File(File(dl, "ZeroInterruptPiano"), sub).apply { mkdirs() }
        } catch (_: Throwable) {
            null
        }
    }

    fun ensureMidiDirectories(context: Context) {
        getImportedDir(context, KeyboardMode.KEYS_21)
        getImportedDir(context, KeyboardMode.KEYS_36)
        try {
            val ext21 = getExternalMidiDir(KeyboardMode.KEYS_21)
            val ext36 = getExternalMidiDir(KeyboardMode.KEYS_36)
            if (ext21 != null && !File(ext21, "readme.txt").exists()) {
                File(ext21, "readme.txt").writeText("请将 21 键模式（3x7 无黑键）MIDI/MXL/JSON 琴谱放入此目录，可在 App 中导入。\n", Charsets.UTF_8)
            }
            if (ext36 != null && !File(ext36, "readme.txt").exists()) {
                File(ext36, "readme.txt").writeText("请将 36 键模式（含升降半音）MIDI/MXL/JSON 琴谱放入此目录，可在 App 中导入。\n", Charsets.UTF_8)
            }
        } catch (_: Throwable) {
        }
    }

    private fun migrateLegacyImportedScores(context: Context) {
        try {
            val root = File(context.filesDir, IMPORTED_DIR_ROOT)
            if (!root.exists()) return
            val legacyFiles = root.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return
            val dir21 = getImportedDir(context, KeyboardMode.KEYS_21)
            val dir36 = getImportedDir(context, KeyboardMode.KEYS_36)
            for (file in legacyFiles) {
                try {
                    val raw = file.readText(Charsets.UTF_8)
                    val is36 = raw.contains("\"targetKeyCount\":36") ||
                            raw.contains("S\"") || raw.contains("#\"")
                    val target = File(if (is36) dir36 else dir21, file.name)
                    file.renameTo(target)
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun loadCalibration(context: Context): CalibrationGrid {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return CalibrationGrid(
            leftNorm = p.getFloat(KEY_CAL_LEFT, CalibrationGrid.DEFAULT_LEFT_NORM),
            topNorm = p.getFloat(KEY_CAL_TOP, CalibrationGrid.DEFAULT_TOP_NORM),
            rightNorm = p.getFloat(KEY_CAL_RIGHT, CalibrationGrid.DEFAULT_RIGHT_NORM),
            bottomNorm = p.getFloat(KEY_CAL_BOTTOM, CalibrationGrid.DEFAULT_BOTTOM_NORM)
        ).clamped()
    }

    fun saveCalibration(context: Context, grid: CalibrationGrid) {
        val c = grid.clamped()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_CAL_LEFT, c.leftNorm)
            .putFloat(KEY_CAL_TOP, c.topNorm)
            .putFloat(KEY_CAL_RIGHT, c.rightNorm)
            .putFloat(KEY_CAL_BOTTOM, c.bottomNorm)
            .apply()
    }

    fun loadCalibrationProfiles(context: Context): List<CalibrationProfile> {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = p.getString(KEY_CAL_PROFILES_JSON, null)
        val parsed = mutableListOf<CalibrationProfile>()
        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    CalibrationProfile.fromJson(arr.optJSONObject(i))?.let { parsed += it }
                }
            } catch (_: Throwable) {
            }
        }
        if (parsed.isNotEmpty()) return parsed

        // Migrate legacy single CalibrationGrid into default named profile
        val legacyGrid = loadCalibration(context)
        val settingsMode = KeyboardMode.fromStorageName(p.getString(KEY_KEYBOARD_MODE, KeyboardMode.KEYS_21.name))
        val defaultProfile = CalibrationProfile(
            id = CalibrationProfile.DEFAULT_PROFILE_ID,
            name = CalibrationProfile.DEFAULT_PROFILE_NAME,
            keyboardMode = settingsMode,
            grid = legacyGrid
        )
        saveCalibrationProfiles(context, listOf(defaultProfile))
        saveActiveCalibrationProfileId(context, defaultProfile.id)
        return listOf(defaultProfile)
    }

    fun saveCalibrationProfiles(context: Context, profiles: List<CalibrationProfile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CAL_PROFILES_JSON, arr.toString())
            .apply()
    }

    fun loadActiveCalibrationProfileId(context: Context): String {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return p.getString(KEY_ACTIVE_CAL_PROFILE_ID, CalibrationProfile.DEFAULT_PROFILE_ID)
            .orEmpty()
            .ifBlank { CalibrationProfile.DEFAULT_PROFILE_ID }
    }

    fun saveActiveCalibrationProfileId(context: Context, profileId: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE_CAL_PROFILE_ID, profileId)
            .apply()
    }

    fun loadSettings(context: Context): PianoSettings {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return PianoSettings(
            rpcHost = p.getString(KEY_RPC_HOST, "127.0.0.1").orEmpty().ifBlank { "127.0.0.1" },
            rpcPort = p.getInt(KEY_RPC_PORT, 27183).coerceIn(1, 65535),
            rpcToken = p.getString(KEY_RPC_TOKEN, "").orEmpty(),
            speedMultiplier = p.getFloat(KEY_SPEED, 1.0f).coerceIn(PianoSettings.MIN_SPEED, PianoSettings.MAX_SPEED),
            minPressMs = p.getLong(KEY_MIN_PRESS, PianoSettings.DEFAULT_MIN_PRESS_MS)
                .coerceIn(PianoSettings.FLOOR_PRESS_MS, 150L),
            minRestrikeGapMs = p.getLong(KEY_MIN_GAP, PianoSettings.DEFAULT_MIN_RESTRIKE_GAP_MS)
                .coerceIn(PianoSettings.FLOOR_RESTRIKE_GAP_MS, 200L),
            maxPolyphony = p.getInt(KEY_MAX_POLY, PianoSettings.DEFAULT_MAX_POLYPHONY).coerceIn(4, 14),
            maxTapHoldMs = p.getLong(KEY_MAX_HOLD, PianoSettings.DEFAULT_MAX_TAP_HOLD_MS).coerceIn(100L, 2000L),
            selectedScoreId = p.getString(KEY_SELECTED_SCORE, PianoSettings.DEFAULT_SCORE_ID)
                .orEmpty().ifBlank { PianoSettings.DEFAULT_SCORE_ID },
            themeMode = AppThemeMode.fromStorageName(p.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)),
            dynamicColor = p.getBoolean(KEY_DYNAMIC_COLOR, true),
            keyboardMode = KeyboardMode.fromStorageName(p.getString(KEY_KEYBOARD_MODE, KeyboardMode.KEYS_21.name))
        )
    }

    fun saveSettings(context: Context, settings: PianoSettings) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_RPC_HOST, settings.rpcHost.ifBlank { "127.0.0.1" })
            .putInt(KEY_RPC_PORT, settings.rpcPort.coerceIn(1, 65535))
            .putString(KEY_RPC_TOKEN, settings.rpcToken.trim())
            .putFloat(KEY_SPEED, settings.speedMultiplier.coerceIn(PianoSettings.MIN_SPEED, PianoSettings.MAX_SPEED))
            .putLong(KEY_MIN_PRESS, settings.minPressMs.coerceIn(PianoSettings.FLOOR_PRESS_MS, 150L))
            .putLong(KEY_MIN_GAP, settings.minRestrikeGapMs.coerceIn(PianoSettings.FLOOR_RESTRIKE_GAP_MS, 200L))
            .putInt(KEY_MAX_POLY, settings.maxPolyphony.coerceIn(4, 14))
            .putLong(KEY_MAX_HOLD, settings.maxTapHoldMs.coerceIn(100L, 2000L))
            .putString(KEY_SELECTED_SCORE, settings.selectedScoreId)
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
            .putString(KEY_KEYBOARD_MODE, settings.keyboardMode.name)
            .apply()
    }

    fun loadAllScores(context: Context, keyboardMode: KeyboardMode = KeyboardMode.KEYS_21): List<PianoScore> {
        val result = mutableListOf<PianoScore>()
        // Built-in scores ordered by catalog.json
        try {
            val catalogRaw = context.assets.open("scores/catalog.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(catalogRaw)
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val fileName = if (keyboardMode == KeyboardMode.KEYS_36) {
                    item.optString("file36", "").ifBlank { item.optString("file", "") }
                } else {
                    item.optString("file", "")
                }
                if (fileName.isBlank()) continue
                val jsonRaw = context.assets.open("scores/$fileName").bufferedReader().use { it.readText() }
                PianoScore.fromJson(JSONObject(jsonRaw), isBuiltIn = true)?.let {
                    result += it.copy(targetKeyCount = keyboardMode.keyCount)
                }
            }
        } catch (_: Throwable) {
        }

        // Migrate legacy flat imported scores to subdirectories if needed
        migrateLegacyImportedScores(context)

        // User-imported scores for current keyboardMode
        val dir = getImportedDir(context, keyboardMode)
        if (dir.exists()) {
            dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file ->
                    try {
                        val obj = JSONObject(file.readText(Charsets.UTF_8))
                        PianoScore.fromJson(obj, isBuiltIn = false)?.let {
                            result += it.copy(targetKeyCount = keyboardMode.keyCount)
                        }
                    } catch (_: Throwable) {
                    }
                }
        }
        return result
    }

    fun saveImportedScore(context: Context, score: PianoScore, mode: KeyboardMode) {
        val dir = getImportedDir(context, mode)
        val safeId = score.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val withTarget = score.copy(targetKeyCount = mode.keyCount)
        File(dir, "$safeId.json").writeText(withTarget.toJson().toString(), Charsets.UTF_8)
    }

    fun deleteImportedScore(context: Context, scoreId: String, mode: KeyboardMode): Boolean {
        val safeId = scoreId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(getImportedDir(context, mode), "$safeId.json")
        if (file.exists() && file.delete()) return true
        // Fallback: check alternate mode
        val otherMode = if (mode == KeyboardMode.KEYS_36) KeyboardMode.KEYS_21 else KeyboardMode.KEYS_36
        val otherFile = File(getImportedDir(context, otherMode), "$safeId.json")
        return otherFile.exists() && otherFile.delete()
    }
}
