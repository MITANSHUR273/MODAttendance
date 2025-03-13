package com.example.modattendance.utils

import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

object GitHubHelper {
    private val GITHUB_TOKEN = System.getenv("GITHUB_TOKEN") ?: ""
    private const val GITHUB_REPO = "MITANSHUR273/NATIONAL_SCIENCE_DAY"
    private const val GITHUB_ATTENDANCE_FILE = "attendance.json"
    private const val GITHUB_SCHOOLS_FILE = "schools.json"
    private const val GITHUB_ATTENDANCE_API_URL = "https://api.github.com/repos/$GITHUB_REPO/contents/$GITHUB_ATTENDANCE_FILE"
    private const val GITHUB_SCHOOLS_API_URL = "https://api.github.com/repos/$GITHUB_REPO/contents/$GITHUB_SCHOOLS_FILE"

    private val client = OkHttpClient()

    // ✅ Fetch Attendance Data with SHA (Required for Overwriting)
    fun fetchAttendanceData(callback: (JSONObject?, String?) -> Unit) {
        fetchDataFromGitHub(GITHUB_ATTENDANCE_API_URL, callback)
    }

    // ✅ Fetch School Data with SHA
    fun fetchSchoolData(callback: (JSONObject?, String?) -> Unit) {
        fetchDataFromGitHub(GITHUB_SCHOOLS_API_URL, callback)
    }

    // ✅ Upload Attendance Percentage & Overwrite Existing Data
    fun uploadAttendancePercentage(
        schoolName: String, city: String, state: String, percentage: Double, callback: (Boolean) -> Unit
    ) {
        fetchAttendanceData { existingJson, sha ->
            val updatedJson = existingJson ?: JSONObject()
            val schoolKey = "$schoolName-$city-$state"

            val schoolData = JSONObject().apply {
                put("schoolName", schoolName)
                put("city", city)
                put("state", state)
                put("finalAveragePercentage", String.format("%.2f", percentage))
            }

            updatedJson.put(schoolKey, schoolData)
            uploadDataToGitHub(GITHUB_ATTENDANCE_API_URL, updatedJson, sha, "Updated attendance percentage", callback)
        }
    }

    // ✅ Upload School Data & Overwrite Existing Data
    fun uploadSchoolData(schoolData: JSONObject, callback: (Boolean) -> Unit) {
        fetchSchoolData { existingJson, sha ->
            val updatedJson = existingJson ?: JSONObject()
            uploadDataToGitHub(GITHUB_SCHOOLS_API_URL, updatedJson, sha, "Updated school data", callback)
        }
    }

    // ✅ Fetch Data from GitHub (Helper Function)
    private fun fetchDataFromGitHub(url: String, callback: (JSONObject?, String?) -> Unit) {
        val token = getGitHubToken()
        if (token.isEmpty()) {
            Log.e("GitHubHelper", "GitHub Token is missing!")
            callback(null, null)
            return
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "token $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GitHubHelper", "Error fetching data from $url", e)
                callback(null, null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(null, null)
                        return
                    }

                    val responseBody = response.body?.string() ?: ""
                    try {
                        val json = JSONObject(responseBody)
                        val content = json.getString("content")
                        val sha = json.getString("sha") // ✅ Store SHA for overwriting
                        val decodedContent = String(Base64.decode(content, Base64.DEFAULT))
                        callback(JSONObject(decodedContent), sha)
                    } catch (e: Exception) {
                        Log.e("GitHubHelper", "JSON Parsing Error", e)
                        callback(null, null)
                    }
                }
            }
        })
    }

    // ✅ Upload Data to GitHub & Overwrite Existing File (Helper Function)
    private fun uploadDataToGitHub(
        url: String, jsonData: JSONObject, sha: String?, commitMessage: String, callback: (Boolean) -> Unit
    ) {
        val token = getGitHubToken()
        if (token.isEmpty()) {
            Log.e("GitHubHelper", "GitHub Token is missing!")
            callback(false)
            return
        }

        val jsonBody = JSONObject().apply {
            put("message", commitMessage)
            put("content", Base64.encodeToString(jsonData.toString().toByteArray(), Base64.NO_WRAP))
            if (sha != null) put("sha", sha) // ✅ Include SHA to overwrite
            put("branch", "main")
        }

        val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "token $token")
            .put(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful)
            }
        })
    }

    // ✅ Function to Get GitHub Token Securely
    private fun getGitHubToken(): String {
        return System.getenv("GITHUB_TOKEN") ?: ""
    }
}
