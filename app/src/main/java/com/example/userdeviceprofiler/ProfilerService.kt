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
import com.example.userdeviceprofiler.data.UserUsageData

class ProfilerService : Service() {
    private var timer: Timer? = null
    private lateinit var notificationManager: NotificationManager
    private var usageStatsManager: UsageStatsManager? = null
    private var activityManager: ActivityManager? = null
    private var lastGpsCollected:Long = 0
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var accuracy: Float = 0.0f
    private var userRecords: MutableList<String> = mutableListOf()
    private var eventRecords: MutableList<String> = mutableListOf()
    private var systemRecords: MutableList<String> = mutableListOf()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null && intent.action.equals(
                "STOP", ignoreCase = true
            )
        ) {
            //Flush current records to file
            flushRecords()
            timer?.cancel()
            stopSelf()
            IS_RUNNING = false
            return START_NOT_STICKY
        }

        showNotification()

        if (timer == null) {
            timer = Timer()
            timer?.schedule(object : TimerTask() {
                override fun run() {
                    IS_RUNNING = true // To check if the service is running or not
                    profile()
                }
            }, 0, 10000) // 10 seconds
        }

        IS_RUNNING = true

        return START_STICKY
    }


    private fun profile() {
        val currentTime = System.currentTimeMillis()

        profileUserData(currentTime)
        profileSystemData(currentTime)
    }

    private fun profileUserData(currentTime: Long) {
        if (usageStatsManager == null) {
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        }

        val interval = 1000 * 10

        profileUserUsage(currentTime, interval)
        profileUserEvents(currentTime, interval)
    }

    private fun profileUserUsage(currentTime: Long, interval: Int = 10000) {
        val stats = usageStatsManager!!.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - interval, // 10 sec period
            currentTime
        )

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

        val userUsageDataString = mutableListOf<String>()

        for (userUsageData in userUsageDataList) {
            val userUsageDataLine =
                "$currentTime,${userUsageData.packageName},${userUsageData.memoryUsed},${userUsageData.lastTimeUsed},${userUsageData.totalTimeInForeground},${userUsageData.firstTimeUsed},${userUsageData.lastTimeForegroundServiceUsed},${userUsageData.totalTimeForegroundServiceUsed},${userUsageData.lastTimeVisible},${userUsageData.totalTimeVisible}\n"
            userUsageDataString.add(userUsageDataLine)
        }

        updateRecords(
            userRecords,
            userUsageDataString,
            USER_USAGE_HEADER,
            "user_${currentTime}.csv"
        )
    }

    private fun profileUserEvents(currentTime: Long, interval: Int = 10000) {
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

        val eventUsageDataString = mutableListOf<String>()

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
            eventUsageDataString.add(eventString)
        }

        // TODO: Define event dataclass and create header with dataclass fields
        updateRecords(eventRecords, eventUsageDataString, EVENT_HEADER, "events_${currentTime}.csv")
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
        systemRecords.add(data)

        if (systemRecords.size >= MAX_SYSTEM_RECORDS) {
            // Save the data collected above as a csv file named 'system_{timestamp}.csv'
            systemRecords.add(0, SYSTEM_HEADER)
            saveFileInBackground("system_${currentTime}.csv", systemRecords)
            systemRecords.clear()
        }
    }


    private fun flushRecords() {
        val currentTime = System.currentTimeMillis()

        userRecords.add(0, USER_USAGE_HEADER)
        eventRecords.add(0, EVENT_HEADER)
        systemRecords.add(0, SYSTEM_HEADER)

        saveFileInBackground("user_${currentTime}.csv", userRecords)
        saveFileInBackground("events_${currentTime}.csv", eventRecords)
        saveFileInBackground("system_${currentTime}.csv", systemRecords)
    }

    private fun updateRecords(records: MutableList<String>, recordsToAdd:MutableList<String>, header: String, fileName: String) {
        records.addAll(recordsToAdd)
        if (records.size > MAX_USAGE_RECORDS) {
            records.add(0, header)
            saveFileInBackground(fileName, records)
            records.clear()
        }
    }


    private fun saveFileInBackground(fileName: String, data: MutableList<String>) {
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

        private const val MAX_USAGE_RECORDS = 1000
        private const val MAX_SYSTEM_RECORDS = 100
        private const val ONGOING_NOTIFICATION_ID = 18
        private const val USER_USAGE_HEADER =  "timestamp,packageName,memoryUsed,lastTimeUsed,totalTimeInForeground,firstTimeUsed,lastTimeForegroundServiceUsed,totalTimeForegroundServiceUsed,lastTimeVisible,totalTimeVisible\n"
        private const val EVENT_HEADER = "timestamp,packageName,timestamp,eventType,standbyBucket\n"
        private const val SYSTEM_HEADER = "timestamp,longitude,latitude,accuracy,isCharging,batteryLevel,batterOverheat,temperature,screenBrightness,isScreenOn\n"
        private const val SERVICE_CHANNEL_ID = "profiler_service_channel_id"
    }
}

private operator fun <T> Array<T>.component6() = get(5)
