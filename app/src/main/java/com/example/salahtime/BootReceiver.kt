package com.example.salahtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device rebooted, rescheduling prayer notifications")

            // Reschedule all prayer notifications
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = Room.databaseBuilder(
                        context.applicationContext,
                        SalahDatabase::class.java,
                        "salah_db"
                    ).fallbackToDestructiveMigration(false).build()

                    val (lat, lon) = loadLastLocation(context)
                    if (!lat.isNullOrEmpty() && !lon.isNullOrEmpty()) {
                        val today = getTodayDate()
                        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
                        val cached = db.salahDao().getValidSalahData(today, lat, lon, sevenDaysAgo)

                        if (cached != null) {
                            val gson = com.google.gson.Gson()
                            val responseBody = gson.fromJson(cached.dataJson, SalahTimeApp::class.java)
                            val prayerScheduler = PrayerNotificationScheduler(context)
                            prayerScheduler.scheduleAllPrayerNotifications(responseBody.data.timings, today)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error rescheduling notifications", e)
                }
            }
        }
    }

    private fun getTodayDate(): String {
        val sdf = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}

