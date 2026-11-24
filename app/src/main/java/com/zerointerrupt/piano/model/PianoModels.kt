package com.zerointerrupt.piano.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Normalized 3x7 piano bounding box (0..1 relative to full screen dimensions).
 * Default values match M0 hardware calibration on 2712x1220 landscape display.
 */
data class CalibrationGrid(
    val leftNorm: Float = DEFAULT_LEFT_NORM,
    val topNorm: Float = DEFAULT_TOP_NORM,
    val rightNorm: Float = DEFAULT_RIGHT_NORM,
    val bottomNorm: Float = DEFAULT_BOTTOM_NORM
) {
    fun keyNorm(keyId: String): Pair<Float, Float> {
        val clean = keyId.trim().uppercase()
        val rowIdx = when (clean.firstOrNull()) {
            'H' -> 0
            'M' -> 1
            'L' -> 2
            else -> 1
        }
        val isSharp = clean.endsWith("S") || clean.endsWith("#")
        val degStr = if (isSharp) clean.drop(1).dropLast(1) else clean.drop(1)
        val colIdx = ((degStr.toIntOrNull() ?: 1) - 1).coerceIn(0, 6)
        val nx = if (isSharp) {
            (leftNorm + (colIdx + 1.0f) / 7f * (rightNorm - leftNorm)).coerceIn(0.01f, 0.99f)
        } else {
            (leftNorm + (colIdx + 0.5f) / 7f * (rightNorm - leftNorm)).coerceIn(0.01f, 0.99f)
        }
        val ny = if (isSharp) {
            (topNorm + (rowIdx + 0.26f) / 3f * (bottomNorm - topNorm)).coerceIn(0.01f, 0.99f)
        } else {
            (topNorm + (rowIdx + 0.5f) / 3f * (bottomNorm - topNorm)).coerceIn(0.01f, 0.99f)
        }
        return Pair(nx, ny)
    }

    fun toScreenPixel(keyId: String, screenW: Int, screenH: Int): Pair<Float, Float> {
        val (nx, ny) = keyNorm(keyId)
        val maxX = max(1, screenW - 1).toFloat()
        val maxY = max(1, screenH - 1).toFloat()
        return Pair((nx * maxX).coerceIn(0f, maxX), (ny * maxY).coerceIn(0f, maxY))
    }

    fun clamped(): CalibrationGrid {
        val minW = 0.16f
        val minH = 0.12f
        val l = leftNorm.coerceIn(0.01f, 1f - minW)
        val t = topNorm.coerceIn(0.01f, 1f - minH)
        val r = rightNorm.coerceIn(l + minW, 0.99f)
        val b = bottomNorm.coerceIn(t + minH, 0.99f)
        return CalibrationGrid(l, t, r, b)
    }

    companion object {
        const val DEFAULT_LEFT_NORM = 0.2181f
        const val DEFAULT_TOP_NORM = 0.4430f
        const val DEFAULT_RIGHT_NORM = 0.7810f
        const val DEFAULT_BOTTOM_NORM = 0.9422f

        val ALL_KEYS: List<String> = listOf("H", "M", "L").flatMap { r ->
            (1..7).map { c -> "$r$c" }
        }

        val BLACK_KEY_DEGREES: IntArray = intArrayOf(1, 2, 4, 5, 6)

        val ALL_KEYS_36: List<String> = listOf("H", "M", "L").flatMap { r ->
            (1..7).map { c -> "$r$c" } + BLACK_KEY_DEGREES.map { d -> "$r${d}S" }
        }
    }
}

data class PianoNoteEvent(
    val tMs: Long,
    val keys: List<String>,
    val durMs: Long
)

