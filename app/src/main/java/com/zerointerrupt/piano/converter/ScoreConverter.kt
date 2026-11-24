package com.zerointerrupt.piano.converter

import android.util.Xml
import com.zerointerrupt.piano.model.KeyboardMode
import com.zerointerrupt.piano.model.PianoNoteEvent
import com.zerointerrupt.piano.model.PianoScore
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object ScoreConverter {
    private val C_MAJOR_PCS = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val C_MAJOR_SET = C_MAJOR_PCS.toSet()

    private val SNAP_TO_DIATONIC = mapOf(
        0 to 0,
        1 to 0,   // C# -> C
        2 to 2,
        3 to 4,   // Eb -> E
        4 to 4,
        5 to 5,
        6 to 7,   // F# -> G
        7 to 7,
        8 to 7,   // Ab -> G
        9 to 9,
        10 to 9,  // Bb -> A
        11 to 11
    )

    private val STEP_TO_PC = mapOf(
        "C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11
    )

    private val KEY_NAMES: Map<Int, String> = buildMap {
        val prefixes = arrayOf("L", "M", "H")
        for ((rowIdx, prefix) in prefixes.withIndex()) {
            val baseMidi = 48 + rowIdx * 12 // L=C3(48), M=C4(60), H=C5(72)
            for ((degIdx, pc) in C_MAJOR_PCS.withIndex()) {
                put(baseMidi + pc, "$prefix${degIdx + 1}")
            }
        }
    }

    private val CHROMATIC_SUFFIXES = arrayOf(
        "1", "1S", "2", "2S", "3", "4", "4S", "5", "5S", "6", "6S", "7"
    )

    private val KEY_NAMES_36: Map<Int, String> = buildMap {
        val prefixes = arrayOf("L", "M", "H")
        for ((rowIdx, prefix) in prefixes.withIndex()) {
            val baseMidi = 48 + rowIdx * 12 // L=C3(48), M=C4(60), H=C5(72)
            for (pc in 0..11) {
                put(baseMidi + pc, "$prefix${CHROMATIC_SUFFIXES[pc]}")
            }
        }
    }

    data class RawNote(val startMs: Long, val durMs: Long, val midiPitch: Int)

    fun importFromStream(
        fileName: String,
        inputStream: InputStream,
        keyboardMode: KeyboardMode = KeyboardMode.KEYS_21
    ): PianoScore {
        val bytes = inputStream.readBytes()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val cleanTitle = fileName.substringBeforeLast('.').ifBlank { "导入乐谱" }
        val id = "user_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

        if (ext == "json") {
            val text = bytes.toString(Charsets.UTF_8)
            val parsed = PianoScore.fromJson(JSONObject(text), isBuiltIn = false)
                ?: throw IllegalArgumentException("无效的琴谱 JSON 格式")
            return parsed.copy(id = id, isBuiltIn = false)
        }

        val (notes, bpm, formatLabel) = when {
            ext == "mid" || ext == "midi" || isMidiHeader(bytes) -> {
                val (n, b) = parseMidi(bytes)
                Triple(n, b, "MIDI")
            }
            ext == "mxl" || isZipHeader(bytes) -> {
                val xmlBytes = extractMusicXmlFromMxl(bytes)
                val (n, b) = parseMusicXml(xmlBytes)
                Triple(n, b, "MXL")
            }
            ext == "xml" || ext == "musicxml" -> {
                val (n, b) = parseMusicXml(bytes)
                Triple(n, b, "XML")
            }
            else -> throw IllegalArgumentException("不支持的文件格式: .$ext（请使用 .mid / .mxl / .musicxml / .json）")
        }

        if (notes.isEmpty()) {
            throw IllegalArgumentException("乐谱中未解析到有效音符")
        }
        return if (keyboardMode == KeyboardMode.KEYS_36) {
            mapNotesTo36Keys(
                id = id,
                title = cleanTitle,
                composer = "本地导入",
                sourceFormat = formatLabel,
                bpm = bpm,
                notes = notes,
                isBuiltIn = false
            )
        } else {
            mapNotesTo21Keys(
                id = id,
                title = cleanTitle,
                composer = "本地导入",
                sourceFormat = formatLabel,
                bpm = bpm,
                notes = notes,
                isBuiltIn = false
            )
        }
    }

    private fun isMidiHeader(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'M'.code.toByte() && bytes[1] == 'T'.code.toByte() &&
            bytes[2] == 'h'.code.toByte() && bytes[3] == 'd'.code.toByte()

    private fun isZipHeader(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private class ByteCursor(private val data: ByteArray) {
        var pos = 0
        fun remaining(): Int = data.size - pos
        fun readU8(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else 0
        fun readU16BE(): Int = (readU8() shl 8) or readU8()
        fun readI16BE(): Int = readU16BE().toShort().toInt()
        fun readU32BE(): Long =
            (readU8().toLong() shl 24) or (readU8().toLong() shl 16) or (readU8().toLong() shl 8) or readU8().toLong()

        fun readString(len: Int): String {
            val actual = len.coerceAtMost(remaining())
            val s = String(data, pos, actual, Charsets.US_ASCII)
            pos += actual
            return s
        }

        fun skip(len: Int) {
            pos = (pos + len).coerceAtMost(data.size)
        }

        fun readBytes(len: Int): ByteArray {
            val actual = len.coerceAtMost(remaining())
            val out = data.copyOfRange(pos, pos + actual)
            pos += actual
            return out
        }

        fun readVlq(): Int {
            var v = 0
            for (i in 0 until 4) {
                if (pos >= data.size) break
                val b = readU8()
                v = (v shl 7) or (b and 0x7F)
                if ((b and 0x80) == 0) break
            }
            return v
        }
    }

    private data class MidiRawEvent(
        val tick: Long,
        val isOn: Boolean,
        val ch: Int,
        val pitch: Int
    )

    private fun parseMidi(data: ByteArray): Pair<List<RawNote>, Float> {
        val c = ByteCursor(data)
        if (c.readString(4) != "MThd") throw IllegalArgumentException("无效 MIDI 文件头")
        val headerLen = c.readU32BE().toInt()
        c.readU16BE() // fmt
        val nTrks = c.readU16BE()
        val division = c.readI16BE()
        if (headerLen > 6) c.skip(headerLen - 6)

        val ticksPerQuarter = if (division > 0) {
            division.toDouble()
        } else {
            // SMPTE fallback: treat ticks/sec roughly at 120bpm
            val fps = -((division shr 8).toByte().toInt())
            val tpf = division and 0xFF
            max(1, fps * tpf / 2).toDouble()
        }

        val tempoEvents = mutableListOf(0L to 500_000L)
        val rawEvents = mutableListOf<MidiRawEvent>()

        for (t in 0 until nTrks) {
            if (c.remaining() < 8) break
            val chunkId = c.readString(4)
            val tLen = c.readU32BE().toInt()
            val endPos = (c.pos + tLen).coerceAtMost(data.size)
            if (chunkId != "MTrk") {
                c.pos = endPos
                continue
            }
            var tick = 0L
            var running = 0
            while (c.pos < endPos) {
                tick += c.readVlq()
                if (c.pos >= endPos) break
                val b = c.readU8()
                val status: Int
                var d1: Int? = null
                if (b < 0x80) {
                    status = running
                    d1 = b
                } else {
                    status = b
                    if (status < 0xF0) {
                        running = status
                    } else if (status in 0xF0..0xF7) {
                        running = 0
                    }
                }
                when {
                    status == 0xFF -> {
                        val meta = c.readU8()
                        val mLen = c.readVlq()
                        val payload = c.readBytes(mLen)
                        if (meta == 0x51 && payload.size == 3) {
                            val usQ = ((payload[0].toInt() and 0xFF) shl 16) or
                                ((payload[1].toInt() and 0xFF) shl 8) or
                                (payload[2].toInt() and 0xFF)
                            if (usQ > 0) tempoEvents += tick to usQ.toLong()
                        }
                    }
                    status == 0xF0 || status == 0xF7 -> {
                        val mLen = c.readVlq()
                        c.skip(mLen)
                    }
                    else -> {
                        val ev = status and 0xF0
                        val ch = status and 0x0F
                        val p1 = d1 ?: c.readU8()
                        if (ev == 0xC0 || ev == 0xD0) continue
                        val p2 = c.readU8()
                        if (ch == 9) continue // Skip GM drums
                        if (ev == 0x90 && p2 > 0) {
                            rawEvents += MidiRawEvent(tick, true, ch, p1)
                        } else if (ev == 0x80 || (ev == 0x90 && p2 == 0)) {
                            rawEvents += MidiRawEvent(tick, false, ch, p1)
                        }
                    }
                }
            }
            c.pos = endPos
        }

        tempoEvents.sortBy { it.first }
        val mergedTempos = mutableListOf<Pair<Long, Long>>()
        for ((tk, us) in tempoEvents) {
            if (mergedTempos.isNotEmpty() && mergedTempos.last().first == tk) {
                mergedTempos[mergedTempos.lastIndex] = tk to us
            } else {
                mergedTempos += tk to us
            }
        }

        data class TempoSeg(val startTick: Long, val startMs: Double, val msPerTick: Double)
        val segments = ArrayList<TempoSeg>(mergedTempos.size)
        var curTk = 0L
        var curMs = 0.0
        var curUs = mergedTempos[0].second
        for ((tk, us) in mergedTempos) {
            val dt = tk - curTk
            if (dt > 0) {
                curMs += dt * (curUs / 1000.0) / ticksPerQuarter
            }
            curTk = tk
            curUs = us
            segments += TempoSeg(curTk, curMs, (curUs / 1000.0) / ticksPerQuarter)
        }

        fun tickToMs(tk: Long): Double {
            var lo = 0
            var hi = segments.lastIndex
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (segments[mid].startTick <= tk) lo = mid else hi = mid - 1
            }
            val seg = segments[lo]
            return seg.startMs + (tk - seg.startTick) * seg.msPerTick
        }

        rawEvents.sortWith(compareBy<MidiRawEvent> { it.tick }.thenBy { if (it.isOn) 1 else 0 })
        val active = HashMap<Long, ArrayDeque<Double>>()
        val notes = mutableListOf<RawNote>()
        for (ev in rawEvents) {
            val key = (ev.ch.toLong() shl 8) or ev.pitch.toLong()
            val ms = tickToMs(ev.tick)
            if (ev.isOn) {
                active.getOrPut(key) { ArrayDeque() }.addLast(ms)
            } else {
                val q = active[key]
                if (q != null && q.isNotEmpty()) {
                    val sMs = q.removeFirst()
                    notes += RawNote(sMs.roundToLong(), max(25L, (ms - sMs).roundToLong()), ev.pitch)
                }
            }
        }
        for ((key, q) in active) {
            val pitch = (key and 0xFF).toInt()
            while (q.isNotEmpty()) {
                val sMs = q.removeFirst()
                notes += RawNote(sMs.roundToLong(), 250L, pitch)
            }
        }
        notes.sortWith(compareBy<RawNote> { it.startMs }.thenBy { it.midiPitch })
        val initBpm = (60_000_000.0 / mergedTempos[0].second).toFloat()
        return notes to ((initBpm * 10f).roundToInt() / 10f)
    }

    private fun extractMusicXmlFromMxl(mxlBytes: ByteArray): ByteArray {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(mxlBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
            }
        }
        var rootFile: String? = null
        val containerBytes = entries["META-INF/container.xml"]
        if (containerBytes != null) {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(ByteArrayInputStream(containerBytes), "UTF-8")
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    rootFile = parser.getAttributeValue(null, "full-path")
                    if (!rootFile.isNullOrBlank()) break
                }
                event = parser.next()
            }
        }
        if (rootFile != null && entries.containsKey(rootFile)) {
            return entries.getValue(rootFile)
        }
        val fallbackKey = entries.keys.firstOrNull { !it.startsWith("META-INF") && it.endsWith(".xml", ignoreCase = true) }
            ?: throw IllegalArgumentException("MXL 压缩包内未找到 MusicXML 乐谱文件")
        return entries.getValue(fallbackKey)
    }

    private fun parseMusicXml(xmlBytes: ByteArray): Pair<List<RawNote>, Float> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        }

        val notes = mutableListOf<RawNote>()
        var firstBpm: Double? = null

        var inPart = false
        var divisions = 1.0
        var curTempo = 96.0
        var measureStartMs = 0.0
        var cursorMs = 0.0
        var lastOnsetMs = 0.0
        var measureEndMs = 0.0
        val tiedNotes = HashMap<String, Int>()

        // Note element temp state
        var inMeasure = false
        var inNote = false
        var inBackup = false
        var inForward = false
        var inAttributes = false
        var inPitch = false

        var isGrace = false
        var isChord = false
        var isRest = false
        var noteDurDiv = 0.0
        var noteVoice = "1"
        var pitchStep = ""
        var pitchOctave = 4
        var pitchAlter = 0
        var tieStart = false
        var tieStop = false

        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "part" -> {
                            inPart = true
                            divisions = 1.0
                            curTempo = 96.0
                            measureStartMs = 0.0
                            tiedNotes.clear()
                        }
                        "measure" -> if (inPart) {
                            inMeasure = true
                            cursorMs = measureStartMs
                            lastOnsetMs = measureStartMs
                            measureEndMs = measureStartMs
                        }
                        "attributes" -> if (inMeasure) inAttributes = true
                        "divisions" -> if (inAttributes) {
                            divisions = max(1.0, parser.nextText().trim().toDoubleOrNull() ?: divisions)
                        }
                        "sound" -> if (inMeasure) {
                            val tStr = parser.getAttributeValue(null, "tempo")
                            val tVal = tStr?.toDoubleOrNull()
                            if (tVal != null) {
                                curTempo = tVal.coerceAtLeast(20.0)
                                if (firstBpm == null) firstBpm = curTempo
                            }
                        }
                        "backup" -> if (inMeasure) inBackup = true
                        "forward" -> if (inMeasure) inForward = true
                        "note" -> if (inMeasure) {
                            inNote = true
                            isGrace = false
                            isChord = false
                            isRest = false
                            noteDurDiv = 0.0
                            noteVoice = "1"
                            pitchStep = ""
                            pitchOctave = 4
                            pitchAlter = 0
                            tieStart = false
                            tieStop = false
                        }
                        "grace" -> if (inNote) isGrace = true
                        "chord" -> if (inNote) isChord = true
                        "rest" -> if (inNote) isRest = true
                        "voice" -> if (inNote) {
                            noteVoice = parser.nextText().trim().ifEmpty { "1" }
                        }
                        "duration" -> {
                            val div = parser.nextText().trim().toDoubleOrNull() ?: 0.0
                            val dtMs = (div / divisions) * (60000.0 / curTempo)
                            when {
                                inNote -> noteDurDiv = div
                                inBackup -> cursorMs = max(measureStartMs, cursorMs - dtMs)
                                inForward -> {
                                    cursorMs += dtMs
                                    measureEndMs = max(measureEndMs, cursorMs)
                                }
                            }
                        }
                        "pitch" -> if (inNote) inPitch = true
                        "step" -> if (inPitch) pitchStep = parser.nextText().trim().uppercase()
                        "octave" -> if (inPitch) pitchOctave = parser.nextText().trim().toIntOrNull() ?: 4
                        "alter" -> if (inPitch) pitchAlter = (parser.nextText().trim().toDoubleOrNull() ?: 0.0).roundToInt()
                        "tie" -> if (inNote) {
                            when (parser.getAttributeValue(null, "type")) {
                                "start" -> tieStart = true
                                "stop" -> tieStop = true
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "part" -> inPart = false
                        "measure" -> if (inPart) {
                            inMeasure = false
                            measureStartMs = if (measureEndMs > measureStartMs) measureEndMs else cursorMs
                        }
                        "attributes" -> inAttributes = false
                        "backup" -> inBackup = false
                        "forward" -> inForward = false
                        "pitch" -> inPitch = false
                        "note" -> if (inNote) {
                            inNote = false
                            if (!isGrace) {
                                val durMs = (noteDurDiv / divisions) * (60000.0 / curTempo)
                                val onsetMs = if (isChord) lastOnsetMs else cursorMs
                                if (!isChord) {
                                    lastOnsetMs = onsetMs
                                    cursorMs += durMs
                                    measureEndMs = max(measureEndMs, cursorMs)
                                }
                                val stepPc = STEP_TO_PC[pitchStep]
                                if (!isRest && stepPc != null) {
                                    val midi = (pitchOctave + 1) * 12 + stepPc + pitchAlter
                                    val tk = "$noteVoice:$midi"
                                    val existingIdx = tiedNotes[tk]
                                    if (tieStop && existingIdx != null) {
                                        val prev = notes[existingIdx]
                                        notes[existingIdx] = prev.copy(durMs = max(30L, (prev.durMs + durMs).roundToLong()))
                                        if (!tieStart) tiedNotes.remove(tk)
                                    } else {
                                        notes += RawNote(onsetMs.roundToLong(), max(30L, durMs.roundToLong()), midi)
                                        if (tieStart) tiedNotes[tk] = notes.lastIndex
                                    }
                                }
                            }
                        }
                    }
                }
            }
            ev = parser.next()
        }

        notes.sortWith(compareBy<RawNote> { it.startMs }.thenBy { it.midiPitch })
        val bpm = ((firstBpm ?: 96.0) * 10.0).roundToInt() / 10f
        return notes to bpm
    }

    fun mapNotesTo21Keys(
        id: String,
        title: String,
        composer: String,
        sourceFormat: String,
        bpm: Float,
        notes: List<RawNote>,
        isBuiltIn: Boolean = false
    ): PianoScore {
        var bestShift = 0
        var bestInScale = -1
        var bestIn21 = -1

        for (shift in -18..18) {
            var inScale = 0
            var in21 = 0
            for (n in notes) {
                val tp = n.midiPitch + shift
                val pc = Math.floorMod(tp, 12)
                if (pc in C_MAJOR_SET) {
                    inScale++
                    if (tp in 48..83) in21++
                }
            }
            val better = when {
                inScale != bestInScale -> inScale > bestInScale
                in21 != bestIn21 -> in21 > bestIn21
                else -> abs(shift) < abs(bestShift)
            }
            if (better) {
                bestInScale = inScale
                bestIn21 = in21
                bestShift = shift
            }
        }

        var snappedCnt = 0
        var foldedCnt = 0
        val t0 = notes.first().startMs

        data class MappedNote(val tMs: Long, val durMs: Long, val keyId: String)
        val mapped = ArrayList<MappedNote>(notes.size)

        for (n in notes) {
            val tp = n.midiPitch + bestShift
            var pc = Math.floorMod(tp, 12)
            val octv = Math.floorDiv(tp, 12)
            if (pc !in C_MAJOR_SET) {
                snappedCnt++
                pc = SNAP_TO_DIATONIC[pc] ?: 0
            }
            var adj = octv * 12 + pc
            if (adj < 48 || adj > 83) {
                foldedCnt++
                while (adj < 48) adj += 12
                while (adj > 83) adj -= 12
            }
            val keyId = KEY_NAMES[adj] ?: "M1"
            mapped += MappedNote((n.startMs - t0).coerceAtLeast(0L), n.durMs, keyId)
        }

        // Group onsets within 18ms into chords
        val events = mutableListOf<PianoNoteEvent>()
        val curKeys = mutableListOf<String>()
        var curT = 0L
        var curDur = 0L

        fun flushGroup() {
            if (curKeys.isNotEmpty()) {
                events += PianoNoteEvent(curT, curKeys.toList(), curDur)
                curKeys.clear()
            }
        }

        for (m in mapped) {
            if (curKeys.isNotEmpty() && abs(m.tMs - curT) <= 18L) {
                if (m.keyId !in curKeys) curKeys += m.keyId
                curDur = max(curDur, m.durMs)
            } else {
                flushGroup()
                curT = m.tMs
                curDur = m.durMs
                curKeys += m.keyId
            }
        }
        flushGroup()

        // Compute max polyphony with sustain
        val activeHolds = mutableListOf<Pair<Long, String>>()
        var maxPoly = 1
        for (ev in events) {
            activeHolds.removeAll { it.first <= ev.tMs }
            for (k in ev.keys) {
                if (activeHolds.none { it.second == k }) {
                    activeHolds += (ev.tMs + ev.durMs) to k
                }
            }
            maxPoly = max(maxPoly, activeHolds.size)
        }

        val total = notes.size.coerceAtLeast(1)
        val diatonicPct = ((bestInScale * 1000.0) / total).roundToInt() / 10f
        val direct21Pct = ((bestIn21 * 1000.0) / total).roundToInt() / 10f
        val durationMs = events.lastOrNull()?.let { it.tMs + it.durMs } ?: 0L

        return PianoScore(
            id = id,
            title = title,
            composer = composer,
            sourceFormat = sourceFormat,
            bpm = bpm,
            durationMs = durationMs,
            totalNotes = notes.size,
            bestShift = bestShift,
            diatonicPct = diatonicPct,
            direct21Pct = direct21Pct,
            snappedAccidentals = snappedCnt,
            octaveFolded = foldedCnt,
            maxChordOnset = events.maxOfOrNull { it.keys.size } ?: 1,
            maxPolyphony = maxPoly,
            events = events,
            isBuiltIn = isBuiltIn,
            targetKeyCount = 21
        )
    }

    fun mapNotesTo36Keys(
        id: String,
        title: String,
        composer: String,
        sourceFormat: String,
        bpm: Float,
        notes: List<RawNote>,
        isBuiltIn: Boolean = false
    ): PianoScore {
        // Choose octave shift (-12, 0, +12) or semitone shift (-18..18) maximizing notes directly in C3..B5 (48..83)
        var bestShift = 0
        var bestIn36 = -1
        for (shift in intArrayOf(0, -12, 12, -24, 24)) {
            var in36 = 0
            for (n in notes) {
                val tp = n.midiPitch + shift
                if (tp in 48..83) in36++
            }
            val better = when {
                in36 != bestIn36 -> in36 > bestIn36
                else -> abs(shift) < abs(bestShift)
            }
            if (better) {
                bestIn36 = in36
                bestShift = shift
            }
        }

        var foldedCnt = 0
        val t0 = notes.first().startMs
        data class MappedNote(val tMs: Long, val durMs: Long, val keyId: String)
        val mapped = ArrayList<MappedNote>(notes.size)

        for (n in notes) {
            var adj = n.midiPitch + bestShift
            if (adj < 48 || adj > 83) {
                foldedCnt++
                while (adj < 48) adj += 12
                while (adj > 83) adj -= 12
            }
            val keyId = KEY_NAMES_36[adj] ?: "M1"
            mapped += MappedNote((n.startMs - t0).coerceAtLeast(0L), n.durMs, keyId)
        }

        val events = mutableListOf<PianoNoteEvent>()
        val curKeys = mutableListOf<String>()
        var curT = 0L
        var curDur = 0L

        fun flushGroup() {
            if (curKeys.isNotEmpty()) {
                events += PianoNoteEvent(curT, curKeys.toList(), curDur)
                curKeys.clear()
            }
        }

        for (m in mapped) {
            if (curKeys.isNotEmpty() && abs(m.tMs - curT) <= 18L) {
                if (m.keyId !in curKeys) curKeys += m.keyId
                curDur = max(curDur, m.durMs)
            } else {
                flushGroup()
                curT = m.tMs
                curDur = m.durMs
                curKeys += m.keyId
            }
        }
        flushGroup()

        val activeHolds = mutableListOf<Pair<Long, String>>()
        var maxPoly = 1
        for (ev in events) {
            activeHolds.removeAll { it.first <= ev.tMs }
            for (k in ev.keys) {
                if (activeHolds.none { it.second == k }) {
                    activeHolds += (ev.tMs + ev.durMs) to k
                }
            }
            maxPoly = max(maxPoly, activeHolds.size)
        }

        val total = notes.size.coerceAtLeast(1)
        val direct36Pct = ((bestIn36 * 1000.0) / total).roundToInt() / 10f
        val durationMs = events.lastOrNull()?.let { it.tMs + it.durMs } ?: 0L

        return PianoScore(
            id = id,
            title = title,
            composer = composer,
            sourceFormat = sourceFormat,
            bpm = bpm,
            durationMs = durationMs,
            totalNotes = notes.size,
            bestShift = bestShift,
            diatonicPct = 100.0f,
            direct21Pct = direct36Pct,
            snappedAccidentals = 0,
            octaveFolded = foldedCnt,
            maxChordOnset = events.maxOfOrNull { it.keys.size } ?: 1,
            maxPolyphony = maxPoly,
            events = events,
            isBuiltIn = isBuiltIn,
            targetKeyCount = 36
        )
    }
}
