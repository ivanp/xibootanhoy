package org.xiboplayer.player

import android.app.Application
import android.os.Environment
import org.xiboplayer.player.model.CmsSettings
import org.xiboplayer.player.storage.FileCache
import org.xiboplayer.player.util.Logger
import java.io.File

/**
 * Xibo Player application class.
 */
class XiboPlayerApp : Application() {

    lateinit var logger: Logger
        private set

    lateinit var cache: FileCache
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        logger = Logger()

        val cacheDir = File(filesDir, "xibo-cache")
        cache = FileCache(cacheDir, logger)

        logger.info("Xibo Player v1.0.0 starting")
    }

    companion object {
        lateinit var instance: XiboPlayerApp
            private set
    }
}
