package my.app.chordmate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * This receiver is triggered when the device boots up, allowing us to
 * restore the scheduled reminders that would otherwise be lost after reboot.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null &&
                intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {

            // Load reminder settings
            SharedPreferences prefs = context.getSharedPreferences("ChordMatePrefs", Context.MODE_PRIVATE);
            boolean reminderEnabled = prefs.getBoolean("reminder_enabled", true);

            if (reminderEnabled) {
                // Get the reminder time
                int hour = prefs.getInt("reminder_hour", 20);
                int minute = prefs.getInt("reminder_minute", 0);

                // Schedule the reminder
                ReminderManager.scheduleReminder(context, hour, minute);
            }
        }
    }
}