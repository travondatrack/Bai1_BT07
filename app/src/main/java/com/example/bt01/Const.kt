package com.example.bt01

import android.os.Build
import androidx.annotation.RequiresApi

object Const {
    const val MY_USERNAME: String = "username"
    const val MY_IMAGES: String = "avatar"

    // Legacy storage permissions (pre-Android 13)
    val STORAGE_PERMISSIONS: Array<String> = arrayOf(
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    // Android 13 (Tiramisu) media permissions
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    val STORAGE_PERMISSIONS_33: Array<String> = arrayOf(
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_AUDIO,
        android.Manifest.permission.READ_MEDIA_VIDEO
    )
}
