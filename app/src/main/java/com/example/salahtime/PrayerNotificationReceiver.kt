package com.example.salahtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PrayerNotificationReceiver : BroadcastReceiver() {
    
    companion object {
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        
        // Notification types
        const val TYPE_15_MIN_BEFORE = "15_min_before"
        const val TYPE_5_MIN_BEFORE = "5_min_before"
        const val TYPE_PRAYER_START = "prayer_start"
        const val TYPE_PRAYER_END = "prayer_end"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return
        val notificationType = intent.getStringExtra(EXTRA_NOTIFICATION_TYPE) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        
        Log.d("PrayerNotification", "Received notification: $prayerName - $notificationType")
        
        val notificationHelper = NotificationHelper(context)
        
        val (title, message) = when (notificationType) {
            TYPE_15_MIN_BEFORE -> {
                "Prayer Reminder" to "$prayerName prayer starts in 15 minutes"
            }
            TYPE_5_MIN_BEFORE -> {
                "Prayer Reminder" to "$prayerName prayer starts in 5 minutes"
            }
            TYPE_PRAYER_START -> {
                "Prayer Time" to "$prayerName prayer has started"
            }
            TYPE_PRAYER_END -> {
                "Prayer Time" to "$prayerName prayer time has ended"
            }
            else -> return
        }
        
        notificationHelper.showPrayerNotification(
            notificationId,
            title,
            message,
            prayerName
        )
    }
}

