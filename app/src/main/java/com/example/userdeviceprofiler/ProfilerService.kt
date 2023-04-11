package com.example.userdeviceprofiler

import android.annotation.SuppressLint
import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileWriter
import java.util.*


class UserUsageData {
    // Information about the current foreground app.
    var packageName: String? = null
    var memoryUsed: Long? = null
    var lastTimeUsed: Long? = null
    var lastTimeVisible: Long? = null
    var totalTimeVisible: Long? = null
    var totalTimeInForeground: Long? = null
    var firstTimeUsed: Long? = null
    var totalTimeForegroundServiceUsed: Long? = null
    var lastTimeForegroundServiceUsed: Long? = null

}

class ProfilerService : Service() {
    private var timer: Timer? = null
    private lateinit var notificationManager: NotificationManager
    private var usageStatsManager: UsageStatsManager? = null
    private var activityManager: ActivityManager? = null
    private var systemDataList: MutableList<String> = mutableListOf()
    private var lastGpsCollected:Long = 0
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var accuracy: Float = 0.0f

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null && intent.action.equals(
                "STOP", ignoreCase = true
            )
        ) {
            timer?.cancel()
            stopSelf()
            IS_RUNNING = false
            return START_NOT_STICKY
        }

        if (intent?.action != null && intent.action.equals("START", ignoreCase = true)) {
            showNotification()

            if (timer == null) {
                timer = Timer()
                timer?.schedule(object : TimerTask() {
                    override fun run() {
                        profile()
                    }
                }, 0, 10000) // 10 seconds
            }

            IS_RUNNING = true
        }

        return START_STICKY
    }

    private fun profileUserData(currentTime: Long) {
        if (usageStatsManager == null) {
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        }

        // Get the current foreground app and its importance
        val interval = 1000 * 10

        val stats = usageStatsManager!!.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - interval, // 10 sec period
            currentTime
        )

        // Create a map that takes the package name as key and UserUsageData as value
        val userUsageDataList = mutableListOf<UserUsageData>()
        // Get the current foreground app information and store their data list in form of UserUsageData
        for (usageStats in stats) {
            val userUsageData = UserUsageData()
            userUsageData.packageName = usageStats.packageName
            // TODO: calculate memory amount used.
            userUsageData.memoryUsed = usageStats.totalTimeInForeground
            userUsageData.lastTimeUsed = usageStats.lastTimeUsed
            userUsageData.totalTimeInForeground = usageStats.totalTimeInForeground
            userUsageData.firstTimeUsed = usageStats.firstTimeStamp

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                userUsageData.lastTimeForegroundServiceUsed =
                    usageStats.lastTimeForegroundServiceUsed
                userUsageData.totalTimeForegroundServiceUsed =
                    usageStats.totalTimeForegroundServiceUsed
                userUsageData.lastTimeVisible = usageStats.lastTimeVisible
                userUsageData.totalTimeVisible = usageStats.totalTimeVisible
            }

            userUsageDataList.add(userUsageData)
        }

        val query = UsageEvents.Event.NONE
        val usageEvents = mutableListOf<UsageEvents.Event>()
        val usageEventsIterator = usageStatsManager!!.queryEvents(currentTime - interval, currentTime)
        while (usageEventsIterator.hasNextEvent()) {
            val event = UsageEvents.Event()
            usageEventsIterator.getNextEvent(event)
            if (event.eventType == query) {
                usageEvents.add(event)
            }
        }

        // Process the retrieved usage events
        val eventList = mutableListOf<String>()
        eventList.add("timestamp,packageName,timestamp,eventType,standbyBucket\n")
        for (event in usageEvents) {
            val packageName = event.packageName
            val timestamp = event.timeStamp
            val eventType = event.eventType
            val standbyBucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                event.appStandbyBucket
            } else {
                -1
            }

            val eventString = "$currentTime,$packageName,$timestamp,$eventType,$standbyBucket\n"
            eventList.add(eventString)
        }

        // Save userdata list to a csv file named 'user_{timestamp}.csv'
        val userUsageDataString = mutableListOf<String>()
        val header = "timestamp,packageName,memoryUsed,lastTimeUsed,totalTimeInForeground,firstTimeUsed,lastTimeForegroundServiceUsed,totalTimeForegroundServiceUsed,lastTimeVisible,totalTimeVisible\n"
        userUsageDataString.add(header)

        for (userUsageData in userUsageDataList) {
            val userUsageDataLine = "$currentTime,${userUsageData.packageName},${userUsageData.memoryUsed},${userUsageData.lastTimeUsed},${userUsageData.totalTimeInForeground},${userUsageData.firstTimeUsed},${userUsageData.lastTimeForegroundServiceUsed},${userUsageData.totalTimeForegroundServiceUsed},${userUsageData.lastTimeVisible},${userUsageData.totalTimeVisible}\n"
            userUsageDataString.add(userUsageDataLine)
        }

        saveFileInBackground("user_${currentTime}.csv", userUsageDataString.toTypedArray())
        saveFileInBackground("events_${currentTime}.csv", eventList.toTypedArray())
    }


    @SuppressLint("MissingPermission")
    private fun profileSystemData(currentTime: Long) {
        // Get the current GPS information
        if (currentTime - lastGpsCollected > 1000 * 60 * 5) { // 5 minutes
            lastGpsCollected = currentTime
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            // TODO: Ensure the GPS permission is set to be always on, not only when the app is in use.
            if (location != null) {
                latitude = location.latitude
                longitude = location.longitude
                accuracy = location.accuracy
            }
        }

        // Get the current battery level
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val isCharging = batteryManager.isCharging
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batterOverheat = batteryManager.getIntProperty(BatteryManager.BATTERY_HEALTH_OVERHEAT)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = applicationContext.registerReceiver(null, filter)
        val temperature = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)

        // Get the screen brightness
        val screenBrightness = Settings.System.getInt(
            applicationContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            0
        )

        // Get if the screen is on or off.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = powerManager.isInteractive

        val data = "$currentTime,$longitude,$latitude,$accuracy,$isCharging,$batteryLevel,$batterOverheat,$temperature,$screenBrightness,$isScreenOn\n"
        systemDataList.add(data)

        if (systemDataList.size >= 10) {
            // Save the data collected above as a csv file named 'system_{timestamp}.csv'
            systemDataList.add(0, "timestamp,longitude,latitude,accuracy,isCharging,batteryLevel,batterOverheat,temperature,screenBrightness,isScreenOn\n")
            saveFileInBackground("system_${currentTime}.csv", systemDataList.toTypedArray())
            systemDataList.clear()
        }
    }

    private fun saveFileInBackground(fileName: String, data: Array<String>) {
        val thread = Thread {
            // Do file saving operations in background thread
            val file = File(getExternalFilesDir(null), fileName)
            val writer = FileWriter(file)
            for (dataLine in data) {
                writer.append(dataLine)
            }

            writer.close()
        }

        thread.start()
    }

    private fun profile() {
        val currentTime = System.currentTimeMillis()

        profileUserData(currentTime)
        profileSystemData(currentTime)
    }

    private fun createNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(SERVICE_CHANNEL_ID) != null) {
            return
        }

        val channel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            applicationContext.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "EIS Profiler Notification Channel"
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification() {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentTitle("EISProfiler")
            .setContentText("Profiler is running")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
    }

    companion object {
        private var is_running: Boolean = false
        var IS_RUNNING: Boolean
            get() {
                return is_running
            }
            set(is_running) {
                this.is_running = is_running
            }
        
        private const val ONGOING_NOTIFICATION_ID = 18
        private const val SERVICE_CHANNEL_ID = "profiler_service_channel_id"
    }
}

private operator fun <T> Array<T>.component6() = get(5)
