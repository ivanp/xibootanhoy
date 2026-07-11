package org.xiboplayer.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.xiboplayer.player.engine.PlayerEngine
import org.xiboplayer.player.model.CmsSettings
import org.xiboplayer.player.ui.XiboPlayerApp
import java.io.File

private val android.app.Activity.dataStore by preferencesDataStore(name = "xibo_settings")

class MainActivity : ComponentActivity() {

    private var engine: PlayerEngine? = null
    private val appLogger get() = (application as XiboPlayerApp).logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logger = appLogger

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val scope = rememberCoroutineScope()
                    var initialSettings by remember { mutableStateOf<CmsSettings?>(null) }
                    var engineReady by remember { mutableStateOf(false) }

                    // Load settings on first composition
                    LaunchedEffect(Unit) {
                        val settings = loadSettings()
                        initialSettings = settings
                        if (settings != null) {
                            startEngine(settings)
                            engineReady = true
                        }
                    }

                    XiboPlayerApp(
                        engine = if (engineReady) engine else null,
                        logger = logger,
                        onSaveSettings = { settings ->
                            scope.launch { saveSettings(settings) }
                            if (engine == null) {
                                startEngine(settings)
                            }
                        },
                        initialSettings = initialSettings
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        engine?.stop()
    }

    private fun startEngine(settings: CmsSettings) {
        val cacheDir = File(filesDir, "xibo-cache")
        val scope = kotlinx.coroutines.MainScope()
        engine = PlayerEngine(settings, cacheDir, appLogger, scope)
        engine?.start()
    }

    private suspend fun saveSettings(settings: CmsSettings) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("cms_address")] = settings.address
            prefs[stringPreferencesKey("cms_key")] = settings.key
            prefs[stringPreferencesKey("display_id")] = settings.displayId
            prefs[stringPreferencesKey("display_name")] = settings.displayName
        }
    }

    private suspend fun loadSettings(): CmsSettings? {
        val prefs = dataStore.data.first()
        val address = prefs[stringPreferencesKey("cms_address")] ?: return null
        val key = prefs[stringPreferencesKey("cms_key")] ?: return null
        val displayId = prefs[stringPreferencesKey("display_id")] ?: return null
        val displayName = prefs[stringPreferencesKey("display_name")] ?: "Android Xibo Player"

        return CmsSettings(
            address = address,
            key = key,
            displayId = displayId,
            displayName = displayName
        )
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
