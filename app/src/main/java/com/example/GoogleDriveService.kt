package com.example

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.http.*
import java.io.File
import java.util.concurrent.TimeUnit

// Helper extension to await standard GMS Task in coroutines
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
}

interface GoogleDriveApi {
    @GET("files")
    suspend fun listFiles(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id, name, createdTime, size)"
    ): ResponseBody

    @Multipart
    @POST("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
    suspend fun uploadFileMultipart(
        @Header("Authorization") authHeader: String,
        @Part metadata: MultipartBody.Part,
        @Part file: MultipartBody.Part
    ): ResponseBody

    @GET("files/{fileId}")
    suspend fun downloadFile(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media"
    ): ResponseBody

    @DELETE("files/{fileId}")
    suspend fun deleteFile(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String
    ): ResponseBody

    @GET("https://www.googleapis.com/oauth2/v3/userinfo")
    suspend fun getUserInfo(
        @Header("Authorization") authHeader: String
    ): ResponseBody
}

class GoogleDriveManager(val context: Context) {
    private val TAG = "GoogleDriveManager"
    private val prefs = context.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    var userEmail: String?
        get() = prefs.getString("user_email", null)
        set(value) = prefs.edit().putString("user_email", value).apply()

    var userName: String?
        get() = prefs.getString("user_name", null)
        set(value) = prefs.edit().putString("user_name", value).apply()

    val isConnected: Boolean
        get() = accessToken != null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val driveApi = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/drive/v3/")
        .client(okHttpClient)
        .build()
        .create(GoogleDriveApi::class.java)

    suspend fun updateUserInfoAfterAuth(token: String) {
        accessToken = token
        fetchUserInfo()
    }

    suspend fun fetchUserInfo() {
        val token = accessToken ?: return
        try {
            val response = driveApi.getUserInfo("Bearer $token")
            val json = JSONObject(response.string())
            userEmail = json.optString("email", "Google User")
            userName = json.optString("name", "Connected")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user info", e)
        }
    }

    fun disconnect() {
        prefs.edit().clear().apply()
    }

    suspend fun getFreshAccessToken(): String {
        try {
            val requestedScopes = listOf(
                Scope("https://www.googleapis.com/auth/drive.file"),
                Scope("https://www.googleapis.com/auth/userinfo.email"),
                Scope("https://www.googleapis.com/auth/userinfo.profile")
            )
            val authRequestBuilder = AuthorizationRequest.builder()
                .setRequestedScopes(requestedScopes)
            
            val authorizationRequest = authRequestBuilder.build()

            val result = Identity.getAuthorizationClient(context)
                .authorize(authorizationRequest)
                .await()

            val token = result.accessToken
            if (!token.isNullOrEmpty()) {
                accessToken = token
                return token
            }
        } catch (e: Exception) {
            val apiEx = e as? com.google.android.gms.common.api.ApiException
            val statusCode = apiEx?.statusCode
            val statusMessage = apiEx?.localizedMessage ?: e.message
            Log.e(TAG, "Failed to get fresh access token silently. Code: $statusCode, Message: $statusMessage", e)
        }
        return accessToken ?: throw Exception("Not connected to Google Drive")
    }

    private suspend fun <T> runWithRetry(block: suspend (String) -> T): T {
        val token = getFreshAccessToken()
        return block("Bearer $token")
    }

    suspend fun uploadBackup(backupFile: File): Boolean {
        return runWithRetry { authHeader ->
            // 1. Delete existing backups if they exist to save space and keep it clean
            try {
                val query = "name = 'calculator_vault_backup.zip' and trashed = false"
                val listResponse = driveApi.listFiles(authHeader, query)
                val json = JSONObject(listResponse.string())
                val files = json.getJSONArray("files")
                for (i in 0 until files.length()) {
                    val fileObj = files.getJSONObject(i)
                    val id = fileObj.getString("id")
                    driveApi.deleteFile(authHeader, id)
                    Log.d(TAG, "Deleted old backup file with ID: $id")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking/deleting old backups", e)
            }

            // 2. Upload file multipart
            val metadataPart = MultipartBody.Part.createFormData(
                "metadata",
                "metadata.json",
                "{\"name\": \"calculator_vault_backup.zip\"}".toRequestBody("application/json; charset=UTF-8".toMediaType())
            )

            val fileRequestBody = backupFile.asRequestBody("application/zip".toMediaType())
            val filePart = MultipartBody.Part.createFormData("file", backupFile.name, fileRequestBody)

            val response = driveApi.uploadFileMultipart(authHeader, metadataPart, filePart)
            val responseStr = response.string()
            Log.d(TAG, "Upload response: $responseStr")
            
            val uploadJson = JSONObject(responseStr)
            uploadJson.has("id")
        }
    }

    suspend fun downloadBackup(destFile: File): Boolean {
        return runWithRetry { authHeader ->
            // 1. Find backup file
            val query = "name = 'calculator_vault_backup.zip' and trashed = false"
            val listResponse = driveApi.listFiles(authHeader, query)
            val json = JSONObject(listResponse.string())
            val files = json.getJSONArray("files")
            if (files.length() == 0) {
                throw Exception("No backup file found in your Google Drive.")
            }

            val fileId = files.getJSONObject(0).getString("id")

            // 2. Download media
            val downloadResponse = driveApi.downloadFile(authHeader, fileId)
            val inputStream = downloadResponse.byteStream()
            
            destFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            true
        }
    }

    suspend fun getBackupInfo(): BackupMetadata? {
        if (!isConnected) return null
        return try {
            runWithRetry { authHeader ->
                val query = "name = 'calculator_vault_backup.zip' and trashed = false"
                val listResponse = driveApi.listFiles(authHeader, query)
                val json = JSONObject(listResponse.string())
                val files = json.getJSONArray("files")
                if (files.length() > 0) {
                    val fileObj = files.getJSONObject(0)
                    BackupMetadata(
                        id = fileObj.getString("id"),
                        name = fileObj.getString("name"),
                        size = fileObj.optLong("size", 0L),
                        createdTime = fileObj.optString("createdTime", "Unknown")
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch backup metadata", e)
            null
        }
    }
}

data class BackupMetadata(
    val id: String,
    val name: String,
    val size: Long,
    val createdTime: String
)
