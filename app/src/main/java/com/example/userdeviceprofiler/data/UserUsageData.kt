package com.example.userdeviceprofiler.data

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