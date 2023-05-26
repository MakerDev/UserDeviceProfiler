package com.example.userdeviceprofiler

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class CsvZipUploader {
    companion object {
        private val TAG = "CsvZipUploader"
        private val TOKEN_SERVER = "https://eisprofilertokenserver.azurewebsites.net/token"
    }

    fun compressAndUploadCsvFiles(context: Context, zipFileName: String) {
        // Step 1: Compress CSV files into a ZIP file
        val csvDirectory = context.getExternalFilesDir(null)
        val zipFile = File(csvDirectory, zipFileName)
        val csvFiles = getCsvFiles(csvDirectory)

        try {
            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    val buffer = ByteArray(1024)
                    for (csvFile in csvFiles) {
                        val zipEntry = ZipEntry(csvFile.name)
                        zos.putNextEntry(zipEntry)

                        FileInputStream(csvFile).use { fis ->
                            var length: Int
                            while (fis.read(buffer).also { length = it } > 0) {
                                zos.write(buffer, 0, length)
                            }
                        }

                        zos.closeEntry()
                    }
                }
            }

            CoroutineScope(Dispatchers.IO).launch {
                val uploadSuccessful = uploadZipFile(zipFileName, zipFile)

                // If successfully uploaded zip file, delete csv files
                if (uploadSuccessful) {
                    for (csvFile in csvFiles) {
                        csvFile.delete()
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload successful!", Toast.LENGTH_LONG).show()
                    }
                }
                else
                {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload failed!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to compress CSV files: ${e.message}")
            return
        }
    }

    private fun getCsvFiles(directory: File?): List<File> {
        val csvFiles = ArrayList<File>()
        val files = directory?.listFiles()
        files?.let {
            for (file in it) {
                if (file.name.lowercase().endsWith(".csv")) {
                    csvFiles.add(file)
                }
            }
        }
        return csvFiles
    }

    private fun uploadZipFile(fileName: String, zipFile: File): Boolean {
        val token = getAccessToken()
        //Step1: MAke POST request to start resumable upload session
        val location = requestLocation(fileName, "Upload at ${System.currentTimeMillis()}", token)

        //Step2: Make PUT request to upload file to session URL
        val client = OkHttpClient()

        // Prepare the request body with the zip file
        val requestBody = zipFile.asRequestBody("application/zip".toMediaType())

        // Create the request
        val request = Request.Builder()
            .url(location)
            .put(requestBody)
            .build()

        // Execute the request
        val response = client.newCall(request).execute()

        // Process the response
        if (!response.isSuccessful) {
            println("Error: ${response.code} - ${response.message}")
        }

        // Close the response
        response.close()

        return response.isSuccessful
    }

    private fun requestLocation(fileName: String, description: String, token: String): String {
        val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable" // Replace with your API endpoint URL

        val client = OkHttpClient()

        // Prepare the request body
        val requestBody =
            """
            {
                "name": "$fileName",
                "description": "$description",
                "parents": ["1oY0xqfuGgCBzEA2sPHHIRjaWS-RPV3nN"]
            }
            """.trimIndent().toRequestBody("application/json;charset=UTF-8".toMediaType())

        // Create the request
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Authorization", "Bearer $token")
            .build()

        // Execute the request
        val response = client.newCall(request).execute()
        var location = ""
        // Process the response
        if (response.isSuccessful) {
            // Request successful
            location = response.header("Location")!!
            println("Location: $location")
        } else {
            // Request failed
            println("Error: ${response.code} - ${response.message}")
        }
        // Close the response
        response.close()

        return location
    }

    private fun getAccessToken(): String {
        val client = OkHttpClient()
        // Create the request
        val request = Request.Builder()
            .url(TOKEN_SERVER)
            .get()
            .build()

        // Execute the request
        val response = client.newCall(request).execute()
        var token = ""
        if (response.isSuccessful) {
            // Request successful
            token = response.body!!.string()
            println("Access Token: $token")
        } else {
            // Request failed
            println("Error: ${response.code} - ${response.message}")
        }

        response.close()

        return token
    }
}