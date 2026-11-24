package com.zerointerrupt.piano.converter

import com.zerointerrupt.piano.model.CalibrationGrid
import com.zerointerrupt.piano.model.PianoScore
import com.zerointerrupt.piano.model.PianoSettings
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToLong

data class ExportBundleSummary(
    val json: String,
    val stepCount: Int,
    val subPresetCount: Int,
    val totalPresetCount: Int
)

/**
 * Exports a PianoScore into ZeroInterruptClicker's `zerointerrupt.presets.bundle.v1` schema.
 *
 * Compatibility guarantees verified against ZeroInterruptClicker's ClickTarget.kt:
 * 1. Uses ActionType.HOLD (2) for single notes and sub-preset children instead of ActionType.TAP (0),
 *    because ClickTarget.resolveSingle overwrites TAP steps with global ClickGlobalSettings defaults.
 * 2. Deduplicates identical chord sub-presets (by sorted keys + quantized press duration).
 * 3. Guards against ClickTarget's SEAMLESS_HOLD_GAP_MS (80ms) & SEAMLESS_HOLD_DIST_PX (20px) merge:
 *    whenever consecutive steps hit the same key with gap <= 80ms, alternates +/-0.005f normX (~27px apart
 *    on 2712px width, still well inside the 155px key box) so ClickTarget never merges distinct strikes.
 */
object ClickBundleExporter {

    private const val ACTION_HOLD = 2
    private const val ACTION_SUB_PRESET = 3
    private const val MODE_CONCURRENT = 0
    private const val MODE_SEQUENTIAL = 1

    fun exportBundle(
        score: PianoScore,
        grid: CalibrationGrid,
        settings: PianoSettings,
        screenW: Int,
        screenH: Int,
        speedMultiplier: Float = settings.speedMultiplier
    ): ExportBundleSummary {
        val speed = speedMultiplier.coerceIn(PianoSettings.MIN_SPEED, PianoSettings.MAX_SPEED).toDouble()
        val minPress = settings.minPressMs.coerceAtLeast(PianoSettings.FLOOR_PRESS_MS)
        val minGap = settings.minRestrikeGapMs.coerceAtLeast(PianoSettings.FLOOR_RESTRIKE_GAP_MS)
        val maxPoly = settings.maxPolyphony.coerceIn(4, 14)

        val maxX = max(1, screenW - 1).toFloat()
        val maxY = max(1, screenH - 1).toFloat()

        val presetsArr = JSONArray()
        val mainTargetsArr = JSONArray()
        val chordSubCache = HashMap<String, String>() // signature -> subPresetId
        var subSeq = 1
        var sameKeyAltSign = 1

        val cleanTitle = score.title.replace("#", "").trim().take(18).ifEmpty { "钢琴曲" }
        val events = score.events

        for (i in events.indices) {
            val ev = events[i]
            val curT = (ev.tMs / speed).roundToLong()
            val nextT = if (i + 1 < events.size) {
                (events[i + 1].tMs / speed).roundToLong()
            } else {
                curT + (ev.durMs / speed).roundToLong() + 150L
            }
            val deltaT = max(minPress + minGap, nextT - curT)
            val scaledDur = (ev.durMs / speed).roundToLong().coerceIn(minPress, settings.maxTapHoldMs)
            val dur = minOf(scaledDur, max(minPress, deltaT - minGap))
            val gap = max(minGap, deltaT - dur)

            val keys = ev.keys.take(maxPoly)
            val nextKeys = if (i + 1 < events.size) events[i + 1].keys else emptyList()

            if (keys.size == 1) {
                val key = keys[0]
                var (nx, ny) = grid.keyNorm(key)
                // Prevent ClickTarget seamless hold merge if same key repeats with gap <= 80ms
                if (gap <= 80L && key in nextKeys) {
                    nx = (nx + sameKeyAltSign * 0.0055f).coerceIn(0.01f, 0.99f)
                    sameKeyAltSign = -sameKeyAltSign
                }
                val px = (nx * maxX).toDouble()
                val py = (ny * maxY).toDouble()
                mainTargetsArr.put(JSONObject().apply {
                    put("id", mainTargetsArr.length() + 1)
                    put("x", px)
                    put("y", py)
                    put("normX", nx.toDouble())
                    put("normY", ny.toDouble())
                    put("intervalMs", gap)
                    put("pressDurationMs", dur)
                    put("microJitter", false)
                    put("enabled", true)
                    put("actionType", ACTION_HOLD)
                    put("repeatCount", 1)
                })
            } else {
                val sortedKeys = keys.sorted()
                val quantizedDur = ((dur + 4L) / 5L) * 5L
                val sig = "${sortedKeys.joinToString(",")}|$quantizedDur"
                val subId = chordSubCache.getOrPut(sig) {
                    val newId = "piano_chord_${subSeq}"
                    val subTargets = JSONArray()
                    sortedKeys.forEachIndexed { idx, k ->
                        val (nx, ny) = grid.keyNorm(k)
                        subTargets.put(JSONObject().apply {
                            put("id", idx + 1)
                            put("x", (nx * maxX).toDouble())
                            put("y", (ny * maxY).toDouble())
                            put("normX", nx.toDouble())
                            put("normY", ny.toDouble())
                            put("intervalMs", 3000L)
                            put("pressDurationMs", quantizedDur)
                            put("microJitter", false)
                            put("enabled", true)
                            put("actionType", ACTION_HOLD)
                            put("repeatCount", 1)
                        })
                    }
                    presetsArr.put(JSONObject().apply {
                        put("id", newId)
                        put("name", "#$cleanTitle.${subSeq}")
                        put("executionMode", MODE_CONCURRENT)
                        put("targets", subTargets)
                    })
                    subSeq++
                    newId
                }

                mainTargetsArr.put(JSONObject().apply {
                    put("id", mainTargetsArr.length() + 1)
                    put("x", (screenW * 0.5).toDouble())
                    put("y", (screenH * 0.5).toDouble())
                    put("normX", -1.0)
                    put("normY", -1.0)
                    put("intervalMs", gap)
                    put("pressDurationMs", quantizedDur)
                    put("microJitter", false)
                    put("enabled", true)
                    put("actionType", ACTION_SUB_PRESET)
                    put("repeatCount", 1)
                    put("refPresetId", subId)
                    put("subDurationMs", quantizedDur)
                })
            }
        }

        val speedTag = if (speedMultiplier == 1.0f) "" else " ${"%.1f".format(speedMultiplier)}x"
        val mainId = "piano_main_${score.id.take(16)}"
        presetsArr.put(JSONObject().apply {
            put("id", mainId)
            put("name", "钢琴:$cleanTitle$speedTag")
            put("executionMode", MODE_SEQUENTIAL)
            put("targets", mainTargetsArr)
        })

        val bundle = JSONObject().apply {
            put("schema", "zerointerrupt.presets.bundle.v1")
            put("activePresetId", mainId)
            put("presets", presetsArr)
        }

        return ExportBundleSummary(
            json = bundle.toString(),
            stepCount = mainTargetsArr.length(),
            subPresetCount = subSeq - 1,
            totalPresetCount = presetsArr.length()
        )
    }
}
