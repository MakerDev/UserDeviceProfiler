package com.example.userdeviceprofiler

import android.app.*
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Debug
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

class ProfilerService: Service() {
    private var timer: Timer? = null
    private lateinit var notificationManager: NotificationManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null && intent.action.equals(
                "STOP", ignoreCase = true)) {
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
                        //TODO
                        profile()
                    }
                }, 0, 10000) // 10 seconds
            }

            IS_RUNNING = true
        }

        return START_STICKY
    }

    private fun profileUserData() {

    }

    private fun profileSystemData() {

    }

    private fun profile() {
        profileUserData()
        profileSystemData()
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        // Get the current foreground app and its importance
        val currentTime = System.currentTimeMillis()
        val interval = 1000 * 10

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - interval, // 10 sec period
            currentTime
        )

        val events = usageStatsManager.queryEvents(
            currentTime - interval, // 10 sec period
            currentTime
        );

        if (stats != null) {
            val mySortedMap: SortedMap<Long, UsageStats> = TreeMap()
            for (usageStats in stats) {
                mySortedMap[usageStats.lastTimeUsed] = usageStats
            }

            if (!mySortedMap.isEmpty()) {
                val statsObj: UsageStats =
                    mySortedMap[mySortedMap.lastKey()] ?: return
                val packageName = statsObj.packageName

                // Get the memory usage of the current foreground app
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val memoryUsed = memoryInfo.totalMem - memoryInfo.availMem
                val runningProcesses = activityManager.runningAppProcesses

                for (processInfo in runningProcesses) {
                    val pid = processInfo.pid
                    val memoryInfo: Array<Debug.MemoryInfo> =
                        activityManager.getProcessMemoryInfo(intArrayOf(pid))
                    val info: Debug.MemoryInfo = memoryInfo[0]
                    val processName = processInfo.processName
                }


                val command: MutableList<String> = ArrayList()
                command.add("top")
                command.add("-b")
                command.add("-n")
                command.add("1")

                val pb = ProcessBuilder(command)
                try {
                    val process = pb.start() // execute the command
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String
                    val text = reader.readText()

                    val lines = reader.readLines()
                    for (line in lines) {
                        // read output line by line
                        val fields = line.trim { it <= ' ' }.split("\\s+".toRegex())
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                        val cpuUsage = fields[2].toFloat()
                        println("Process CPU usage: $cpuUsage")
                    }
                    reader.close()
                    process.destroy()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
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
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

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

        private const val ONGOING_NOTIFICATION_ID = 18
        private const val SERVICE_CHANNEL_ID = "profiler_service_channel_id"
    }
}