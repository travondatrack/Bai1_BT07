package com.example.bt01

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import java.io.File

class ProfileActivity : AppCompatActivity() {

    private lateinit var profileImage: ImageView
    private lateinit var titleView: TextView
    private lateinit var idView: TextView
    private lateinit var usernameView: TextView
    private lateinit var fullnameView: TextView
    private lateinit var emailView: TextView
    private lateinit var genderView: TextView
    private lateinit var logoutButton: Button

    // Launcher to start UploadImageActivity and receive result
    private val uploadLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val avatarUriStr = data?.getStringExtra("avatarUri")
            android.util.Log.d("ProfileActivity", "Received avatarUri: $avatarUriStr")
            if (!avatarUriStr.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(avatarUriStr)
                    android.util.Log.d("ProfileActivity", "Loading avatar from avatarUri: $avatarUriStr")
                    Glide.with(this)
                        .load(uri)
                        .error(android.R.drawable.stat_notify_error)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .signature(ObjectKey(System.currentTimeMillis()))
                        .circleCrop()
                        .into(profileImage)
                    return@registerForActivityResult
                } catch (e: Exception) {
                    android.util.Log.w("ProfileActivity", "Failed to load avatarUri: ${e.message}")
                }
            }
            val avatarPath = data?.getStringExtra("avatarPath")
            android.util.Log.d("ProfileActivity", "Received avatarPath from UploadImageActivity: $avatarPath")
            // Debug: check file existence when path looks like a filesystem path
            if (!avatarPath.isNullOrEmpty() && (avatarPath.startsWith("/") || avatarPath.startsWith("file://"))) {
                try {
                    val filePath = if (avatarPath.startsWith("file://")) Uri.parse(avatarPath).path ?: avatarPath else avatarPath
                    val file = java.io.File(filePath)
                    android.util.Log.d("ProfileActivity", "avatarPath resolved to file: ${file.absolutePath}, exists=${file.exists()}, size=${if (file.exists()) file.length() else 0}")
                    if (file.exists()) {
                        Glide.with(this)
                            .load(file)
                            .error(android.R.drawable.stat_notify_error)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .signature(ObjectKey(System.currentTimeMillis()))
                            .circleCrop()
                            .into(profileImage)
                        return@registerForActivityResult
                    } else {
                        android.util.Log.w("ProfileActivity", "File does not exist: $filePath; will attempt to load as URL")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProfileActivity", "Error checking avatarPath file: ${e.message}", e)
                }
            }
            if (!avatarPath.isNullOrEmpty()) {
                try {
                    // Fallback: treat as URL/string
                    Glide.with(this)
                        .load(avatarPath)
                        .error(android.R.drawable.stat_notify_error)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .signature(ObjectKey(System.currentTimeMillis()))
                        .circleCrop()
                        .into(profileImage)
                } catch (e: Exception) {
                    // Fallback: try loading directly
                    Glide.with(this)
                        .load(avatarPath)
                        .error(android.R.drawable.stat_notify_error)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .signature(ObjectKey(System.currentTimeMillis()))
                        .circleCrop()
                        .into(profileImage)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        AnhXa()

        // Set toolbar as the activity ActionBar if it exists in the layout
        try {
            // Avoid compile-time dependency on R.id.toolbar (layout may not include it).
            val toolbarId = resources.getIdentifier("toolbar", "id", packageName)
            if (toolbarId != 0) {
                val tb = findViewById<androidx.appcompat.widget.Toolbar?>(toolbarId)
                tb?.let {
                    setSupportActionBar(it)
                    it.title = "Hồ sơ cá nhân"
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_revert)
                }
            } else {
                android.util.Log.d("ProfileActivity", "No toolbar resource id found; skipping setSupportActionBar")
            }
        } catch (e: Exception) {
            android.util.Log.w("ProfileActivity", "setSupportActionBar failed", e)
        }

        // set sample data (could be replaced with real user data)
        idView.text = "Mã ID: 3"
        usernameView.text = "Tên đăng nhập: trung1"
        fullnameView.text = "Họ tên: Nguyễn Hữu Trung"
        emailView.text = "Email: trung2@gmail.com"
        genderView.text = "Giới tính: Male"

        // Ensure the ImageView is clickable
        profileImage.isClickable = true
        profileImage.isFocusable = true

        // Click avatar to open upload activity
        profileImage.setOnClickListener {
            // debug: show toast and log
            android.util.Log.d("ProfileActivity", "Avatar clicked")
            android.widget.Toast.makeText(this, "Avatar clicked", android.widget.Toast.LENGTH_SHORT).show()

            // Pass username to upload activity via intent
            val intent = Intent(this, UploadImageActivity::class.java)
            intent.putExtra(Const.MY_USERNAME, "trung1")
            try {
                uploadLauncher.launch(intent)
            } catch (e: Exception) {
                android.util.Log.e("ProfileActivity", "Failed to launch UploadImageActivity", e)
                android.widget.Toast.makeText(this, "Không thể mở màn hình upload: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        logoutButton.setOnClickListener {
            // For now simply finish the activity. Replace with clearing login state if needed.
            finish()
        }
    }

    private fun AnhXa() {
        profileImage = findViewById(R.id.profileImage)
        titleView = findViewById(R.id.title)
        idView = findViewById(R.id.id)
        usernameView = findViewById(R.id.username)
        fullnameView = findViewById(R.id.fullname)
        emailView = findViewById(R.id.email)
        genderView = findViewById(R.id.gender)
        logoutButton = findViewById(R.id.logoutButton)
    }
}
