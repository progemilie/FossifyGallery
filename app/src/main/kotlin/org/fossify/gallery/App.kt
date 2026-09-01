package org.fossify.gallery

import android.os.StrictMode
import org.fossify.gallery.helpers.Config
import com.github.ajalt.reprint.core.Reprint
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.Request
import okhttp3.Response
import org.fossify.commons.FossifyApp

class App : FossifyApp() {

    override val isAppLockFeatureAvailable = true

    /**
     * The one Config the app uses. Building one opens SharedPreferences - which stats the
     * filesystem, on whatever thread asked - and allocates six Flows, and it was measured doing
     * that 16 times per fling of the media grid. It lives here rather than in a static field so
     * that holding a Context is not a leak. See [org.fossify.gallery.extensions.config].
     */
    val config: Config by lazy { Config.newInstance(this) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }

        Reprint.initialize(this)
        Picasso.setSingletonInstance(Picasso.Builder(this).downloader(object : Downloader {
            override fun load(request: Request) = Response.Builder().build()

            override fun shutdown() {}
        }).build())
    }

    /**
     * Names the things that quietly cost a frame: a file read or a Room query on the main thread, a
     * stream left open. Logged rather than fatal - they are worth seeing, not worth a crash - and
     * debug-only, so a release build sets no policy at all.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }
}
