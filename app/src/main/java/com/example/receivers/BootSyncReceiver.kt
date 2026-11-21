package com.example.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ElysiumApp
import com.example.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val appCtx = context.applicationContext
        val app = appCtx as? ElysiumApp ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = app.workoutRepository
                val preferences = PreferencesManager(appCtx)
                NotificationScheduler.rescheduleAllAlarms(appCtx, repository, preferences)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
