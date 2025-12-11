package com.example.bt01

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
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
import java.io.IOException
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    // Hold selected Uri
    private var mUri: Uri? = null

    // Views
    private var imageViewChoose: ImageView? = null
    private var btnChoose: Button? = null
    private var btnUpload: Button? = null
    private var imageViewUpload: ImageView? = null
    private var editTextUserName: EditText? = null
    private var textViewUsername: TextView? = null

    // Progress dialog
    private var mProgressDialog: ProgressDialog? = null

    companion object {
        const val MY_REQUEST_CODE: Int = 100
    }

    // ActivityResultLauncher to receive an Intent result (StartActivityForResult)
    private val mActivityResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            Log.e(TAG, "onActivityResult")
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data == null) return@registerForActivityResult
                val uri: Uri? = data.data
                mUri = uri
                if (uri != null) {
                    try {
                        val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                        // Set bitmap to ImageView if available (safe call)
                        imageViewChoose?.setImageBitmap(bitmap)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use existing XML layout for upload
        setContentView(R.layout.activity_upload_image)

        // Bind views using actual R.id values
        AnhXa()

        // Wire up buttons
        btnChoose?.setOnClickListener {
            // Open gallery
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            mActivityResultLauncher.launch(intent)
        }

        btnUpload?.setOnClickListener {
            uploadImage1()
        }
    }

    // Map views to variables (AnhXa)
    private fun AnhXa() {
        // Use direct R.id lookups (preferred)
        btnChoose = findViewById(R.id.chooseFileButton)
        btnUpload = findViewById(R.id.uploadButton)

        // Single image view in layout used for preview and upload result
        imageViewChoose = findViewById(R.id.imagePlaceholder)
        imageViewUpload = findViewById(R.id.imagePlaceholder)

        // Optional username fields may not exist in this layout — look them up by name safely
        val idEdit = resources.getIdentifier("editUserName", "id", packageName)
        if (idEdit != 0) editTextUserName = findViewById(idEdit)

        val idTv = resources.getIdentifier("tvUsername", "id", packageName)
        if (idTv != 0) textViewUsername = findViewById(idTv)

        mProgressDialog = ProgressDialog(this)
        mProgressDialog?.setMessage("Đang xử lý...")
        mProgressDialog?.setCancelable(false)
    }

    // New function: uploadImage1() — Kotlin port of the provided Java UploadImage1()
    private fun uploadImage1() {
        mProgressDialog?.show()

        val username = editTextUserName?.text?.toString()?.trim() ?: ""
        val requestUsername: RequestBody = username.toRequestBody("text/plain".toMediaTypeOrNull())

        // Use a local copy of mUri to avoid smart-cast problems
        val uri = mUri
        if (uri == null) {
            mProgressDialog?.dismiss()
            Toast.makeText(this, "Không có ảnh để upload", Toast.LENGTH_SHORT).show()
            return
        }

        val IMAGE_PATH: String? = RealPathUtil.getRealPath(this, uri)
        Log.e(TAG, "IMAGE_PATH = $IMAGE_PATH")

        if (IMAGE_PATH.isNullOrEmpty()) {
            mProgressDialog?.dismiss()
            Toast.makeText(this, "Không lấy được đường dẫn ảnh", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(IMAGE_PATH)
        if (!file.exists()) {
            mProgressDialog?.dismiss()
            Toast.makeText(this, "File không tồn tại: $IMAGE_PATH", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Uploading file: ${file.name}, size: ${file.length()} bytes, username: $username")

        // Use correct MIME type for image files
        val mimeType = when {
            file.name.endsWith(".jpg", ignoreCase = true) || file.name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            file.name.endsWith(".png", ignoreCase = true) -> "image/png"
            file.name.endsWith(".gif", ignoreCase = true) -> "image/gif"
            else -> "image/*"
        }

        val requestFile: RequestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val partBodyAvatar: MultipartBody.Part = MultipartBody.Part.createFormData(Const.MY_IMAGES, file.name, requestFile)

        RetrofitClient.service.upload(requestUsername, partBodyAvatar).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                mProgressDialog?.dismiss()

                Log.d(TAG, "Response code: ${response.code()}")
                Log.d(TAG, "Response message: ${response.message()}")

                if (response.isSuccessful && response.body() != null) {
                    val raw = try { response.body()!!.string() } catch (e: Exception) { null }
                    Log.d(TAG, "Raw response: $raw")

                    val json = extractFirstJsonObject(raw ?: "")
                    if (json != null) {
                        try {
                            val gson = Gson()
                            // Try parse as Message first
                            val msg = gson.fromJson(json, Message::class.java)
                            if (msg != null && msg.success) {
                                Toast.makeText(this@MainActivity, "Upload thành công: ${msg.message}", Toast.LENGTH_LONG).show()
                                // If caller provided avatarPath or similar, nothing to do here — UI preview already updated from local file
                                return
                            }
                            // Otherwise try parse result array -> ImageUpload[]
                            val listType = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, ImageUpload::class.java).type
                            val arr = gson.fromJson<List<ImageUpload>>(json, listType)
                            if (!arr.isNullOrEmpty()) {
                                // update UI with first avatar
                                val first = arr[0]
                                textViewUsername?.text = first.username ?: ""
                                imageViewUpload?.let { iv ->
                                    Glide.with(this@MainActivity)
                                        .load(first.avatar)
                                        .into(iv)
                                }
                                Toast.makeText(this@MainActivity, "Thành công", Toast.LENGTH_LONG).show()
                                return
                            }

                            // fallback: show raw json
                            Toast.makeText(this@MainActivity, "Server trả về: $json", Toast.LENGTH_LONG).show()
                            return
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse JSON: ${e.message}", e)
                            Toast.makeText(this@MainActivity, "Lỗi phân tích phản hồi: ${e.message}", Toast.LENGTH_LONG).show()
                            return
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Server trả về dữ liệu không hợp lệ", Toast.LENGTH_LONG).show()
                        return
                    }
                } else {
                    val err = try { response.errorBody()?.string() ?: "" } catch (e: Exception) { "" }
                    Log.e(TAG, "Error response: $err")
                    Toast.makeText(this@MainActivity, "Lỗi server: ${response.code()}", Toast.LENGTH_LONG).show()
                    return
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                mProgressDialog?.dismiss()
                Log.e(TAG, "API call failed: ${t.message}", t)
                Toast.makeText(this@MainActivity, "Gọi API thất bại: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    // Utility: extract first JSON object substring from a raw response that may contain PHP notices/HTML
    private fun extractFirstJsonObject(raw: String): String? {
        if (raw.isBlank()) return null
        val pattern = Pattern.compile("\\{.*?\\}|\\[.*?\\]", Pattern.DOTALL)
        val matcher = pattern.matcher(raw)
        return if (matcher.find()) matcher.group(0) else null
    }
}
