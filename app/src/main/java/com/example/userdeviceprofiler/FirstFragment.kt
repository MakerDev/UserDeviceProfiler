package com.example.userdeviceprofiler

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.userdeviceprofiler.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)

        if (activity is MainActivity) {
            (activity as MainActivity).setFirstFragment(this)
        }

        binding.gpsPermissionButton.setOnClickListener {
            requestGps(requireContext())
        }

        binding.notificationPermissionButton.setOnClickListener {
            requestNotification(requireContext())
        }

        binding.usageStatPermissionButton.setOnClickListener {
            requestUsageStat()
        }

        return binding.root
    }

    @SuppressLint("SetTextI18n")
    fun updateIsRunning(started: Boolean) {
        binding.isServiceRunning.text = "Is Service Running: $started"
    }

    fun requestAllPermissions(context: Context) {
        requestGps(context)
        requestNotification(context)
        requestUsageStat()
    }

    private fun requestUsageStat() {
        val intentUsageStat = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intentUsageStat.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intentUsageStat)
    }

    private fun requestNotification(context: Context) {
        val intentNotification = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intentNotification.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        startActivity(intentNotification)
    }

    private fun requestGps(context: Context) {
        val intentGps = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", context.packageName, null)
        intentGps.data = uri
        intentGps.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intentGps)
    }

    @SuppressLint("SetTextI18n")
    fun updatePermissionStatus(gpsGranted: Boolean, notificationGranted: Boolean, usageStatGranted: Boolean) {
        binding.notificationPermissionStatus.text = "Notification Permission: ${isGranted(notificationGranted)}"
        binding.gpsPermissionStatus.text = "GPS Permission: ${isGranted(gpsGranted)}"
        binding.usageStatPermissionStatus.text = "Usage Stat Permission: ${isGranted(usageStatGranted)}"

        binding.gpsPermissionButton.isEnabled = !gpsGranted
        binding.notificationPermissionButton.isEnabled = !notificationGranted
        binding.usageStatPermissionButton.isEnabled = !usageStatGranted
    }

    private fun isGranted(granted: Boolean): String {
        return if (granted) {
            "Granted"
        } else {
            "Not Granted"
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        binding.isServiceRunning.text = "Is Service Running: ${ProfilerService.IS_RUNNING}"

        val activity = activity as MainActivity
        val gpsGranted = activity.isGpsPermissionGranted(activity.applicationContext)
        val notificationGranted = activity.isNotificationPermissionGranted(activity.applicationContext)
        val usageStatGranted = activity.isUsageStatPermissionGranted(activity.applicationContext)

        updatePermissionStatus(gpsGranted, notificationGranted, usageStatGranted)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.isServiceRunning.text = "Is Service Running: ${ProfilerService.IS_RUNNING}"

        binding.buttonFirst.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}