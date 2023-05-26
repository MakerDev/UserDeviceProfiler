package com.example.userdeviceprofiler
import java.text.SimpleDateFormat
import java.util.*
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat.getSystemService
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

        binding.uploadDataButton.setOnClickListener {
            binding.uploadDataButton.isEnabled = false
            binding.nameTextField.clearFocus()
            hideKeyboard()

            uploadData()
        }

        binding.nameTextField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not needed for this scenario
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Update the enabled state of the uploadDataButton based on nameTextField's text
                val isNameFieldEmpty = s.isNullOrEmpty()
                binding.uploadDataButton.isEnabled = !isNameFieldEmpty
            }

            override fun afterTextChanged(s: Editable?) {
                // Not needed for this scenario
            }
        })

        return binding.root
    }

    private fun hideKeyboard() {
        val inputMethodManager = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.nameTextField.windowToken, 0)
    }

    @SuppressLint("SetTextI18n")
    fun updateIsRunning(started: Boolean) {
        binding.isServiceRunning.text = "Is Service Running: $started"
    }

    fun uploadData() {
        val uploader = CsvZipUploader()
        val name = binding.nameTextField.text.toString()
        val dateFormat = SimpleDateFormat("MMdd_HHmmss", Locale.getDefault())
        val dateString = dateFormat.format(Date(System.currentTimeMillis()))
        uploader.compressAndUploadCsvFiles(requireContext(), "${name}_${dateString}.zip")
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
        // TODO: Request for ACCESS_BACKGROUND_LOCATION too.
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