package com.example.salahtime

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.salahtime.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var db: SalahDatabase

    private val LOCATION_PERMISSION_REQUEST = 1001

    private var currentLatitude: String? = null
    private var currentLongitude: String? = null
    private var currentDate: String = getTodayDate()

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        db = Room.databaseBuilder(applicationContext, SalahDatabase::class.java, "salah_db")
            .fallbackToDestructiveMigration(false) // <- Add this line
            .build()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationTextView = findViewById(R.id.tv_location)
        dateTextView = findViewById(R.id.tv_date)

        findViewById<Button>(R.id.myButton).setOnClickListener {
            checkLocationPermissionAndFetch()
        }

        dateTextView.text = formatDateForDisplay(currentDate)
        dateTextView.setOnClickListener { showDatePicker() }

        lifecycleScope.launch {
            val (lat, lon) = loadLastLocation(this@MainActivity)
            if (!lat.isNullOrEmpty() && !lon.isNullOrEmpty()) {
                currentLatitude = lat
                currentLongitude = lon
                locationTextView.text = "Lat: $lat, Lon: $lon"
                fetchSalahData()
            } else {
                checkLocationPermissionAndFetch()
            }
        }
    }

    private fun checkLocationPermissionAndFetch() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            requestNewLocationData()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation()
        }
    }

    private fun getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location == null) {
                        requestNewLocationData()
                    } else {
                        updateLocation(location)
                    }
                }
                .addOnFailureListener {
                    Log.e("location", "Location error: ${it.localizedMessage}")
                }
        }
    }

    private fun requestNewLocationData() {
        val locationRequest = LocationRequest.Builder(1)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setIntervalMillis(0)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    updateLocation(location)
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun updateLocation(location: Location) {
        val lat = location.latitude.toString()
        val lon = location.longitude.toString()

        if (lat != currentLatitude || lon != currentLongitude) {
            currentLatitude = lat
            currentLongitude = lon
            locationTextView.text = "Lat: $lat, Lon: $lon"

            lifecycleScope.launch {
                saveLocation(lat, lon, this@MainActivity)
                fetchSevenDaysAndCache(lat, lon, getTodayDate()) // Fetch for today and next 6 days
            }
        } else {
            locationTextView.text = "Lat: $lat, Lon: $lon"
            lifecycleScope.launch {
                fetchSalahData()
            }
        }
    }


    private fun fetchSalahData() {
        if (currentLatitude == null || currentLongitude == null) return

        val lat = currentLatitude!!
        val lon = currentLongitude!!

        lifecycleScope.launch {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val cached = db.salahDao().getValidSalahData(currentDate, lat, lon, sevenDaysAgo)

            if (cached != null) {
                val responseBody = Gson().fromJson(cached.dataJson, SalahTimeApp::class.java)
                displaySalahData(responseBody)
            } else if (isNetworkAvailable(this@MainActivity)) {
                // Fetch 7 days from selected date and cache them with delay
                fetchSevenDaysAndCache(lat, lon, currentDate)
            } else {
                binding.tvCurrentPrayer.text = "No data"
                binding.tvCurrentPrayerTime.text = ""
                Toast.makeText(
                    this@MainActivity,
                    "No cached data available. Connect to the internet.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    private suspend fun fetchSevenDaysAndCache(lat: String, lon: String, startDate: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.aladhan.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(ApiInterface::class.java)
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.time = sdf.parse(startDate)!!

        withContext(Dispatchers.IO) {
            for (i in 0 until 7) {
                val date = sdf.format(calendar.time)
                try {
                    val cached = db.salahDao().getValidSalahData(date, lat, lon, System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000))
                    if (cached != null) {
                        Log.d("CACHE", "✅ Already cached for $date")
                    } else {
                        val response = api.getSalahTime(date, lat, lon, 4).execute()
                        if (response.isSuccessful && response.body() != null) {
                            val json = Gson().toJson(response.body())
                            db.salahDao().insertSalahData(
                                CachedSalahData(
                                    date = date,
                                    latitude = lat,
                                    longitude = lon,
                                    dataJson = json,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            Log.d("CACHE", "✅ Cached $date")
                        } else {
                            Log.e("API", "❌ Failed $date: ${response.message()}")
                        }
                        delay(2000) // 2-second delay
                    }
                } catch (e: Exception) {
                    Log.e("CACHE", "❌ Exception for $date: ${e.localizedMessage}")
                }
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            withContext(Dispatchers.Main) {
                fetchSalahData() // Load current date after cache
            }
        }
    }


    private fun displaySalahData(responseBody: SalahTimeApp) {
        val hijriDay = responseBody.data.date.hijri.day
        val hijriMonth = responseBody.data.date.hijri.month.en
        val hijriYear = responseBody.data.date.hijri.year
        val timings = responseBody.data.timings

        binding.tvHijri.text = "$hijriDay $hijriMonth $hijriYear"
        binding.tvFajr.text = timings.Fajr
        binding.tvSunrise.text = timings.Sunrise
        binding.tvDhuhr.text = timings.Dhuhr
        binding.tvAsr.text = timings.Asr
        binding.tvSunset.text = timings.Sunset
        binding.tvMaghrib.text = timings.Maghrib
        binding.tvIsha.text = timings.Isha

        val prayerTimes = parsePrayerTimes(timings)
        val prayerInfo = getCurrentPrayerAndNext(prayerTimes)

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        binding.tvCurrentPrayer.text = prayerInfo.currentPrayer
        binding.tvCurrentPrayerTime.text = timeFormat.format(prayerInfo.currentPrayerTime.time)
        binding.tvNextPrayer.text = prayerInfo.nextPrayer
        binding.tvNextPrayerTime.text = timeFormat.format(prayerInfo.nextPrayerTime.time)
    }


    private fun parsePrayerTimes(timings: Timings): List<Pair<String, String>> {
        return listOf(
            "Fajr" to timings.Fajr,
            "Dhuhr" to timings.Dhuhr,
            "Asr" to timings.Asr,
            "Maghrib" to timings.Maghrib,
            "Isha" to timings.Isha
        )
    }

    data class PrayerInfo(
        val currentPrayer: String,
        val currentPrayerTime: Calendar,
        val nextPrayer: String,
        val nextPrayerTime: Calendar
    )

    private fun getCurrentPrayerAndNext(prayerTimes: List<Pair<String, String>>): PrayerInfo {
        val now = Calendar.getInstance()
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val today = Calendar.getInstance()

        for (i in prayerTimes.indices) {
            val time = prayerTimes[i].second.split(" ")[0]
            val prayerCal = today.clone() as Calendar
            val parsedTime = formatter.parse(time)
            if (parsedTime != null) {
                prayerCal.time = parsedTime
                prayerCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
                prayerCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
                prayerCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

                if (now.before(prayerCal)) {
                    if (i == 0) {
                        // Before Fajr
                        return PrayerInfo("None", now, prayerTimes[i].first, prayerCal)
                    } else {
                        // Between two prayers
                        val currentPrayerTime = today.clone() as Calendar
                        val previousTime = prayerTimes[i - 1].second.split(" ")[0]
                        val parsedPreviousTime = formatter.parse(previousTime)

                        if (parsedPreviousTime != null) {
                            currentPrayerTime.time = parsedPreviousTime
                            currentPrayerTime.set(Calendar.YEAR, today.get(Calendar.YEAR))
                            currentPrayerTime.set(Calendar.MONTH, today.get(Calendar.MONTH))
                            currentPrayerTime.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))
                        }

                        return PrayerInfo(
                            currentPrayer = prayerTimes[i - 1].first,
                            currentPrayerTime = currentPrayerTime,
                            nextPrayer = prayerTimes[i].first,
                            nextPrayerTime = prayerCal
                        )
                    }
                }
            }
        }

        // After Isha, next is tomorrow's Fajr
        val fajrCal = today.clone() as Calendar
        val fajrTime = formatter.parse(prayerTimes[0].second.split(" ")[0])
        if (fajrTime != null) {
            fajrCal.time = fajrTime
            fajrCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        val ishaTime = prayerTimes.last().second.split(" ")[0]
        val ishaCal = today.clone() as Calendar
        val parsedIsha = formatter.parse(ishaTime)
        if (parsedIsha != null) {
            ishaCal.time = parsedIsha
        }

        return PrayerInfo("Isha", ishaCal, "Fajr", fajrCal)
    }



    private fun showDatePicker() {
        val parts = currentDate.split("-")
        val day = parts[0].toInt()
        val month = parts[1].toInt() - 1
        val year = parts[2].toInt()

        val picker = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            currentDate = String.format("%02d-%02d-%04d", selectedDay, selectedMonth + 1, selectedYear)
            dateTextView.text = formatDateForDisplay(currentDate)

            lifecycleScope.launch {
                if (currentLatitude != null && currentLongitude != null && isNetworkAvailable(this@MainActivity)) {
                    fetchSevenDaysAndCache(currentLatitude!!, currentLongitude!!, currentDate)
                } else {
                    fetchSalahData()
                }
            }
        }, year, month, day)

        picker.show()
    }


    private fun getTodayDate(): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun formatDateForDisplay(date: String): String {
        return try {
            val input = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val output = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            val parsed = input.parse(date)
            output.format(parsed!!)
        } catch (e: Exception) {
            date
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
