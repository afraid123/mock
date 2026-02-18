package com.dj.dailyjobs.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dj.dailyjobs.DJApp
import java.io.File

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as DJApp
            val tasks = app.repository.getAllTasksNow()
            val outDir = File(applicationContext.filesDir, "backups").apply { mkdirs() }
            val backup = File(outDir, "tasks_latest.json")
            val json = buildString {
                append("[\n")
                tasks.forEachIndexed { idx, t ->
                    append("  {\"id\":${t.id},\"text\":\"")
                    append(t.text.replace("\"", "\\\""))
                    append("\",\"categoryId\":${t.categoryId},\"reminderAtMillis\":${t.reminderAtMillis},\"repeatIntervalMinutes\":${t.repeatIntervalMinutes},\"isCompleted\":${t.isCompleted}}")
                    if (idx != tasks.lastIndex) append(",")
                    append("\n")
                }
                append("]")
            }
            backup.writeText(json)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
