package com.example.salahtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class PrayerNotificationScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val calculator = PrayerTimeCalculator()

    fun scheduleAllPrayerNotifications(timings: Timings, deviceCurrentDate: String) {
        // Cancel existing alarms first
        cancelAllScheduledNotifications()

        // Always use the device's current date for scheduling notifications
        val events = calculator.calculateAllPrayerEvents(timings, deviceCurrentDate)

        Log.d("PrayerScheduler", "Scheduling ${events.size} prayer notification events for device date: $deviceCurrentDate")

        events.forEach { event ->
            scheduleNotification(event)
        }

        // Log next upcoming event
        val nextEvent = calculator.getNextPrayerEvent(events)
        if (nextEvent != null) {
            val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            Log.d("PrayerScheduler", "Next event: ${nextEvent.prayerName} ${nextEvent.eventType} at ${timeFormat.format(nextEvent.time.time)}")
        }
    }

    private fun scheduleNotification(event: PrayerTimeEvent) {
        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            putExtra(PrayerNotificationReceiver.EXTRA_PRAYER_NAME, event.prayerName)
            putExtra(PrayerNotificationReceiver.EXTRA_NOTIFICATION_TYPE, event.eventType)
            putExtra(PrayerNotificationReceiver.EXTRA_NOTIFICATION_ID, event.notificationId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use setExactAndAllowWhileIdle for better reliability
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    event.time.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    event.time.timeInMillis,
                    pendingIntent
                )
            }

            val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            Log.d("PrayerScheduler", "Scheduled: ${event.prayerName} ${event.eventType} at ${timeFormat.format(event.time.time)}")

        } catch (e: SecurityException) {
            Log.e("PrayerScheduler", "Failed to schedule alarm - permission denied", e)
        } catch (e: Exception) {
            Log.e("PrayerScheduler", "Failed to schedule alarm", e)
        }
    }

    fun cancelAllScheduledNotifications() {
        // Cancel all possible notification IDs
        val baseIds = listOf(100, 200, 300, 400, 500) // Fajr, Dhuhr, Asr, Maghrib, Isha
        val offsets = listOf(3, 4) // start, end

        baseIds.forEach { baseId ->
            offsets.forEach { offset ->
                val notificationId = baseId + offset
                cancelScheduledNotification(notificationId)
            }
        }

        Log.d("PrayerScheduler", "Cancelled all scheduled prayer notifications")
    }

    private fun cancelScheduledNotification(notificationId: Int) {
        val intent = Intent(context, PrayerNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun requestExactAlarmPermission(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission()) {
            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        } else {
            null
        }
    }

    private fun getCurrentDate(): String {
        val sdf = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    fun getUpcomingNotifications(timings: Timings, deviceCurrentDate: String): List<PrayerTimeEvent> {
        // Always use the device's current date for upcoming notifications
        val events = calculator.calculateAllPrayerEvents(timings, deviceCurrentDate)
        val now = java.util.Calendar.getInstance()

        return events.filter { it.time.after(now) }.take(5) // Next 5 upcoming events
    }
}

