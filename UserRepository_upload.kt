package com.smzdm.android.module.user.repository

import com.smzdm.android.module.user.api.UserApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

/**
 * 用户相关 Repository
 */
class UserRepository @Inject constructor(
    private val userApi: UserApi
) {

    /**
     * 上传头像 - 方式1：只传文件
     * 
     * @param file 头像文件
     * @return Result<String> 成功返回头像 URL，失败返回异常
     */
    suspend fun uploadAvatar(file: File): Result<String> {
        return try {
            // 1. 创建 RequestBody
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            
            // 2. 创建 MultipartBody.Part
            // 参数名 "avatar" 需要和后端约定一致
            val body = MultipartBody.Part.createFormData(
                "avatar",           // 参数名（询问后端）
                file.name,          // 文件名
                requestFile
            )
            
            // 3. 调用接口
            val response = userApi.uploadAvatar(body)
            
            // 4. 处理响应
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody?.error_code == "0") {
                    val avatarUrl = responseBody.data?.url
                    if (avatarUrl != null) {
                        Result.success(avatarUrl)
                    } else {
                        Result.failure(Exception("未返回头像URL"))
                    }
                } else {
                    Result.failure(Exception(responseBody?.error_msg ?: "上传失败"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 上传头像 - 方式2：带额外参数
     * 
     * @param file 头像文件
     * @param userId 用户ID
     * @param token Token
     */
    suspend fun uploadAvatarWithParams(
        file: File,
        userId: String,
        token: String
    ): Result<String> {
        return try {
            // 创建文件 Part
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val avatarPart = MultipartBody.Part.createFormData("avatar", file.name, requestFile)
            
            // 创建其他参数
            val userIdBody = userId.toRequestBody("text/plain".toMediaTypeOrNull())
            val tokenBody = token.toRequestBody("text/plain".toMediaTypeOrNull())
            
            // 调用接口
            val response = userApi.uploadAvatarWithParams(avatarPart, userIdBody, tokenBody)
            
            if (response.isSuccessful && response.body()?.error_code == "0") {
                val avatarUrl = response.body()?.data?.url
                if (avatarUrl != null) {
                    Result.success(avatarUrl)
                } else {
                    Result.failure(Exception("未返回头像URL"))
                }
            } else {
                Result.failure(Exception(response.body()?.error_msg ?: "上传失败"))
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 上传头像 - 方式3：使用 Header 传递 Token
     * 
     * @param file 头像文件
     * @param token 认证 Token
     */
    suspend fun uploadAvatarWithToken(file: File, token: String): Result<String> {
        return try {
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val avatarPart = MultipartBody.Part.createFormData("avatar", file.name, requestFile)
            
            // Token 通过 Header 传递（格式：Bearer your_token）
            val authToken = "Bearer $token"
            
            val response = userApi.uploadAvatarWithToken(authToken, avatarPart)
            
            if (response.isSuccessful && response.body()?.error_code == "0") {
                val avatarUrl = response.body()?.data?.url
                if (avatarUrl != null) {
                    Result.success(avatarUrl)
                } else {
                    Result.failure(Exception("未返回头像URL"))
                }
            } else {
                Result.failure(Exception(response.body()?.error_msg ?: "上传失败"))
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
