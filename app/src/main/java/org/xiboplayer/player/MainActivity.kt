package org.xiboplayer.player

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.xiboplayer.player.engine.PlayerEngine
import org.xiboplayer.player.model.CmsSettings
import org.xiboplayer.player.ui.XiboPlayerApp
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "xibo_settings"
        private const val KEY_ADDRESS = "cms_address"
        private const val KEY_KEY = "cms_key"
        private const val KEY_DISPLAY_ID = "display_id"
        private const val KEY_DISPLAY_NAME = "display_name"
    }

    private val appLogger get() = (application as XiboPlayerApp).logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val logger = appLogger
            val scope = rememberCoroutineScope()
            var engine by remember { mutableStateOf<PlayerEngine?>(null) }
            var initialSettings by remember { mutableStateOf<CmsSettings?>(null) }

            // Load settings on first composition
            LaunchedEffect(Unit) {
                val settings = loadSettings()
                initialSettings = settings
                if (settings != null) {
                    val e = createEngine(settings)
                    e.start()
                    engine = e
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    XiboPlayerApp(
                        engine = engine,
                        logger = logger,
                        onSaveSettings = { settings ->
                            saveSettings(settings)
                            if (engine == null) {
                                val e = createEngine(settings)
                                e.start()
                                engine = e
                            }
                        },
                        onLogout = {
                            engine?.stop()
                            engine = null
                            clearSettings()
                        },
                        initialSettings = initialSettings
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
    }

    private fun createEngine(settings: CmsSettings): PlayerEngine {
        val cacheDir = File(filesDir, "xibo-cache")
        val scope = kotlinx.coroutines.MainScope()
        return PlayerEngine(settings, cacheDir, appLogger, scope)
    }

    private fun saveSettings(settings: CmsSettings) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ADDRESS, settings.address)
            .putString(KEY_KEY, settings.key)
            .putString(KEY_DISPLAY_ID, settings.displayId)
            .putString(KEY_DISPLAY_NAME, settings.displayName)
            .apply()
    }

    private fun loadSettings(): CmsSettings? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        val key = prefs.getString(KEY_KEY, null) ?: return null
        val displayId = prefs.getString(KEY_DISPLAY_ID, null) ?: return null
        val displayName = prefs.getString(KEY_DISPLAY_NAME, "Android Xibo Player") ?: "Android Xibo Player"

        return CmsSettings(
            address = address,
            key = key,
            displayId = displayId,
            displayName = displayName
        )
    }

    private fun clearSettings() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

private val darkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF4CAF50),
    secondary = Color(0xFF2196F3),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)
