package com.example.userdeviceprofiler

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

class ProfilerService: Service() {
    private var timer: Timer? = null
    private lateinit var notificationManager: NotificationManagerCompat

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        showNotification()

        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                //TODO
                profile()
            }
        }, 0, 10000) // 10 seconds

        return START_STICKY
    }

    private fun profileUserData() {

    }

    private fun profileSystemData() {

    }

    private fun profile() {
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

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "EISProfiler_service_channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "EIS Profiler Notification Channel"
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("EISProfiler")
            .setContentText("Profiler is running")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        private const val ONGOING_NOTIFICATION_ID = 1
        private const val SERVICE_CHANNEL_ID = "profiler_service_channel_id"
    }
}