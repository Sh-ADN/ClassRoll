package com.aistudio.classroll.jkmxlp.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aistudio.classroll.jkmxlp.MainActivity
import com.aistudio.classroll.jkmxlp.data.AppDatabase
import com.aistudio.classroll.jkmxlp.data.SettingsRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

const val REMINDER_WORK_NAME = "attendance_reminder"
private const val CHANNEL_ID = "attendance_reminder_channel"
private const val NOTIFICATION_ID = 1001

// Checks once a day whether today's attendance has been recorded yet, and
// shows a notification if not. Reschedules itself for the same time
// tomorrow every time it runs, so it keeps going without needing an exact
// alarm or any extra permission beyond POST_NOTIFICATIONS.
class AttendanceReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settingsRepo = SettingsRepository(applicationContext)
        val enabled = settingsRepo.reminderEnabledFlow.first()

        if (!enabled) {
            // Reminder was turned off since this was scheduled -- stop the chain.
            return Result.success()
        }

        val year = settingsRepo.academicYearFlow.first()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        if (year.isNotBlank()) {
            val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "classroll_db")
                .fallbackToDestructiveMigration()
                .build()
            try {
                val count = db.classRollDao().countAttendanceForDate(year, today)
                if (count == 0) {
                    showNotification()
                }
            } finally {
                db.close()
            }
        }

        // Queue tomorrow's check regardless, so the reminder keeps recurring.
        scheduleNext(applicationContext, settingsRepo.reminderTimeFlow.first())
        return Result.success()
    }

    private fun showNotification() {
        val context = applicationContext
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Attendance reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Attendance not taken yet")
            .setContentText("Today's attendance hasn't been recorded. Tap to open ClassRoll.")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        // Call this whenever the reminder is turned on, or its time is changed.
        // Safe to call repeatedly -- REPLACE policy means it always reflects
        // the latest settings instead of stacking up duplicate schedules.
        fun scheduleNext(context: Context, time: String) {
            val delay = millisUntilNext(time)
            val request = OneTimeWorkRequestBuilder<AttendanceReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                REMINDER_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_NAME)
        }

        private fun millisUntilNext(time: String): Long {
            val parts = time.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 10
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val now = Calendar.getInstance()
            val target = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (!target.after(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}
