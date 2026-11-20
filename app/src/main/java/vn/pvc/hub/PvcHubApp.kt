package vn.pvc.hub

import android.app.Application
import vn.pvc.hub.sources.SyncRunner

class PvcHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncRunner.schedule(this)
    }
}
