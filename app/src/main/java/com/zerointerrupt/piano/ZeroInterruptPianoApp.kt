package com.zerointerrupt.piano

import android.app.Application
import com.zerointerrupt.piano.player.PianoPlayerEngine

class ZeroInterruptPianoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PianoPlayerEngine.initialize(this)
    }
}
