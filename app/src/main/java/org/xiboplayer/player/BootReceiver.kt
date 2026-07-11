package org.xiboplayer.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Auto-starts the player on device boot.
 * For TV boxes that need to start the player automatically.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        }
    }
}
