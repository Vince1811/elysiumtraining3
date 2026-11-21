package com.example.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.PreferencesManager
import com.example.data.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

object NotificationScheduler {
    private const val TAG = "NotificationScheduler"

    // Request Codes for PendingIntents to keep them unique
    private const val RC_WATER = 9999
    private const val RC_MEDICATION_BASE = 10000

    fun scheduleWaterReminder(context: Context, intervalMins: Int) {
        if (intervalMins <= 0) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_WATER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RC_WATER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + intervalMins.toLong() * 60 * 1000
        val canScheduleExact = if (android.os.Build.VERSION.SDK_INT >= 31) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            try {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.d(TAG, "Scheduled water reminder in $intervalMins mins.")
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.i(TAG, "Permission fallback water reminder scheduled due to: ${e.message}")
            } catch (e: Exception) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.w(TAG, "Fallback water reminder scheduled due to: ${e.message}")
            }
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Log.d(TAG, "Scheduled water reminder (inexact habit) since exact permissions are not held or needed.")
        }
    }

    fun cancelWaterReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_WATER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RC_WATER,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled water reminder alarm.")
        }
    }

    fun scheduleMedicationReminder(
        context: Context,
        medicationId: Int,
        medicationName: String,
        dosage: String,
        timeString: String
    ) {
        val calendar = parseTimeToCalendar(timeString) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_MEDICATION_REMINDER
            putExtra(NotificationReceiver.EXTRA_MED_ID, medicationId)
            putExtra(NotificationReceiver.EXTRA_MED_NAME, medicationName)
            putExtra(NotificationReceiver.EXTRA_MED_DOSAGE, dosage)
            putExtra(NotificationReceiver.EXTRA_MED_TIME, timeString)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RC_MEDICATION_BASE + medicationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canScheduleExact = if (android.os.Build.VERSION.SDK_INT >= 31) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            try {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                Log.d(TAG, "Scheduled medication '$medicationName' (ID: $medicationId) at: ${calendar.time}")
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                Log.i(TAG, "Permission fallback medication '$medicationName' scheduled due to: ${e.message}")
            } catch (e: Exception) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                Log.w(TAG, "Fallback medication '$medicationName' scheduled due to: ${e.message}")
            }
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            Log.d(TAG, "Scheduled medication '$medicationName' (inexact) because exact permissions are not held or needed.")
        }
    }

    fun cancelMedicationReminder(context: Context, medicationId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_MEDICATION_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RC_MEDICATION_BASE + medicationId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled medication reminder (ID: $medicationId).")
        }
    }

    fun rescheduleAllAlarms(
        context: Context,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ) {
        // Run in background / non-blocking thread preferably
        Log.d(TAG, "Rescheduling all active reminders...")
        
        // 1. Water Reminder
        // Flow reads we fetch immediately using .first() to read once block-free
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val waterEnabled = preferencesManager.waterRemindersEnabledFlow.first()
                if (waterEnabled) {
                    val interval = preferencesManager.waterReminderIntervalMinsFlow.first()
                    scheduleWaterReminder(context, interval)
                } else {
                    cancelWaterReminder(context)
                }

                // 2. Medication Reminders
                val medications = repository.allMedications.first()
                medications.forEach { medication ->
                    scheduleMedicationReminder(
                        context,
                        medication.id,
                        medication.name,
                        medication.dosage,
                        medication.reminderTime
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling alarms: ${e.message}")
            }
        }
    }

    fun parseTimeToCalendar(timeString: String): Calendar? {
        val clean = timeString.trim().uppercase()
        val digits = clean.filter { it.isDigit() || it == ':' }
        if (digits.isEmpty()) return null
        
        val parts = digits.split(":")
        if (parts.isEmpty()) return null
        
        val hour: Int
        val minute: Int
        
        try {
            if (parts.size >= 2) {
                hour = parts[0].toInt()
                minute = parts[1].toInt()
            } else {
                val num = digits.toInt()
                if (num in 0..23) {
                    hour = num
                    minute = 0
                } else if (digits.length >= 3) {
                    hour = digits.substring(0, digits.length - 2).toInt()
                    minute = digits.substring(digits.length - 2).toInt()
                } else {
                    return null
                }
            }
        } catch (e: Exception) {
            return null
        }
        
        var finalHour = hour
        if (clean.contains("PM") && finalHour < 12) {
            finalHour += 12
        } else if (clean.contains("AM") && finalHour == 12) {
            finalHour = 0
        }
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, finalHour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar
    }
}
