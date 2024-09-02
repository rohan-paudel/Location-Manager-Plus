package com.slyyk.passenger.app.rohanutils.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.slyyk.rider.R


data class LocationData(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fullAddress: String? = null,
    val zipCode: String? = null
)

object LocationDataHolder {
    val locationLiveData: MutableLiveData<LocationData?> = MutableLiveData()
}

/**
 * LocationUtils is a utility class that manages location permissions and prompts users
 * to enable foreground and background location services as required by the application.
 *
 * @property context The context of the calling activity or application.
 * @property isForegroundLocationRequired A boolean indicating whether foreground location permission is required.
 * @property isBackgroundLocationRequired A boolean indicating whether background location permission is required.
 *
 * @constructor Creates a LocationUtils instance with the specified parameters.
 *
 * @throws IllegalArgumentException If background location is required but foreground location is not.
 */
class LocationUtils(
    private val context: Context,
    private var isForegroundLocationRequired: Boolean = false,
    private var isBackgroundLocationRequired: Boolean = false
) {
    companion object {
        private const val TAG = "RohanLocationUtils"
        private const val COUNT_OF_FOREGROUND_LOCATION_REQUEST_KEY =
            "count_of_foreground_location_request_key"
        private const val COUNT_OF_BACKGROUND_LOCATION_REQUEST_KEY =
            "count_of_background_location_request_key"

    }


    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var isRequestingLocationByGetDeviceLocationFunction = false
    private var isPendingFunctionRun = false

    private var pendingFunction: String = ""

    private var isBroadcastDeviceLocationStream: Boolean = false

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    }

    private fun getPreference(context: Context, key: String): String {
        val prefs: SharedPreferences = getSharedPreferences(context)
        return prefs.getString(key, "")!!
    }

    private fun setPreference(context: Context, key: String, value: String) {
        val settings: SharedPreferences = getSharedPreferences(context)
        val editor = settings.edit()
        editor.putString(key, value)
        editor.apply()
    }


    init {
        require(!(isBackgroundLocationRequired && !isForegroundLocationRequired)) {
            "Foreground location must be required when background location is required."
        }
    }

    private val locationPermissionLauncher =
        (context as AppCompatActivity).registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            showPopup()
        }

    private val appSettingsLauncher = (context as AppCompatActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        showPopup()
    }

    private val resolutionForResult =
        (context as AppCompatActivity).registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            when (result.resultCode) {

                AppCompatActivity.RESULT_OK -> {
                    if (isBroadcastDeviceLocationStream) {
                        broadcastDeviceLocationStream()
                    } else {
                        broadcastDeviceLocation()
                    }

                }

                AppCompatActivity.RESULT_CANCELED -> {
                    Log.i("TAG", "User chose not to make required location settings changes.")

                }

            }
        }

    private fun showPopup() {
        if (isRequestingLocationByGetDeviceLocationFunction && shouldAskPermission()) {
            processWhatToShowForForegroundLocation()
            isRequestingLocationByGetDeviceLocationFunction = false
        } else if (isForegroundLocationRequired && isAskingPermissionRequired().first) {
            processWhatToShowForForegroundLocation()
        } else if (isBackgroundLocationRequired && isAskingPermissionRequired().second) {
            processWhatToShowForBackgroundLocation()
        } else if (isPendingFunctionRun) {
            val pendingFunctionName = pendingFunction
            resetPendingFunction()
            when (pendingFunctionName) {
                "broadcastDeviceLocation" -> {
                    broadcastDeviceLocation()
                }
                "broadcastDeviceLocationStream" -> {
                    broadcastDeviceLocationStream()
                }
            }
        }
    }

    private fun resetPendingFunction() {
        pendingFunction = ""
        isPendingFunctionRun = false
    }

    private fun calculateForegroundLocationRequestCount(): Int {
        var totalCount = getPreference(context, COUNT_OF_FOREGROUND_LOCATION_REQUEST_KEY)
        if (totalCount == "") {
            totalCount = "0"
            return totalCount.toInt()
        } else {
            return totalCount.toInt()
        }
    }

    private fun increaseForegroundTotalCount(totalCount: Int) {
        setPreference(context, COUNT_OF_FOREGROUND_LOCATION_REQUEST_KEY, "${totalCount + 1}")
    }

    private fun calculateBackgroundLocationRequestCount(): Int {
        var totalCount = getPreference(context, COUNT_OF_BACKGROUND_LOCATION_REQUEST_KEY)
        if (totalCount == "") {
            totalCount = "0"
            return totalCount.toInt()
        } else {
            return totalCount.toInt()
        }
    }

    private fun increaseBackgroundTotalCount(totalCount: Int) {
        setPreference(context, COUNT_OF_BACKGROUND_LOCATION_REQUEST_KEY, "${totalCount + 1}")
    }

    private fun processWhatToShowForForegroundLocation() {
        val totalCount = calculateForegroundLocationRequestCount()
        when (totalCount) {
            0 -> {
                requestForegroundLocationPopup()
            }

            1 -> {
                MaterialAlertDialogBuilder(context).setTitle("Location service")
                    .setMessage("Location service is required to run this app. Please provide accurate location to continue.")
                    .setCancelable(false).setPositiveButton("Yes") { dialog, which ->
                        requestForegroundLocationPopup()
                    }.show()

            }

            else -> {
                MaterialAlertDialogBuilder(context).setTitle("Location service")
                    .setMessage("Location service is required to run this app. Please open settings to give permission of location service.")
                    .setCancelable(false).setPositiveButton("Open settings") { dialog, which ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:" + context.packageName)
                        }
                        appSettingsLauncher.launch(intent)
                    }.show()
            }
        }
    }

    private fun requestForegroundLocationPopup() {
        val totalCount = calculateForegroundLocationRequestCount()
        increaseForegroundTotalCount(totalCount)
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    private fun processWhatToShowForBackgroundLocation() {
        if (isBackgroundLocationRequired && isAskingPermissionRequired().second) {
            val totalCount = calculateBackgroundLocationRequestCount()
            when (totalCount) {
                0 -> {
                    MaterialAlertDialogBuilder(context).setTitle("Location service")
                        .setMessage("Background Location service is required").setCancelable(false)
                        .setPositiveButton("Enable") { dialog, which ->
                            requestBackgroundLocationPermissionIfNeeded()
                        }.show()
                }

                1 -> {
                    MaterialAlertDialogBuilder(context).setTitle("Location service")
                        .setMessage("Background Location service is required").setCancelable(false)
                        .setPositiveButton("Enable") { dialog, which ->
                            requestBackgroundLocationPermissionIfNeeded()
                        }.show()
                }

                else -> {
                    MaterialAlertDialogBuilder(context).setTitle("Location service")
                        .setMessage("Background Location service is required. Please open settings to give permission of location service. Make sure to choose Allow all the time.")
                        .setCancelable(false).setPositiveButton("Open settings") { dialog, which ->
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:" + context.packageName)
                                }
                            appSettingsLauncher.launch(intent)
                        }.show()
                }
            }
        }
    }


    @SuppressLint("ObsoleteSdkInt")
    private fun requestBackgroundLocationPermissionIfNeeded() {
        val totalCount = calculateBackgroundLocationRequestCount()
        increaseBackgroundTotalCount(totalCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            )
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun isAskingPermissionRequired(): Triple<Boolean, Boolean, Boolean> {
        var foregroundAsk = false
        var backgroundAsk = false
        var isAskRequired = false

        if (isForegroundLocationRequired) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                foregroundAsk = true
                isAskRequired = true
            } else {
                if (isBackgroundLocationRequired) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        backgroundAsk = true
                        isAskRequired = true
                    }
                }
            }
            return Triple(foregroundAsk, backgroundAsk, isAskRequired)

        } else {
            if (isRequestingLocationByGetDeviceLocationFunction) {
                if (ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    foregroundAsk = true
                    isAskRequired = true
                } else {
                    isRequestingLocationByGetDeviceLocationFunction = false
                }
            } else {
                Log.d(
                    TAG,
                    "The total request asked to user for the location is ${calculateForegroundLocationRequestCount()}. Only for the first time this will return true if foreground location is not required."
                )
                isAskRequired = calculateForegroundLocationRequestCount() == 0
            }
            return Triple(foregroundAsk, false, isAskRequired)

        }
    }

    /**
     * Determines whether the app should ask the user for location permissions.
     *
     * @return `true` if asking for permission is required, `false` otherwise.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun shouldAskPermission(): Boolean = isAskingPermissionRequired().third


    /**
     * Requests the necessary location permissions from the user if required.
     *
     * This function first checks if the app should ask for location permissions by calling
     * `shouldAskPermission()`. If the result is `true`, it will display the appropriate
     * popup to guide the user through granting the required permissions.
     */
    fun askForPermission(
    ) {
        if (shouldAskPermission()) {
            showPopup()
        }
    }

    /**
     * Broadcasts the device's location once.
     *
     * This function is used to get the device's current location a single time and broadcast it.
     * It first sets a flag `isRequestingLocationByGetDeviceLocationFunction` to `true`, indicating
     * that the location is being requested via the `broadcastDeviceLocation` function. It also
     * disables continuous location broadcasting by setting `isBroadcastDeviceLocationStream` to `false`.
     *
     * Before broadcasting the location, it checks if the app needs to ask for location permissions
     * by calling `shouldAskPermission()`. If permissions are required, it delays the location
     * broadcast by storing the function name (`broadcastDeviceLocation`) for later execution via
     * `makePendingFunctionRun()`, and displays the permission popup.
     *
     * If permissions are not required, it proceeds to check the device's location settings and
     * starts broadcasting the device's current location.
     */
    fun broadcastDeviceLocation() {
        isRequestingLocationByGetDeviceLocationFunction = true
        isBroadcastDeviceLocationStream = false

        if (shouldAskPermission()) {
            makePendingFunctionRun("broadcastDeviceLocation")
            showPopup()
            return
        }
        checkLocationSettings()
    }

    /**
     * Broadcasts the device's location continuously (as a stream).
     *
     * This function is used to request the device's location on an ongoing basis, continuously
     * broadcasting it as a stream of location updates. It sets flags `isBackgroundLocationRequired`
     * and `isForegroundLocationRequired` to `true`, indicating that both background and foreground
     * location access are needed. It also sets `isBroadcastDeviceLocationStream` to `true` to
     * enable continuous location streaming.
     *
     * Before starting the continuous location stream, it checks if the app needs to ask for
     * location permissions by calling `shouldAskPermission()`. If permissions are required, it
     * delays the location streaming by storing the function name (`broadcastDeviceLocationStream`)
     * for later execution via `makePendingFunctionRun()`, and displays the permission popup.
     *
     * If permissions are already granted, it proceeds to check the device's location settings and
     * initiates the continuous broadcasting of location updates.
     */
    fun broadcastDeviceLocationStream() {
        isBackgroundLocationRequired = true
        isForegroundLocationRequired = true
        isBroadcastDeviceLocationStream = true
        if (shouldAskPermission()) {
            makePendingFunctionRun("broadcastDeviceLocationStream")
            showPopup()
            return
        }
        checkLocationSettings()
    }

    /**
     * Stops broadcasting the device's location stream.
     *
     * This function is used to stop the continuous streaming of the device's location updates.
     * It creates an intent for the `LocationService` and stops the service, which effectively
     * halts any ongoing location updates.
     */
    fun stopBroadcastingDeviceLocationStream() {
        val intent = Intent(context, LocationService::class.java)
        context.stopService(intent)
    }

    private fun checkLocationSettings() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000).build()

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val settingsClient = LocationServices.getSettingsClient(context)
        val task = settingsClient.checkLocationSettings(builder.build())

        task.addOnCompleteListener { taskResult ->
            try {
                if (isBroadcastDeviceLocationStream) {
                    val intent = Intent(context, LocationService::class.java)
                    context.startForegroundService(intent)

                } else {
                    runOnceLocation()
                }

            } catch (exception: ApiException) {
                when (exception.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        try {
                           exception as ResolvableApiException
                            val intentSenderRequest =
                                IntentSenderRequest.Builder(exception.resolution).build()
                            resolutionForResult.launch(intentSenderRequest)
                        } catch (e: IntentSender.SendIntentException) {
                            Log.i("TAG", "PendingIntent unable to execute request.")
                        } catch (e: ClassCastException) {
                            Log.i("TAG", "ClassCastException: ${e.message}")
                        }
                    }

                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        Log.i(
                            "TAG",
                            "Location settings are inadequate, and cannot be fixed here. Dialog not created."
                        )
                    }
                }
            }
        }
    }


    private fun runOnceLocation() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted || coarseLocationGranted) {
            getCurrentLocation(fineLocationGranted, context)
        } else {
            LocationDataHolder.locationLiveData.postValue(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(isFineLocation: Boolean, context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val priority = if (isFineLocation) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                location?.let {
                    val lat = it.latitude
                    val lng = it.longitude

                    if (isFineLocation) {
                        Log.d(TAG, "getCurrentLocation: $lat, $lng")
                        LocationDataHolder.locationLiveData.postValue(LocationData(lat, lng))
                    } else {
                        val approxLat = Math.round(lat * 100000.0) / 100000.0
                        val approxLng = Math.round(lng * 100000.0) / 100000.0
                        Log.d(TAG, "getCurrentLocation: $approxLat, $approxLng")
                        LocationDataHolder.locationLiveData.postValue(
                            LocationData(
                                approxLat, approxLng
                            )
                        )
                    }

                } ?: run {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                        lastLocation?.let {
                            val lat = it.latitude
                            val lng = it.longitude

                            if (isFineLocation) {
                                Log.d(TAG, "getCurrentLocation: $lat, $lng")
                                LocationDataHolder.locationLiveData.postValue(
                                    LocationData(
                                        lat, lng
                                    )
                                )
                            } else {
                                val approxLat = Math.round(lat * 1000.0) / 1000.0
                                val approxLng = Math.round(lng * 1000.0) / 1000.0
                                Log.d(TAG, "getCurrentLocation: $approxLat, $approxLng")
                                LocationDataHolder.locationLiveData.postValue(
                                    LocationData(
                                        approxLat, approxLng
                                    )
                                )
                            }
                        } ?: run {
                            Log.d(TAG, "getCurrentLocation: null")
                            LocationDataHolder.locationLiveData.postValue(null)
                        }
                    }
                }
            }.addOnFailureListener {
                Log.d(TAG, "getCurrentLocation: null")
                LocationDataHolder.locationLiveData.postValue(null)
            }
    }


    private fun makePendingFunctionRun(functionName: String) {
        pendingFunction = functionName
        isPendingFunctionRun = true
    }


}


