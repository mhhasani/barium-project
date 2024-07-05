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
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener)
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
                is CellInfoGsm -> "GSM Cell Info: ${cellInfo.cellIdentity}"
                is CellInfoLte -> "LTE Cell Info: ${cellInfo.cellIdentity}"
                is CellInfoCdma -> "CDMA Cell Info: ${cellInfo.cellIdentity}"
                is CellInfoWcdma -> "WCDMA Cell Info: ${cellInfo.cellIdentity}"
                else -> "Unknown Cell Info"
            }
        }
        return "No Cell Info Available"
    }

    @SuppressLint("MissingPermission")
    private fun getSignalStrength(): Int {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return -1 // Permission not granted
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
                    -1 // Not available
                }
                else -> -1 // Unknown signal strength
            }

            if (signalStrength in MIN_SIGNAL_STRENGTH..MAX_SIGNAL_STRENGTH) {
                return signalStrength
            }
        }
        return -1 // No valid signal strength available
    }

    private fun getLocationInfo(): String {
        currentLocation?.let {
            return "Lat: ${it.latitude}, Lng: ${it.longitude}"
        }
        return "Location not available"
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
        val locationInfo = getLocationInfo()
        val cellInfo = getCellInfo()
        val signalStrength = getSignalStrength()
        val stateInfo = "Location: $locationInfo\nCell Info: $cellInfo\nSignal Strength: $signalStrength dBm\nThreshold: $signalThreshold dBm"
        textView.text = stateInfo
    }

    private fun startSignalStrengthCheck() {
        handler.post(object : Runnable {
            override fun run() {
                checkSignalStrength()
                handler.postDelayed(this, SIGNAL_CHECK_INTERVAL)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // Stop the periodic task when activity is destroyed
    }
}