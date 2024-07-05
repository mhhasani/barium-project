package com.example.barium

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var locationManager: LocationManager
    private lateinit var textView: TextView
    private lateinit var phoneNumberEditText: EditText
    private lateinit var thresholdEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var logRecyclerView: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private var signalThreshold = -100 // Default threshold for signal strength in dBm
    private var currentLocation: Location? = null
    private var previousSignalState: Boolean? = null
    private val handler = Handler(Looper.getMainLooper())
    private var userPhoneNumber: String? = null
    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1
        private const val SIGNAL_CHECK_INTERVAL = 1000L // 1 second
        private const val MAX_SIGNAL_STRENGTH = -30 // Maximum realistic signal strength in dBm
        private const val MIN_SIGNAL_STRENGTH = -120 // Minimum realistic signal strength in dBm
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupSaveButton()
        initializeLocationManager()
        requestPermissions()
        startSignalStrengthCheck()
    }

    private fun initializeViews() {
        latitudeTextView = findViewById(R.id.latitudeTextView)
        longitudeTextView = findViewById(R.id.longitudeTextView)
        textView = findViewById(R.id.textView)
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText)
        thresholdEditText = findViewById(R.id.thresholdEditText)
        saveButton = findViewById(R.id.saveButton)
        logRecyclerView = findViewById(R.id.logRecyclerView)
        logAdapter = LogAdapter()
        logRecyclerView.layoutManager = LinearLayoutManager(this)
        logRecyclerView.adapter = logAdapter
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            userPhoneNumber = phoneNumberEditText.text.toString()
            signalThreshold = thresholdEditText.text.toString().toInt()
            updateStateInfo()
        }
    }

    private fun initializeLocationManager() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startLocationUpdates()
        } else {
            Log.e(TAG, "Permissions not granted")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L, // حداقل فاصله زمانی بین به‌روزرسانی‌ها (1 ثانیه)
                0f, // حداقل فاصله بین به‌روزرسانی‌ها (0 متر)
                locationListener
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                1000L, // حداقل فاصله زمانی بین به‌روزرسانی‌ها (1 ثانیه)
                0f, // حداقل فاصله بین به‌روزرسانی‌ها (0 متر)
                locationListener
            )
            updateLocationWithLastKnownLocation()
        }
    }

    private fun updateLocationWithLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val lastKnownGPSLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastKnownNetworkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (lastKnownGPSLocation != null && (lastKnownNetworkLocation == null || lastKnownGPSLocation.time > lastKnownNetworkLocation.time)) {
                currentLocation = lastKnownGPSLocation
            } else if (lastKnownNetworkLocation != null) {
                currentLocation = lastKnownNetworkLocation
            }

            updateStateInfo()
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            updateStateInfo()
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Log.d(TAG, "Status changed: $status")
        }

        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "Provider enabled: $provider")
        }

        override fun onProviderDisabled(provider: String) {
            Log.d(TAG, "Provider disabled: $provider")
        }
    }

    private fun sendSms(phoneNumber: String, message: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SMS permission not granted")
            return
        }
        try {
            val smsManager = SmsManager.getDefault()
            val finalMessage = "1234: $message" // Adding a simple password for security
            val messageParts = smsManager.divideMessage(finalMessage)
            smsManager.sendMultipartTextMessage(phoneNumber, null, messageParts, null, null)
            logAdapter.addLog("SMS sent to $phoneNumber: $finalMessage")
            Log.d(TAG, "SMS sent: $finalMessage")
        } catch (e: Exception) {
            Log.e(TAG, "SMS sending failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCellInfo(): String {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "Permission not granted"
        }

        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val cellInfoList: List<CellInfo> = telephonyManager.allCellInfo

        if (cellInfoList.isNotEmpty()) {
            val cellInfo = cellInfoList[0]
            return when (cellInfo) {
                is CellInfoGsm -> "GSM Cell ID: ${cellInfo.cellIdentity.cid}"
                is CellInfoLte -> "LTE Cell ID: ${cellInfo.cellIdentity.ci}"
                is CellInfoCdma -> "CDMA Cell ID: ${cellInfo.cellIdentity.basestationId}"
                is CellInfoWcdma -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    "WCDMA Cell ID: ${cellInfo.cellIdentity.cid}"
                } else {
                    "WCDMA Cell ID: Not available"
                }
                else -> "Unknown Cell Info"
            }
        }
        return "No Cell Info Available"
    }


    @SuppressLint("MissingPermission")
    private fun getSignalStrength(): Int {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return MIN_SIGNAL_STRENGTH
        }

        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val cellInfoList: List<CellInfo> = telephonyManager.allCellInfo

        if (cellInfoList.isNotEmpty()) {
            val cellInfo = cellInfoList[0]
            val signalStrength = when (cellInfo) {
                is CellInfoGsm -> cellInfo.cellSignalStrength.dbm
                is CellInfoLte -> cellInfo.cellSignalStrength.dbm
                is CellInfoCdma -> cellInfo.cellSignalStrength.dbm
                is CellInfoWcdma -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    cellInfo.cellSignalStrength.dbm
                } else {
                    MIN_SIGNAL_STRENGTH
                }
                else -> MIN_SIGNAL_STRENGTH
            }

            if (signalStrength in MIN_SIGNAL_STRENGTH..MAX_SIGNAL_STRENGTH) {
                return signalStrength
            }
        }
        return MIN_SIGNAL_STRENGTH
    }

    private fun getLocationInfo(): Pair<String, String> {
        currentLocation?.let {
            val latitude = "Lat: ${it.latitude}"
            val longitude = "Lng: ${it.longitude}"
            return Pair(latitude, longitude)
        }
        return Pair("Lat: Not available", "Lng: Not available")
    }


    private fun checkSignalStrength() {
        val signalStrength = getSignalStrength()
        updateStateInfo()
        val currentState = signalStrength != -1 && signalStrength < signalThreshold

        if (previousSignalState == null) {
            previousSignalState = currentState
            return
        }

        if (currentState != previousSignalState) {
            val locationInfo = getLocationInfo()
            val cellInfo = getCellInfo()
            val message = if (currentState) {
                "Warning: Signal strength is low: $signalStrength dBm, Location: $locationInfo, Cell Info: $cellInfo"
            } else {
                "Info: Signal strength is back to normal: $signalStrength dBm, Location: $locationInfo, Cell Info: $cellInfo"
            }
            userPhoneNumber?.let { sendSms(it, message) }
            previousSignalState = currentState
        }
    }

    private fun updateStateInfo() {
        val (latitude, longitude) = getLocationInfo()
        val cellInfo = getCellInfo()
        val signalStrength = getSignalStrength()
        val signalStrengthInfo = if (signalStrength == MIN_SIGNAL_STRENGTH) "Signal strength not available" else "$signalStrength dBm"
        latitudeTextView.text = latitude
        longitudeTextView.text = longitude
        val stateInfo = "Cell Info: $cellInfo\nSignal Strength: $signalStrengthInfo\nThreshold: $signalThreshold dBm"
        textView.text = stateInfo
    }


    private fun startSignalStrengthCheck() {
        handler.post(object : Runnable {
            override fun run() {
                checkSignalStrength()
                updateStateInfo() // اضافه کردن این خط برای اطمینان از به‌روزرسانی هر ثانیه
                handler.postDelayed(this, SIGNAL_CHECK_INTERVAL)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // Stop the periodic task when activity is destroyed
    }
}
