package com.example.bt01

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.util.regex.Pattern

class UploadImageActivity : AppCompatActivity() {

    // Helper container to return multipart part together with a local file path (if available)
    private data class UploadPart(val part: MultipartBody.Part, val localFilePath: String?)

    // ActivityResultLauncher that accepts an Intent (StartActivityForResult)
    private val mActivityResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            uri?.let { handleImageSelected(it) }
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.all { it }
        if (granted) {
            openGallery()
        } else {
            Toast.makeText(this, "Quyền bị từ chối", Toast.LENGTH_SHORT).show()
        }
    }

    // View references (AnhXa)
    private lateinit var btnChoose: Button
    private lateinit var btnUpload: Button
    private lateinit var imagePlaceholder: ImageView
    private lateinit var progressBar: ProgressBar

    // Selected image uri
    private var mUri: Uri? = null

    // last local image path selected (to return in result)
    private var lastImagePath: String? = null

    companion object {
        val TAG: String = UploadImageActivity::class.java.name
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_image)

        // Gọi hàm ánh xạ
        AnhXa()

        // Bắt sự kiện nút chọn ảnh
        btnChoose.setOnClickListener {
            // Kiểm tra quyền rồi mở gallery
            checkPermissionAndOpen()
        }

        // Bắt sự kiện nút upload ảnh
        btnUpload.setOnClickListener {
            if (mUri != null) {
                uploadImage()
            } else {
                Toast.makeText(this, "Vui lòng chọn ảnh trước khi upload", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Ánh xạ view
    private fun AnhXa() {
        btnChoose = findViewById(R.id.chooseFileButton)
        btnUpload = findViewById(R.id.uploadButton)
        imagePlaceholder = findViewById(R.id.imagePlaceholder)
        progressBar = findViewById(R.id.progressBar)
    }

    // Check permission and request if needed; uses ContextCompat to check individual perms
    private fun checkPermissionAndOpen() {
        val perms = permissions()
        val allGranted = perms.all { p ->
            ContextCompat.checkSelfPermission(this, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            openGallery()
        } else {
            requestPermissionsLauncher.launch(perms)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        val chooser = Intent.createChooser(intent, "Select Picture")
        mActivityResultLauncher.launch(chooser)
    }

    private fun handleImageSelected(uri: Uri) {
        // Show preview (use Glide circleCrop so it fits circular frame)
        try {
            com.bumptech.glide.Glide.with(this)
                .load(uri)
                .circleCrop()
                .into(imagePlaceholder)
        } catch (e: Exception) {
            imagePlaceholder.setImageURI(uri)
        }
        // Save uri for upload
        mUri = uri
        // Try to resolve real path (may be null depending on provider)
        val path = RealPathUtil.getRealPath(this, uri)
        lastImagePath = path
        Toast.makeText(this, "Path: $path", Toast.LENGTH_SHORT).show()
    }

    // Real upload implementation to 'updateimages.php'
    private fun uploadImage() {
        // show progress
        progressBar.visibility = android.view.View.VISIBLE
        btnUpload.isEnabled = false
        btnChoose.isEnabled = false

        val user = intent.getStringExtra(Const.MY_USERNAME) ?: ""
        if (user.isBlank()) {
            Toast.makeText(this, "Username not provided; upload will send empty username", Toast.LENGTH_SHORT).show()
        }
        val requestUsername: RequestBody = user.toRequestBody("text/plain".toMediaTypeOrNull())

        val uri = mUri
        if (uri == null) {
            progressBar.visibility = android.view.View.GONE
            btnUpload.isEnabled = true
            btnChoose.isEnabled = true
            Toast.makeText(this, "Không có ảnh để upload", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Starting upload for uri: $uri")

        try {
            // Build multipart part from Uri (works for file:// and content://)
            val uploadPart = buildMultipartFromUri(uri) ?: run {
                progressBar.visibility = android.view.View.GONE
                btnUpload.isEnabled = true
                btnChoose.isEnabled = true
                Toast.makeText(this, "Không thể tạo multipart từ ảnh", Toast.LENGTH_LONG).show()
                return
            }

            val partBodyAvatar = uploadPart.part
            val finalImagePath = uploadPart.localFilePath

            Log.d(TAG, "Calling API: ${ServiceAPI.BASE_URL}updateimages.php")

            // Build part map for form fields. Include id even if empty to satisfy server-side checks.
            val partMap = HashMap<String, RequestBody>()
            partMap[Const.MY_USERNAME] = requestUsername
            val idStr = intent.getStringExtra("id") ?: ""
            partMap["id"] = idStr.toRequestBody("text/plain".toMediaTypeOrNull())

            // Pass the multipart part (already created by buildMultipartFromUri) directly. buildMultipartFromUri now uses field name "images".
            RetrofitClient.service.updateImages(partMap, partBodyAvatar).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    // hide progress
                    progressBar.visibility = android.view.View.GONE
                    btnUpload.isEnabled = true
                    btnChoose.isEnabled = true

                    Log.d(TAG, "Request headers: ${call.request().headers}")
                    Log.d(TAG, "Response code: ${response.code()}")
                    Log.d(TAG, "Response message: ${response.message()}")

                    if (response.isSuccessful && response.body() != null) {
                        val raw = try {
                            response.body()!!.string()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to read response body: ${e.message}")
                            ""
                        }
                        Log.d(TAG, "Raw response: $raw")

                        // Strip PHP notices / HTML that may precede JSON using a regex that finds the first JSON object
                        val json = extractFirstJsonObject(raw)
                        if (json == null) {
                            Log.e(TAG, "No JSON found in response")
                            Toast.makeText(this@UploadImageActivity, "Server trả dữ liệu không hợp lệ", Toast.LENGTH_LONG).show()
                            return
                        }

                        try {
                            val gson = Gson()
                            val msg = gson.fromJson(json, Message::class.java)
                            Log.d(TAG, "Parsed JSON: success=${msg?.success}, message=${msg?.message}")
                            if (msg != null && msg.success) {
                                Toast.makeText(this@UploadImageActivity, "Upload thành công: ${msg.message}", Toast.LENGTH_LONG).show()
                                val result = Intent()
                                // Return the local file path so caller can display the new avatar
                                Log.d(TAG, "Returning avatarPath: $finalImagePath")
                                result.putExtra("avatarPath", finalImagePath ?: "")
                                try {
                                    finalImagePath?.let { fp ->
                                        val file = File(fp)
                                        if (file.exists()) {
                                            result.putExtra("avatarUri", Uri.fromFile(file).toString())
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to build avatarUri: ${e.message}")
                                }
                                setResult(RESULT_OK, result)
                                finish()
                            } else {
                                val errorMsg = msg?.message ?: "Không có thông tin lỗi"
                                Toast.makeText(this@UploadImageActivity, "Upload thất bại: $errorMsg", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse JSON: ${e.message}", e)
                            Toast.makeText(this@UploadImageActivity, "Không thể phân tích phản hồi từ server", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val errorBody = try {
                            response.errorBody()?.string() ?: "Không có thông tin lỗi"
                        } catch (e: Exception) {
                            "Không đọc được errorBody: ${e.message}"
                        }
                        Log.e(TAG, "Error response: $errorBody")
                        Toast.makeText(this@UploadImageActivity, "Lỗi server: ${response.code()}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    // hide progress
                    progressBar.visibility = android.view.View.GONE
                    btnUpload.isEnabled = true
                    btnChoose.isEnabled = true

                    Log.e(TAG, "Gọi API thất bại: ${t.message}", t)
                    Toast.makeText(this@UploadImageActivity, "Gọi API thất bại: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })

        } catch (e: Exception) {
            progressBar.visibility = android.view.View.GONE
            btnUpload.isEnabled = true
            btnChoose.isEnabled = true
            Log.e(TAG, "Exception during upload: ${e.message}", e)
            Toast.makeText(this, "Lỗi khi xử lý file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Helper: create MultipartBody.Part from a content Uri. This handles both file paths and content URIs
    private fun buildMultipartFromUri(uri: Uri): UploadPart? {
        try {
            // Resolve filename if needed (we'll name temp files with unique prefix)
            // val fileName = getFileName(uri) ?: "temp_image_${System.currentTimeMillis()}.jpg"

            // Try to get a real path; if exists use File to avoid extra copy
            val realPath = RealPathUtil.getRealPath(this, uri)
            if (!realPath.isNullOrEmpty()) {
                val fileOnDisk = File(realPath)
                if (fileOnDisk.exists() && fileOnDisk.length() > 0L) {
                    val ext = fileOnDisk.extension.ifBlank { "jpg" }
                    val finalFile = File(filesDir, "avatar_${System.currentTimeMillis()}.$ext")
                    fileOnDisk.copyTo(finalFile, overwrite = true)
                    val mimeType = when (ext.lowercase()) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "gif" -> "image/gif"
                        "webp" -> "image/webp"
                        else -> contentResolver.getType(uri) ?: "image/jpeg"
                    }
                    Log.d(TAG, "Copied original to app files: ${finalFile.absolutePath}, mime=$mimeType")
                    val requestFile = finalFile.asRequestBody(mimeType.toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("images", finalFile.name, requestFile)
                    return UploadPart(part, finalFile.absolutePath)
                }
            }

            // If no real path or file doesn't exist, copy content to a temp file in cache and return its path
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val tmpFile = File.createTempFile("upload_", "_${System.currentTimeMillis()}", cacheDir)
                tmpFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                if (!tmpFile.exists() || tmpFile.length() == 0L) {
                    Log.e(TAG, "Temp file creation failed or zero length: ${tmpFile.absolutePath}")
                    return null
                }
                // Move/copy temp file into app files directory to make it persistent and accessible
                val ext = tmpFile.extension.ifBlank { "jpg" }
                val finalFile = File(filesDir, "avatar_${System.currentTimeMillis()}.$ext")
                tmpFile.copyTo(finalFile, overwrite = true)
                // Optionally delete tmpFile
                try { tmpFile.delete() } catch (ignored: Exception) {}
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                Log.d(TAG, "Copied content to final file: ${finalFile.absolutePath}, mime=$mimeType, size=${finalFile.length()}")
                val requestFile = finalFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("images", finalFile.name, requestFile)
                return UploadPart(part, finalFile.absolutePath)
            }

            Log.e(TAG, "Failed to open InputStream for uri: $uri")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "buildMultipartFromUri exception: ${e.message}", e)
            return null
        }
    }

    // Return correct permission array depending on SDK
    private fun permissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Const.STORAGE_PERMISSIONS_33
        } else {
            Const.STORAGE_PERMISSIONS
        }
    }

    // Helper function to get filename from URI
    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            val path = uri.path ?: return null
            val cut = path.lastIndexOf('/')
            if (cut != -1) {
                result = path.substring(cut + 1)
            } else {
                result = path
            }
        }
        return result
    }

    // Utility: extract first JSON object substring from a raw response that may contain PHP notices/HTML
    private fun extractFirstJsonObject(raw: String): String? {
        if (raw.isBlank()) return null
        // Simple regex to find the first {...} JSON object
        val pattern = Pattern.compile("\\{.*?\\}", Pattern.DOTALL)
        val matcher = pattern.matcher(raw)
        return if (matcher.find()) matcher.group(0) else null
    }
}
