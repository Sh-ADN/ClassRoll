package com.aistudio.classroll.jkmxlp.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
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

class AttendanceReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settingsRepo = SettingsRepository(applicationContext)
        val enabled = settingsRepo.reminderEnabledFlow.first()
        if (!enabled) {
            return Result.success()
        }

        val year = settingsRepo.academicYearFlow.first()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.FRIDAY // or Saturday
        val holidays = settingsRepo.holidaysFlow.first()
        val isHoliday = holidays.contains(today)

        if (!isWeekend && !isHoliday) {
            try {
                val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "classroll_db")
                    .fallbackToDestructiveMigration()
                    .build()
                val count = db.classRollDao().countAttendanceForDate(year, today)
                db.close()

                if (count == 0) {
                    showNotification()
                }
            } catch (e: Exception) {
                // Ignore DB error
            }
        }

        // Reschedule next
        val time = settingsRepo.reminderTimeFlow.first()
        scheduleNext(applicationContext, time)

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
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Attendance not taken yet")
            .setContentText("Today's attendance hasn't been recorded. Tap to open ClassRoll.")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == 0
        if (hasPermission) {
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        fun scheduleNext(context: Context, time: String) {
            val delay = millisUntilNext(time)
            val request = OneTimeWorkRequest.Builder(AttendanceReminderWorker::class.java)
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
