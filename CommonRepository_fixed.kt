package com.smzdm.android.module.user.repository

import com.smzdm.android.base.repository.BaseRepository
import com.smzdm.android.module.user.api.UserCommonApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

class CommonRepository @Inject constructor(
    private val mApi: UserCommonApiService
) : BaseRepository() {

    /**
     * 上传头像
     * 
     * @param image 图片文件
     * @param bucketName 存储桶名称（可选）
     * @param customPath 自定义路径（可选）
     * @param customName 自定义名称（可选）
     */
    fun userUploadAvatar(
        image: File,
        bucketName: String? = null,
        customPath: String? = null,
        customName: String? = null
    ) = requestResult {
        // 1. 创建文件的 RequestBody
        val requestFile = image.asRequestBody("image/jpeg".toMediaTypeOrNull())
        
        // 2. 创建 MultipartBody.Part（参数名改为 "image"）
        val imagePart = MultipartBody.Part.createFormData(
            "image",        // ✅ 修正：参数名是 "image" 不是 "avatar"
            image.name,
            requestFile
        )

        // 3. 创建其他参数的 RequestBody（如果有值的话）
        val bucketNameBody = bucketName?.toRequestBody("text/plain".toMediaTypeOrNull())
        val customPathBody = customPath?.toRequestBody("text/plain".toMediaTypeOrNull())
        val customNameBody = customName?.toRequestBody("text/plain".toMediaTypeOrNull())

        // 4. 调用 API
        mApi.userUploadAvatar(
            image = imagePart,
            bucketName = bucketNameBody,
            customPath = customPathBody,
            customName = customNameBody
        )
    }
}
