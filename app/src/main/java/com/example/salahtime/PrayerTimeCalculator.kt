package com.example.salahtime

import java.text.SimpleDateFormat
import java.util.*

data class PrayerTimeEvent(
    val prayerName: String,
    val eventType: String, // "15_min_before", "5_min_before", "start", "end"
    val time: Calendar,
    val notificationId: Int
)

class PrayerTimeCalculator {
    
    companion object {
        // Base notification IDs for each prayer (multiplied by 10 to allow for 4 events per prayer)
        private const val FAJR_BASE_ID = 100
        private const val DHUHR_BASE_ID = 200
        private const val ASR_BASE_ID = 300
        private const val MAGHRIB_BASE_ID = 400
        private const val ISHA_BASE_ID = 500
        
        // Event type offsets
        private const val OFFSET_15_MIN_BEFORE = 1
        private const val OFFSET_5_MIN_BEFORE = 2
        private const val OFFSET_START = 3
        private const val OFFSET_END = 4
    }
    
    fun calculateAllPrayerEvents(timings: Timings, selectedDate: String): List<PrayerTimeEvent> {
        val events = mutableListOf<PrayerTimeEvent>()
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val selectedCalendar = Calendar.getInstance()
        
        try {
            selectedCalendar.time = dateFormatter.parse(selectedDate) ?: Date()
        } catch (e: Exception) {
            selectedCalendar.time = Date() // fallback to today
        }
        
        // Define prayer times with their end times
        val prayerData = listOf(
            Triple("Fajr", timings.Fajr, timings.Sunrise),
            Triple("Dhuhr", timings.Dhuhr, timings.Asr),
            Triple("Asr", timings.Asr, timings.Sunset),
            Triple("Maghrib", timings.Maghrib, timings.Isha),
            Triple("Isha", timings.Isha, timings.Midnight) // Isha ends at midnight
        )
        
        prayerData.forEachIndexed { index, (prayerName, startTime, endTime) ->
            val baseId = when (prayerName) {
                "Fajr" -> FAJR_BASE_ID
                "Dhuhr" -> DHUHR_BASE_ID
                "Asr" -> ASR_BASE_ID
                "Maghrib" -> MAGHRIB_BASE_ID
                "Isha" -> ISHA_BASE_ID
                else -> 600 + (index * 100)
            }
            
            // Parse start time
            val startTimeStr = startTime.split(" ")[0] // Remove timezone info
            val startCal = parseTimeForDate(startTimeStr, selectedCalendar, formatter)
            
            if (startCal != null) {
                // Only schedule notifications for future times
                val now = Calendar.getInstance()
                
                // 15 minutes before
                val fifteenMinBefore = startCal.clone() as Calendar
                fifteenMinBefore.add(Calendar.MINUTE, -15)
                if (fifteenMinBefore.after(now)) {
                    events.add(PrayerTimeEvent(
                        prayerName,
                        PrayerNotificationReceiver.TYPE_15_MIN_BEFORE,
                        fifteenMinBefore,
                        baseId + OFFSET_15_MIN_BEFORE
                    ))
                }
                
                // 5 minutes before
                val fiveMinBefore = startCal.clone() as Calendar
                fiveMinBefore.add(Calendar.MINUTE, -5)
                if (fiveMinBefore.after(now)) {
                    events.add(PrayerTimeEvent(
                        prayerName,
                        PrayerNotificationReceiver.TYPE_5_MIN_BEFORE,
                        fiveMinBefore,
                        baseId + OFFSET_5_MIN_BEFORE
                    ))
                }
                
                // Prayer start
                if (startCal.after(now)) {
                    events.add(PrayerTimeEvent(
                        prayerName,
                        PrayerNotificationReceiver.TYPE_PRAYER_START,
                        startCal,
                        baseId + OFFSET_START
                    ))
                }
                
                // Prayer end
                val endCal = if (endTime != null) {
                    val endTimeStr = endTime.split(" ")[0]
                    parseTimeForDate(endTimeStr, selectedCalendar, formatter)
                } else {
                    // For Isha, end at midnight (next day)
                    val midnight = selectedCalendar.clone() as Calendar
                    midnight.add(Calendar.DAY_OF_MONTH, 1)
                    midnight.set(Calendar.HOUR_OF_DAY, 0)
                    midnight.set(Calendar.MINUTE, 0)
                    midnight.set(Calendar.SECOND, 0)
                    midnight.set(Calendar.MILLISECOND, 0)
                    midnight
                }
                
                if (endCal != null && endCal.after(now)) {
                    events.add(PrayerTimeEvent(
                        prayerName,
                        PrayerNotificationReceiver.TYPE_PRAYER_END,
                        endCal,
                        baseId + OFFSET_END
                    ))
                }
            }
        }
        
        return events.sortedBy { it.time.timeInMillis }
    }
    
    private fun parseTimeForDate(timeStr: String, dateCalendar: Calendar, formatter: SimpleDateFormat): Calendar? {
        return try {
            val time = formatter.parse(timeStr)
            if (time != null) {
                val calendar = dateCalendar.clone() as Calendar
                val timeCalendar = Calendar.getInstance()
                timeCalendar.time = time
                
                calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
                calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                
                calendar
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    fun getNextPrayerEvent(events: List<PrayerTimeEvent>): PrayerTimeEvent? {
        val now = Calendar.getInstance()
        return events.firstOrNull { it.time.after(now) }
    }
    
    fun getTodaysPrayerEvents(timings: Timings): List<PrayerTimeEvent> {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        return calculateAllPrayerEvents(timings, today)
    }
}

