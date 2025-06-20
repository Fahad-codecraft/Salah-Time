package com.example.salahtime

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationTextView = findViewById(R.id.tv_location)
        dateTextView = findViewById(R.id.tv_date)

        val fetchButton: Button = findViewById(R.id.myButton)
        fetchButton.setOnClickListener {
            checkLocationPermissionAndFetch()
        }

        // Display formatted date
        dateTextView.text = formatDateForDisplay(currentDate)

        dateTextView.setOnClickListener {
            showDatePicker()
        }

        // Fetch location on app start
        checkLocationPermissionAndFetch()
    }

    private fun checkLocationPermissionAndFetch() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            requestNewLocationData()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            getLastLocation()
        }
    }

    private fun getLastLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location == null) {
                        Log.w("location", "Location is null, requesting new one...")
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
                } else {
                    Log.e("location", "Fresh location is still null")
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
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
                    val time = responseBody.data.timings.Fajr
                    Log.d("API", "Fajr time on $currentDate: $time")
                } else {
                    Log.e("API", "API call failed: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<SalahTimeApp>, t: Throwable) {
                Log.e("API", "Network error: ${t.localizedMessage}")
            }
        })
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
}
