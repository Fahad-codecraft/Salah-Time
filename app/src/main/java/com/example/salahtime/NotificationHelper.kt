package com.example.salahtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {
    
    companion object {
        const val PRAYER_CHANNEL_ID = "prayer_notifications"
        const val PRAYER_CHANNEL_NAME = "Prayer Notifications"
        const val PRAYER_CHANNEL_DESCRIPTION = "Notifications for prayer times"
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PRAYER_CHANNEL_ID,
                PRAYER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = PRAYER_CHANNEL_DESCRIPTION
                enableVibration(true)
                setShowBadge(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showPrayerNotification(
        notificationId: Int,
        title: String,
        message: String,
        prayerName: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, PRAYER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        
        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Handle case where notification permission is not granted
            e.printStackTrace()
        }
    }
    
    fun cancelNotification(notificationId: Int) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(notificationId)
    }
    
    fun cancelAllNotifications() {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancelAll()
    }
}