data class PianoScore(
    val id: String,
    val title: String,
    val composer: String,
    val sourceFormat: String,
    val bpm: Float,
    val durationMs: Long,
    val totalNotes: Int,
    val bestShift: Int,
    val diatonicPct: Float,
    val direct21Pct: Float,
    val snappedAccidentals: Int,
    val octaveFolded: Int,
    val maxChordOnset: Int,
    val maxPolyphony: Int,
    val events: List<PianoNoteEvent>,
    val isBuiltIn: Boolean = true,
    val targetKeyCount: Int = 21
) {
    val fitTierLabel: String
        get() = when {
            diatonicPct >= 93f -> "高度还原"
            diatonicPct >= 88f -> "轻度变音"
            else -> "半音较多"
        }

    fun formattedDuration(speed: Float = 1.0f): String {
        val scaledSec = ((durationMs / speed.coerceIn(0.5f, 2.0f)) / 1000f).roundToInt().coerceAtLeast(0)
        return "%d:%02d".format(scaledSec / 60, scaledSec % 60)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("composer", composer)
        put("sourceFormat", sourceFormat)
        put("bpm", bpm.toDouble())
        put("durationMs", durationMs)
        put("totalNotes", totalNotes)
        put("bestShift", bestShift)
        put("diatonicPct", diatonicPct.toDouble())
        put("direct21Pct", direct21Pct.toDouble())
        put("snappedAccidentals", snappedAccidentals)
        put("octaveFolded", octaveFolded)
        put("maxChordOnset", maxChordOnset)
        put("maxPolyphony", maxPolyphony)
        put("targetKeyCount", targetKeyCount)
        val arr = JSONArray()
        for (ev in events) {
            arr.put(JSONObject().apply {
                put("t", ev.tMs)
                put("dur", ev.durMs)
                put("keys", JSONArray(ev.keys))
            })
        }
        put("events", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject, isBuiltIn: Boolean): PianoScore? {
            val id = obj.optString("id", "").ifBlank { return null }
            val evArr = obj.optJSONArray("events") ?: return null
            val events = ArrayList<PianoNoteEvent>(evArr.length())
            for (i in 0 until evArr.length()) {
                val e = evArr.optJSONObject(i) ?: continue
                val keysArr = e.optJSONArray("keys") ?: continue
                val keys = ArrayList<String>(keysArr.length())
                for (j in 0 until keysArr.length()) {
                    val k = keysArr.optString(j, "").trim().uppercase()
                    if (k.isNotEmpty()) keys += k
                }
                if (keys.isNotEmpty()) {
                    events += PianoNoteEvent(
                        tMs = e.optLong("t", 0L).coerceAtLeast(0L),
                        keys = keys,
                        durMs = e.optLong("dur", 120L).coerceAtLeast(25L)
                    )
                }
            }
            if (events.isEmpty()) return null
            val lastEnd = events.maxOf { it.tMs + it.durMs }
            val hasSharps = events.any { ev -> ev.keys.any { it.endsWith("S") || it.endsWith("#") } }
            val targetKeyCount = obj.optInt("targetKeyCount", if (hasSharps) 36 else 21)
            return PianoScore(
                id = id,
                title = obj.optString("title", id),
                composer = obj.optString("composer", "未知作曲家"),
                sourceFormat = obj.optString("sourceFormat", "JSON"),
                bpm = obj.optDouble("bpm", 100.0).toFloat(),
                durationMs = obj.optLong("durationMs", lastEnd).coerceAtLeast(lastEnd),
                totalNotes = obj.optInt("totalNotes", events.sumOf { it.keys.size }),
                bestShift = obj.optInt("bestShift", 0),
                diatonicPct = obj.optDouble("diatonicPct", 100.0).toFloat(),
                direct21Pct = obj.optDouble("direct21Pct", 100.0).toFloat(),
                snappedAccidentals = obj.optInt("snappedAccidentals", 0),
                octaveFolded = obj.optInt("octaveFolded", 0),
                maxChordOnset = obj.optInt("maxChordOnset", events.maxOf { it.keys.size }),
                maxPolyphony = obj.optInt("maxPolyphony", events.maxOf { it.keys.size }),
                events = events,
                isBuiltIn = isBuiltIn,
                targetKeyCount = targetKeyCount
            )
        }
    }
}

enum class KeyboardMode(val keyCount: Int, val label: String) {
    KEYS_21(21, "21键"),
    KEYS_36(36, "36键");

    companion object {
        fun fromStorageName(raw: String?): KeyboardMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: KEYS_21
    }
}

enum class AppThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun fromStorageName(raw: String?): AppThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

data class PianoSettings(
    val rpcHost: String = "127.0.0.1",
    val rpcPort: Int = 27183,
    val rpcToken: String = "",
    val speedMultiplier: Float = 1.0f,
    val minPressMs: Long = DEFAULT_MIN_PRESS_MS,
    val minRestrikeGapMs: Long = DEFAULT_MIN_RESTRIKE_GAP_MS,
    val maxPolyphony: Int = DEFAULT_MAX_POLYPHONY,
    val maxTapHoldMs: Long = DEFAULT_MAX_TAP_HOLD_MS,
    val selectedScoreId: String = DEFAULT_SCORE_ID,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val keyboardMode: KeyboardMode = KeyboardMode.KEYS_21
) {
    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2.0f
        const val FLOOR_PRESS_MS = 28L
        const val DEFAULT_MIN_PRESS_MS = 35L
        const val FLOOR_RESTRIKE_GAP_MS = 35L
        const val DEFAULT_MIN_RESTRIKE_GAP_MS = 50L
        const val DEFAULT_MAX_POLYPHONY = 12
        const val DEFAULT_MAX_TAP_HOLD_MS = 450L
        const val DEFAULT_SCORE_ID = "canon_in_d_full"
    }
}
