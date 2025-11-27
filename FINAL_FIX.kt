// ============================================
// 1. API 接口定义（关键修改）
// ============================================

interface UserCommonApiService {

    /**
     * 用户上传头像
     * 
     * 关键点：
     * 1. @Part image: MultipartBody.Part - 不能有参数名 @Part("image")
     * 2. 其他参数用 @Part("参数名") + RequestBody
     */
    @POST("image/upload")
    @Multipart
    suspend fun userUploadAvatar(
        @Part image: MultipartBody.Part,  // ✅ 关键：不要写成 @Part("image")
        @Part("bucket_name") bucketName: RequestBody? = null,
        @Part("custom_path") customPath: RequestBody? = null,
        @Part("custom_name") customName: RequestBody? = null
    ): ResponseData<UserSetAvatarBean>
}

// ============================================
// 2. Repository 实现
// ============================================

class CommonRepository @Inject constructor(
    private val mApi: UserCommonApiService
) : BaseRepository() {

    /**
     * 上传头像
     * 
     * @param image 图片文件
     * @param bucketName 存储桶名称（可选）- 注意：这里是 String?
     * @param customPath 自定义路径（可选）- 注意：这里是 String?
     * @param customName 自定义名称（可选）- 注意：这里是 String?
     */
    fun userUploadAvatar(
        image: File,
        bucketName: String? = null,    // ✅ 接收 String? 类型
        customPath: String? = null,    // ✅ 接收 String? 类型
        customName: String? = null     // ✅ 接收 String? 类型
    ) = requestResult {
        // 1. 创建文件 Part
        val requestFile = image.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val imagePart = MultipartBody.Part.createFormData(
            "image",        // 参数名在这里指定
            image.name,
            requestFile
        )

        // 2. 将 String? 转换为 RequestBody?（在函数内部转换）
        val bucketNameBody = bucketName?.toRequestBody("text/plain".toMediaTypeOrNull())
        val customPathBody = customPath?.toRequestBody("text/plain".toMediaTypeOrNull())
        val customNameBody = customName?.toRequestBody("text/plain".toMediaTypeOrNull())

        // 3. 调用 API
        mApi.userUploadAvatar(
            image = imagePart,
            bucketName = bucketNameBody,    // 传递 RequestBody?
            customPath = customPathBody,
            customName = customNameBody
        )
    }
}

// ============================================
// 3. ViewModel 调用
// ============================================

@HiltViewModel
class ProfileUploadAvatarViewModel @Inject constructor(
    private val commonRepository: CommonRepository
) : ViewModel() {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    /**
     * 上传头像
     */
    fun uploadAvatar(
        imageFile: File,
        bucketName: String? = null,  // ✅ String? 类型
        customPath: String? = null,
        customName: String? = null
    ) {
        viewModelScope.launch {
            try {
                if (!imageFile.exists()) {
                    _uploadState.value = UploadState.Error("文件不存在")
                    return@launch
                }

                android.util.Log.d("UploadViewModel", "=== 开始上传头像 ===")
                android.util.Log.d("UploadViewModel", "文件: ${imageFile.absolutePath}")
                android.util.Log.d("UploadViewModel", "大小: ${imageFile.length() / 1024}KB")
                android.util.Log.d("UploadViewModel", "bucket_name: $bucketName")
                android.util.Log.d("UploadViewModel", "custom_path: $customPath")
                android.util.Log.d("UploadViewModel", "custom_name: $customName")

                _uploadState.value = UploadState.Loading

                // 调用 Repository（传递 String? 类型）
                commonRepository.userUploadAvatar(
                    image = imageFile,
                    bucketName = bucketName,
                    customPath = customPath,
                    customName = customName
                ).catch { e ->
                    android.util.Log.e("UploadViewModel", "上传异常", e)
                    _uploadState.value = UploadState.Error(e.message ?: "上传失败")
                }.collect { result ->
                    if (result.isSuccess) {
                        val data = result.getOrNull()
                        android.util.Log.d("UploadViewModel", "上传成功: $data")
                        
                        // 根据你的实际返回结构获取 URL
                        val avatarUrl = data?.data?.url ?: ""
                        _uploadState.value = UploadState.Success(avatarUrl)
                    } else {
                        val error = result.exceptionOrNull()
                        android.util.Log.e("UploadViewModel", "上传失败", error)
                        _uploadState.value = UploadState.Error(error?.message ?: "上传失败")
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("UploadViewModel", "上传发生异常", e)
                _uploadState.value = UploadState.Error(e.message ?: "未知错误")
            }
        }
    }

    sealed class UploadState {
        object Idle : UploadState()
        object Loading : UploadState()
        data class Success(val avatarUrl: String) : UploadState()
        data class Error(val message: String) : UploadState()
    }
}

// ============================================
// 4. Activity 中调用（只传文件，其他参数为 null）
// ============================================

class ProfileSettingsActivity : BaseActivity() {

    private val uploadViewModel by viewModels<ProfileUploadAvatarViewModel>()

    private fun handleCropResult() {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, mUri)
            val circleBitmap = AvatarUploadHelper.getCircleBitmap(bitmap)
            ivAvatar.setImageBitmap(circleBitmap)

            lifecycleScope.launch(Dispatchers.IO) {
                val savedFile = AvatarUploadHelper.processCroppedBitmap(
                    this@ProfileSettingsActivity,
                    bitmap
                )

                if (savedFile != null) {
                    withContext(Dispatchers.Main) {
                        // 只传文件，其他参数传 null
                        uploadViewModel.uploadAvatar(
                            imageFile = savedFile,
                            bucketName = null,  // ✅ 传 null
                            customPath = null,  // ✅ 传 null
                            customName = null   // ✅ 传 null
                        )
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ============================================
// 关键点总结
// ============================================

/*
1. API 接口：
   @Part image: MultipartBody.Part  ✅ 正确（不要加参数名）
   @Part("image") image: MultipartBody.Part  ❌ 错误

2. Repository 参数类型：
   fun userUploadAvatar(image: File, bucketName: String? = null)  ✅ 正确
   fun userUploadAvatar(image: File, bucketName: RequestBody? = null)  ❌ 错误

3. 转换时机：
   在 Repository 函数内部将 String? 转换为 RequestBody?

4. 调用时：
   uploadViewModel.uploadAvatar(imageFile, null, null, null)  ✅ 传 null 即可
*/
