package com.example.salahtime

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.salahtime.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationTextView: TextView
    private lateinit var dateTextView: TextView

    private val LOCATION_PERMISSION_REQUEST = 1001

    private var currentLatitude: String? = null
    private var currentLongitude: String? = null
    private var currentDate: String = getTodayDate()

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var countdownHandler: Handler? = null
    private var countdownRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationTextView = findViewById(R.id.tv_location)
        dateTextView = findViewById(R.id.tv_date)

        val fetchButton: Button = findViewById(R.id.myButton)
        fetchButton.setOnClickListener {
            checkLocationPermissionAndFetch()
        }

        dateTextView.text = formatDateForDisplay(currentDate)

        dateTextView.setOnClickListener {
            showDatePicker()
        }

        checkLocationPermissionAndFetch()
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
        val lat = location.latitude
        val lon = location.longitude
        currentLatitude = lat.toString()
        currentLongitude = lon.toString()
        locationTextView.text = "Lat: $lat, Lon: $lon"
        fetchSalahData()
    }

    private fun fetchSalahData() {
        if (currentLatitude == null || currentLongitude == null) {
            Log.e("fetch", "Location not ready")
            return
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.aladhan.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(ApiInterface::class.java)
        val call = api.getSalahTime(currentDate, currentLatitude!!, currentLongitude!!, 4)

        call.enqueue(object : Callback<SalahTimeApp> {
            override fun onResponse(call: Call<SalahTimeApp>, response: Response<SalahTimeApp>) {
                val responseBody = response.body()
                if (response.isSuccessful && responseBody != null) {
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
                    val (currentPrayer, nextPrayer, nextPrayerTimeCal) = getCurrentPrayerAndNext(prayerTimes)

                    binding.tvCurrentPrayer.text = currentPrayer
                    binding.tvNextPrayer.text = nextPrayer
                    binding.tvNextPrayerTime.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(nextPrayerTimeCal.time)

                    startCountdown(nextPrayerTimeCal)
                } else {
                    Log.e("API", "API call failed: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<SalahTimeApp>, t: Throwable) {
                Log.e("API", "Network error: ${t.localizedMessage}")
            }
        })
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

    private fun getCurrentPrayerAndNext(prayerTimes: List<Pair<String, String>>): Triple<String, String, Calendar> {
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
                    val currentPrayer = if (i == 0) "None" else prayerTimes[i - 1].first
                    val nextPrayer = prayerTimes[i].first
                    return Triple(currentPrayer, nextPrayer, prayerCal)
                }
            }
        }

        val fajrCal = today.clone() as Calendar
        val fajrTime = formatter.parse(prayerTimes[0].second.split(" ")[0])
        if (fajrTime != null) {
            fajrCal.time = fajrTime
            fajrCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return Triple("Isha", "Fajr", fajrCal)
    }

    private fun startCountdown(targetTime: Calendar) {
        countdownHandler?.removeCallbacks(countdownRunnable ?: Runnable { })

        countdownHandler = Handler(Looper.getMainLooper())
        countdownRunnable = object : Runnable {
            override fun run() {
                val now = Calendar.getInstance()
                val diff = targetTime.timeInMillis - now.timeInMillis

                if (diff > 0) {
                    binding.tvTimeRemaining.text = formatDuration(diff)
                    countdownHandler?.postDelayed(this, 1000)
                } else {
                    binding.tvTimeRemaining.text = "Now"
                    fetchSalahData()
                }
            }
        }

        countdownHandler?.post(countdownRunnable!!)
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = (seconds / 60) % 60
        val hours = seconds / 3600
        val secs = seconds % 60

        return String.format("in %02dh %02dm %02ds", hours, minutes, secs)
    }

    private fun showDatePicker() {
        val parts = currentDate.split("-")
        val day = parts[0].toInt()
        val month = parts[1].toInt() - 1
        val year = parts[2].toInt()

        val picker = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            currentDate = String.format("%02d-%02d-%04d", selectedDay, selectedMonth + 1, selectedYear)
            dateTextView.text = formatDateForDisplay(currentDate)
            fetchSalahData()
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
        countdownHandler?.removeCallbacks(countdownRunnable ?: Runnable { })
        super.onDestroy()
    }
}
