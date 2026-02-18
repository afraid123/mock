package com.dj.dailyjobs.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dj.dailyjobs.DJApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as DJApp
        CoroutineScope(Dispatchers.IO).launch {
            val scheduler = ReminderScheduler(context)
            app.repository.getAllTasksNow()
                .filter { !it.isCompleted && it.reminderAtMillis != null }
                .forEach { scheduler.schedule(it) }
        }
    }
}
