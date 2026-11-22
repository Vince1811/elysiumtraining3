package com.example.receivers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.GitHubUpdateCheckResult
import com.example.data.GitHubUpdateChecker
import com.example.data.PreferencesManager
import kotlinx.coroutines.flow.first

object AppUpdateNotifier {
    const val CHANNEL_APP_UPDATES = "app_updates"
    const val NOTIFICATION_ID_APP_UPDATE = 3001

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    suspend fun checkAndNotifyIfUpdated(
        context: Context,
        preferencesManager: PreferencesManager,
        forceNotify: Boolean = false
    ): GitHubUpdateCheckResult {
        val source = preferencesManager.appUpdateSourceFlow.first()
            .ifBlank { PreferencesManager.DEFAULT_GITHUB_UPDATE_URL }
        val token = preferencesManager.githubUpdateTokenFlow.first()
        val specifiedApk = preferencesManager.specifiedApkUrlFlow.first().trim()
        val notificationsEnabled = preferencesManager.updateNotificationsEnabledFlow.first()
        val lastNotifiedKey = preferencesManager.lastNotifiedUpdateKeyFlow.first()
        val dismissedLabel = preferencesManager.dismissedUpdateLabelFlow.first()

        val result = GitHubUpdateChecker.checkGitHubFilesForUpdate(
            context = context.applicationContext,
            rawSource = source,
            token = token
        )
        val effectiveApkUrl = specifiedApk.ifBlank { result.apkDownloadUrl.orEmpty() }

        preferencesManager.saveUpdateStatus(
            available = result.isUpdateAvailable,
            label = result.updateLabel,
            message = result.statusMessage,
            url = effectiveApkUrl
        )

        if (result.isUpdateAvailable && notificationsEnabled) {
            val signature = listOf(
                result.updateLabel,
                result.latestCommitSha,
                effectiveApkUrl
            ).joinToString("|")
            val isDismissed = result.updateLabel.isNotBlank() && result.updateLabel == dismissedLabel
            if (forceNotify || (!isDismissed && signature != lastNotifiedKey)) {
                val posted = showUpdateNotification(context.applicationContext, result)
                if (posted) {
                    preferencesManager.saveLastNotifiedUpdateKey(signature)
                }
            }
        }

        return result.copy(
            apkDownloadUrl = effectiveApkUrl.ifBlank { result.apkDownloadUrl }
        )
    }

    fun showUpdateNotification(context: Context, result: GitHubUpdateCheckResult): Boolean {
        if (!hasNotificationPermission(context)) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_APP_UPDATES,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when GitHub repository files change and a new app update is available."
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val openSettingsIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, "settings")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_APP_UPDATE,
            openSettingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (result.updateLabel.isNotBlank()) {
            "App Update Ready (${result.updateLabel})"
        } else {
            "App Update Available"
        }
        val summary = "GitHub files changed. Tap to open Settings and download the update."
        val expanded = buildString {
            append(summary)
            if (result.statusMessage.isNotBlank()) {
                append("\n")
                append(result.statusMessage)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_APP_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID_APP_UPDATE, notification)
        return true
    }

    fun openExternalUpdateUrl(context: Context, rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
