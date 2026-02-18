package com.dj.dailyjobs

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dj.dailyjobs.backup.BackupWorker
import com.dj.dailyjobs.data.AppDatabase
import com.dj.dailyjobs.data.TaskRepository
import com.dj.dailyjobs.reminder.ReminderScheduler
import java.util.concurrent.TimeUnit

class DJApp : Application() {
    lateinit var repository: TaskRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        repository = TaskRepository(db.taskDao(), db.categoryDao())
        enqueueBackupWorker()
    }

    private fun enqueueBackupWorker() {
        val req = PeriodicWorkRequestBuilder<BackupWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "periodic_backup",
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }
}