class LocationService : Service() {
    private val CHANNEL_ID = "location_channel_id"
    private val NOTIFICATION_ID = 1

    private val locationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }


    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.let {
                val location = it.lastLocation
                broadcastLocation(location)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        removeNotification()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2 * 1000L
        ).apply {
            setMinUpdateIntervalMillis(2 * 1000L)
            setWaitForAccurateLocation(true)
        }.build()
        locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
    }

    private fun stopLocationUpdates() {
        locationClient.removeLocationUpdates(locationCallback)
    }

    private fun startForegroundService() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, BgLocationAccessActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Location Service")
            .setContentText("Location updates are active")
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setOngoing(true).build()
    }

    private fun removeNotification() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Location Service Channel", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Channel for location service"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun broadcastLocation(location: Location?) {
        if (location != null) {
            val intent = Intent("LOCATION_UPDATE").apply {
                putExtra("latitude", location.latitude)
                putExtra("longitude", location.longitude)
            }
            sendBroadcast(intent)
            LocationDataHolder.locationLiveData.postValue(
                LocationData(
                    location.latitude, location.longitude
                )
            )
            Log.e(
                "LocationService",
                "Location broadcast sent: ${location.latitude}, ${location.longitude}"
            )
        } else {
            Log.e("LocationService", "Cannot broadcast location")
            LocationDataHolder.locationLiveData.postValue(null)
        }

    }
}