package com.smzdm.android.module.user.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * 用户相关 API
 */
interface UserApi {

    /**
     * 上传头像 - Multipart 方式
     * 
     * 最简单的方式：只上传文件
     */
    @Multipart
    @POST("api/upload/avatar")  // 替换为你的实际接口地址
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part
    ): Response<AvatarUploadResponse>

    /**
     * 上传头像 - 带额外参数
     * 
     * 如果需要传递其他参数（如 userId, token 等）
     */
    @Multipart
    @POST("api/upload/avatar")
    suspend fun uploadAvatarWithParams(
        @Part avatar: MultipartBody.Part,
        @Part("userId") userId: RequestBody,
        @Part("token") token: RequestBody
    ): Response<AvatarUploadResponse>

    /**
     * 上传头像 - 使用 Header 传递 Token
     * 
     * 如果你的项目使用 Token 验证，可以通过 Header 传递
     */
    @Multipart
    @POST("api/upload/avatar")
    suspend fun uploadAvatarWithToken(
        @Header("Authorization") token: String,  // 例如：Bearer your_token
        @Part avatar: MultipartBody.Part
    ): Response<AvatarUploadResponse>
}

/**
 * 上传响应
 */
data class AvatarUploadResponse(
    val error_code: String,      // "0" 表示成功
    val error_msg: String,        // 错误消息
    val data: AvatarData?         // 数据
)

/**
 * 头像数据
 */
data class AvatarData(
    val url: String              // 上传成功后的头像 URL
)
