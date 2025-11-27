package com.smzdm.android.module.user.api

import com.smzdm.android.base.network.ResponseData
import com.smzdm.android.module.user.bean.UserSetAvatarBean
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface UserCommonApiService {

    /**
     * 用户上传头像
     * 
     * 注意：
     * 1. 不能同时使用 @FormUrlEncoded 和 @Multipart
     * 2. 文件用 @Part，其他参数也要用 @Part（不能用 @Field）
     */
    @POST("image/upload")
    @Multipart  // ❌ 删除 @FormUrlEncoded
    suspend fun userUploadAvatar(
        @Part image: MultipartBody.Part,  // 文件参数，参数名 "image"
        @Part("bucket_name") bucketName: RequestBody? = null,      // ✅ 改为 @Part
        @Part("custom_path") customPath: RequestBody? = null,      // ✅ 改为 @Part
        @Part("custom_name") customName: RequestBody? = null       // ✅ 改为 @Part
    ): ResponseData<UserSetAvatarBean>
}
