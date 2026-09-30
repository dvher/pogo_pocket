package dev.pogo.pocket

import android.app.Application
import dev.pogo.pocket.widget.SyncWorker

class PogoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedulePeriodic(this)
    }
}
