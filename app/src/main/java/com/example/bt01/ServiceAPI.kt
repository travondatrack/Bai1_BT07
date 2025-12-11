package com.example.bt01

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap

interface ServiceAPI {
    companion object {
        const val BASE_URL = "http://app.iotstar.vn:8081/appfoods/"
    }

    @Multipart
    @POST("upload.php")
    fun upload(
        @Part(Const.MY_USERNAME) username: RequestBody,
        @Part avatar: MultipartBody.Part
    ): Call<ResponseBody>

    @Multipart
    @POST("upload1.php")
    fun upload1(
        @Part(Const.MY_USERNAME) username: RequestBody,
        @Part avatar: MultipartBody.Part
    ): Call<Message>

    @Multipart
    @POST("updateimages.php")
    fun updateImages(
        @PartMap partMap: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part file: MultipartBody.Part
    ): Call<ResponseBody>
}
