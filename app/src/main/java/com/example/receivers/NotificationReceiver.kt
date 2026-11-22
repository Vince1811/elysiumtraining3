package com.example.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WATER_REMINDER = "com.example.action.WATER_REMINDER"
        const val ACTION_MEDICATION_REMINDER = "com.example.action.MEDICATION_REMINDER"
        const val ACTION_CHECK_APP_UPDATE = "com.example.action.CHECK_APP_UPDATE"

        const val EXTRA_MED_ID = "extra_med_id"
        const val EXTRA_MED_NAME = "extra_med_name"
        const val EXTRA_MED_DOSAGE = "extra_med_dosage"
        const val EXTRA_MED_TIME = "extra_med_time"

        const val CHANNEL_MED_ID = "medication_reminders"
        const val CHANNEL_WATER_ID = "water_reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        Log.d("NotificationReceiver", "Received alarm: $action")

        val prefs = com.example.data.PreferencesManager(context.applicationContext)
        val pendingResult = goAsync()

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                if (action == ACTION_CHECK_APP_UPDATE) {
                    val enabled = prefs.updateNotificationsEnabledFlow.first()
                    if (enabled) {
                        AppUpdateNotifier.checkAndNotifyIfUpdated(context.applicationContext, prefs)
                        NotificationScheduler.scheduleAppUpdateCheck(context.applicationContext)
                    }
                    return@launch
                }

                // Check if the corresponding setting is enabled before showing notification
                val isEnabled = when (action) {
                    ACTION_WATER_REMINDER -> prefs.waterRemindersEnabledFlow.first()
                    ACTION_MEDICATION_REMINDER -> prefs.medicationRemindersEnabledFlow.first()
                    else -> true
                }

                if (!isEnabled) {
                    Log.d("NotificationReceiver", "Notifications for action $action are disabled. Skipping notification.")
                    return@launch
                }

                // Create notification channels if on Android O or above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val medChannel = NotificationChannel(
                        CHANNEL_MED_ID,
                        "Medication Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Channels for medication taking alerts"
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(medChannel)

                    val waterChannel = NotificationChannel(
                        CHANNEL_WATER_ID,
                        "Water Reminders",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Channels for water intake reminders"
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(waterChannel)
                }

                val contentIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = when (action) {
                    ACTION_MEDICATION_REMINDER -> {
                        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: "Medication"
                        val dosage = intent.getStringExtra(EXTRA_MED_DOSAGE) ?: "Prescribed Dosage"
                        NotificationCompat.Builder(context, CHANNEL_MED_ID)
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle("Take Medication Reminder 💊")
                            .setContentText("It's time to take your $medName (Dose: $dosage). Please mark it as taken!")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                    }
                    ACTION_WATER_REMINDER -> {
                        NotificationCompat.Builder(context, CHANNEL_WATER_ID)
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle("Drink Water Reminder 💧")
                            .setContentText("Keep up with your hydration habits! It's time to drink a glass of water now.")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                    }
                    else -> return@launch
                }

                val notificationId = when (action) {
                    ACTION_MEDICATION_REMINDER -> {
                        val medId = intent.getIntExtra(EXTRA_MED_ID, 0)
                        1000 + medId
                    }
                    ACTION_WATER_REMINDER -> 2000
                    else -> 3000
                }

                if (AppUpdateNotifier.hasNotificationPermission(context)) {
                    notificationManager.notify(notificationId, builder.build())
                }

                // Chain the next water alarm if enabled
                if (action == ACTION_WATER_REMINDER) {
                    val interval = prefs.waterReminderIntervalMinsFlow.first()
                    NotificationScheduler.scheduleWaterReminder(context, interval)
                    Log.d("NotificationReceiver", "Chained next water reminder in $interval mins.")
                }
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Error processing notification check/alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
