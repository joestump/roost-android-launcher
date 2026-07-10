package rocks.stump.claudelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * On boot we can't reliably start an activity directly (background-activity-launch limits),
 * so we just arm a one-shot flag. The system starts our HOME activity as the default launcher,
 * and MainActivity.onResume consumes the flag to foreground Claude.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.autoLaunchOnBoot(context)) {
            Prefs.setPendingBootLaunch(context, true)
        }
    }
}
