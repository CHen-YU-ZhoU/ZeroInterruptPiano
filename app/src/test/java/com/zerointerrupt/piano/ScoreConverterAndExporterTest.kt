package com.zerointerrupt.piano

import com.zerointerrupt.piano.converter.ClickBundleExporter
import com.zerointerrupt.piano.converter.ScoreConverter
import com.zerointerrupt.piano.model.CalibrationGrid
import com.zerointerrupt.piano.model.CalibrationMethod
import com.zerointerrupt.piano.model.CalibrationProfile
import com.zerointerrupt.piano.model.KeyboardMode
import com.zerointerrupt.piano.model.PianoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreConverterAndExporterTest {

    @Test
    fun canonScaleTranspositionMapsCleanlyToC() {
        // D major phrase: D4(62), F#4(66), A4(69), D5(74) -> shifted -2 => C4(M1), E4(M3), G4(M5), C5(H1)
        val notes = listOf(
            ScoreConverter.RawNote(0L, 300L, 62),
            ScoreConverter.RawNote(300L, 300L, 66),
            ScoreConverter.RawNote(600L, 300L, 69),
            ScoreConverter.RawNote(900L, 600L, 74)
        )
        val score = ScoreConverter.mapNotesTo21Keys(
            id = "test_canon",
            title = "Canon D Sample",
            composer = "Pachelbel",
            sourceFormat = "TEST",
            bpm = 100f,
            notes = notes
        )
        assertEquals(100.0f, score.diatonicPct, 0.01f)
        assertEquals(-2, score.bestShift)
        assertEquals(listOf("M1", "M3", "M5", "H1"), score.events.flatMap { it.keys })
    }

    @Test
    fun exporterDeduplicatesChordsAndRespectsSpeedAndSeamlessGapGuard() {
        val notes = listOf(
            // Chord C4+E4+G4 twice at t=0 and t=1000
            ScoreConverter.RawNote(0L, 300L, 60),
            ScoreConverter.RawNote(5L, 300L, 64),
            ScoreConverter.RawNote(10L, 300L, 67),
            // Fast consecutive same-key strike (M1 at t=400 and t=490 -> 90ms apart -> gap <= 80ms)
            ScoreConverter.RawNote(400L, 40L, 60),
            ScoreConverter.RawNote(490L, 40L, 60),
            // Identical chord repeated at t=1000
            ScoreConverter.RawNote(1000L, 300L, 60),
            ScoreConverter.RawNote(1005L, 300L, 64),
            ScoreConverter.RawNote(1010L, 300L, 67)
        )
        val score = ScoreConverter.mapNotesTo21Keys(
            id = "test_exp",
            title = "Export Test",
            composer = "Test",
            sourceFormat = "TEST",
            bpm = 120f,
            notes = notes
        )
        val bundle1x = ClickBundleExporter.exportBundle(
            score = score,
            grid = CalibrationGrid(),
            settings = PianoSettings(),
            screenW = 2712,
            screenH = 1220,
            speedMultiplier = 1.0f
        )
        // Two identical chords should share 1 sub-preset
        assertEquals(1, bundle1x.subPresetCount)
        assertEquals(4, bundle1x.stepCount)
        assertTrue(bundle1x.json.contains("zerointerrupt.presets.bundle.v1"))
        // Should use actionType=2 (HOLD) or 3 (SUB_PRESET), never actionType=0 (TAP)
        assertTrue(!bundle1x.json.contains("\"actionType\":0"))

        val bundle2x = ClickBundleExporter.exportBundle(
            score = score,
            grid = CalibrationGrid(),
            settings = PianoSettings(),
            screenW = 2712,
            screenH = 1220,
            speedMultiplier = 2.0f
        )
        assertEquals(4, bundle2x.stepCount)
    }

    @Test
    fun scoreTargetKeyCountAnd36KeyAccidentalsPersistCleanly() {
        val notes = listOf(
            ScoreConverter.RawNote(0L, 200L, 60),  // C4 -> M1
            ScoreConverter.RawNote(200L, 200L, 61), // C#4 -> M1S
            ScoreConverter.RawNote(400L, 200L, 62), // D4 -> M2
            ScoreConverter.RawNote(600L, 200L, 63)  // D#4 -> M2S
        )
        val score36 = ScoreConverter.mapNotesTo36Keys(
            id = "test_chromatic",
            title = "Chromatic Test",
            composer = "Tester",
            sourceFormat = "TEST",
            bpm = 100f,
            notes = notes
        )
        assertEquals(36, score36.targetKeyCount)
        val keys = score36.events.flatMap { it.keys }
        assertTrue(keys.contains("M1S"))
        assertTrue(keys.contains("M2S"))

        val json = score36.toJson()
        assertEquals(36, json.getInt("targetKeyCount"))

        val parsed = com.zerointerrupt.piano.model.PianoScore.fromJson(json, isBuiltIn = false)
        assertTrue(parsed != null)
        assertEquals(36, parsed!!.targetKeyCount)
        assertEquals("test_chromatic", parsed.id)
    }

    @Test
    fun manualCalibrationProfileRoundTripAndKeyLookupWork() {
        assertEquals(21, CalibrationGrid.PICK_ORDER_21.size)
        assertEquals(36, CalibrationGrid.PICK_ORDER_36.size)
        assertEquals(listOf("L1", "L1S", "L2", "L2S", "L3"), CalibrationGrid.PICK_ORDER_36.take(5))
        assertEquals("低音 #2", CalibrationGrid.formatKeyWithOctave("L2S"))

        val manualGrid = CalibrationGrid(
            calibrationMethod = CalibrationMethod.MANUAL_PICK,
            manualPoints = mapOf(
                "L1" to Pair(0.12f, 0.85f),
                "L2S" to Pair(0.24f, 0.72f)
            )
        )
        val profile = CalibrationProfile(
            id = "game_heartopia",
            name = "心动小镇 36键",
            keyboardMode = KeyboardMode.KEYS_36,
            grid = manualGrid
        )
        val restored = CalibrationProfile.fromJson(profile.toJson())
        assertTrue(restored != null)
        assertEquals("心动小镇 36键", restored!!.name)
        assertEquals(KeyboardMode.KEYS_36, restored.keyboardMode)
        assertEquals(CalibrationMethod.MANUAL_PICK, restored.grid.calibrationMethod)

        val (nx, ny) = restored.grid.keyNorm("L2#")
        assertEquals(0.24f, nx, 0.0001f)
        assertEquals(0.72f, ny, 0.0001f)
    }
}
