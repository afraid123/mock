package com.dj.dailyjobs.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dj.dailyjobs.DJApp
import com.dj.dailyjobs.MainActivity
import com.dj.dailyjobs.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (taskId <= 0) return

        createChannel(context)
        val app = context.applicationContext as DJApp
        CoroutineScope(Dispatchers.IO).launch {
            val task = app.repository.getTask(taskId) ?: return@launch
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pi = launchIntent?.let {
                android.app.PendingIntent.getActivity(
                    context,
                    taskId.toInt(),
                    it,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Task Reminder")
                    .setContentText(task.text)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setSilent(true)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()

                NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
            }

            if (task.repeatIntervalMinutes != null && !task.isCompleted) {
                val next = task.copy(reminderAtMillis = System.currentTimeMillis() + task.repeatIntervalMinutes * 60_000)
                app.repository.updateTask(next)
                ReminderScheduler(context).schedule(next)
            }
        }
    }

    private fun createChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Task Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        private const val CHANNEL_ID = "silent_reminders"
    }
}
