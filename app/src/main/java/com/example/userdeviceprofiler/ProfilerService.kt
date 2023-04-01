package com.example.userdeviceprofiler

import android.annotation.SuppressLint
import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
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

        val userUsageDataList = ArrayList<UserUsageData>()
        // Get the current foreground app information and store their data list in form of UserUsageData
        for (usageStats in stats) {
            val userUsageData = UserUsageData()
            userUsageData.packageName = usageStats.packageName
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
        for (event in usageEvents) {
            val packageName = event.packageName
            val timestamp = event.timeStamp
            val eventType = event.eventType
            val standbyBucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                event.appStandbyBucket
            } else {
                -1
            }
        }

        // TODO: Save userdata to a csv file named 'user_{timestamp}.csv'

    }


    @SuppressLint("MissingPermission")
    private fun profileSystemData(currentTime: Long) {
        // Get the current GPS information
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val latitude = location?.latitude
        val longitude = location?.longitude
        val accuracy = location?.accuracy

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

        val data = "$currentTime,$longitude,$latitude,$accuracy,$isCharging,$batteryLevel,$batterOverheat,$temperature,$screenBrightness\n"
        systemDataList.add(data)

        if (systemDataList.size >= 100) {
            // Save the data collected above as a csv file named 'system_{timestamp}.csv'
            val file = File(
                applicationContext.getExternalFilesDir(null),
                "system_${currentTime}.csv"
            )

            val writer = FileWriter(file)

            val header = "Timestamp,Longitude,Latitude,Accuracy,IsCharging,BatteryLevel,BatteryOverheat,Temperature,ScreenBrightness\n"
            writer.append(header)
            for (dataLine in systemDataList) {
                writer.append(dataLine)
            }

            writer.flush()
            writer.close()
        }
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
            set(is_running: Boolean) {
                this.is_running = is_running
            }
        private const val MAX_SAMPLES = 3000
        private const val ONGOING_NOTIFICATION_ID = 18
        private const val SERVICE_CHANNEL_ID = "profiler_service_channel_id"
    }
}

private operator fun <T> Array<T>.component6() = get(5)
