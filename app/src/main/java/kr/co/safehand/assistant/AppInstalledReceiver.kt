package kr.co.safehand.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** 새 앱 설치를 감지해 출처와 권한 확인을 안내한다. 앱 업데이트는 알리지 않는다. */
class AppInstalledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        val installedPackage = intent.data?.schemeSpecificPart ?: return
        if (installedPackage == context.packageName) return

        val appLabel = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(installedPackage, 0)).toString()
        }.getOrDefault("새 앱")
        showSafetyReminder(context, installedPackage, appLabel)
    }

    private fun showSafetyReminder(context: Context, packageName: String, appLabel: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "새 앱 설치 확인", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "새로 설치된 앱의 출처와 권한을 확인하도록 알려드립니다."
                }
            )
        }
        val detailsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        val pendingIntent = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            detailsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("새 앱이 설치되었어요")
            .setContentText("$appLabel: 출처와 권한을 확인해 보세요.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$appLabel 앱이 설치되었어요. 출처와 권한을 확인해 보세요."))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(packageName.hashCode(), notification)
    }

    private companion object { const val CHANNEL_ID = "new_app_safety_reminder" }
}
