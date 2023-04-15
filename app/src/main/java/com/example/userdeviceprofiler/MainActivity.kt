package com.example.userdeviceprofiler

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.userdeviceprofiler.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var isGpsGranted = false
    private var isNotificationGranted = false
    private var isUsageStatGranted = false
    private lateinit var firstFragment: FirstFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fabStart.setOnClickListener {
            // Start the profiler foreground service.
            val intent = Intent(this, ProfilerService::class.java)
            intent.action = "START"
            applicationContext.startForegroundService(intent)
            firstFragment.updateIsRunning(true)
            binding.fabStart.isEnabled = false
            binding.fabStop.isEnabled = true
        }
        
        binding.fabStop.setOnClickListener {
            // Stop the profiler foreground service.
            val intent = Intent(this, ProfilerService::class.java)
            intent.action = "STOP"
            applicationContext.startService(intent)
            firstFragment.updateIsRunning(false)
            binding.fabStart.isEnabled = true
            binding.fabStop.isEnabled = false
        }

        checkIfPermissionsGranted()
    }

    fun setFirstFragment(firstFragment: FirstFragment) {
        this.firstFragment = firstFragment
    }


    private fun checkIfPermissionsGranted() {
        isGpsGranted = isGpsPermissionGranted(applicationContext)
        isNotificationGranted = isNotificationPermissionGranted(applicationContext)
        isUsageStatGranted = isUsageStatPermissionGranted(applicationContext)

        // If no permissions are granted, request all permissions.
        if (!isGpsGranted && !isNotificationGranted && !isUsageStatGranted) {
            firstFragment.requestAllPermissions(applicationContext)
        }

        firstFragment.updatePermissionStatus(isGpsGranted, isNotificationGranted, isUsageStatGranted)
        updateButtonStates()
    }

    private fun updateButtonStates() {
        // TODO: Deal with the case that user disable permission while the service is running.
        if (ProfilerService.IS_RUNNING) {
            binding.fabStart.isEnabled = false
            binding.fabStop.isEnabled = true

            return
        }

        if (!isGpsGranted || !isNotificationGranted || !isUsageStatGranted) {
            binding.fabStart.isEnabled = false
            binding.fabStop.isEnabled = false

            return
        }

        // All permissions are granted and the service is not running.
        binding.fabStart.isEnabled = true
        binding.fabStop.isEnabled = false
    }

    fun isGpsPermissionGranted(context: Context): Boolean {
        val packageName = context.packageName
        val packageManager = context.packageManager
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        // If sdk version is 29 or higher, we need to check both fine and coarse location permissions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        for (permission in permissions) {
            if (packageManager.checkPermission(permission, packageName) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        return true
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun isUsageStatPermissionGranted(context: Context): Boolean {
        val granted: Boolean
        val appOps = context.getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), applicationContext.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), applicationContext.packageName
            )
        }

        granted = if (mode == AppOpsManager.MODE_DEFAULT) {
            applicationContext.checkCallingOrSelfPermission(
                Manifest.permission.PACKAGE_USAGE_STATS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            (mode == AppOpsManager.MODE_ALLOWED)
        }

        return granted
    }

    override fun onResume() {
        super.onResume()

        //TODO: check if permissions are granted
        checkIfPermissionsGranted()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}