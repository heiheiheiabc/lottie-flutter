# 文件上传方式指南

## 📝 后端需要的文件格式说明

后端需要的是 **Multipart/form-data** 格式的文件上传，不是 Base64。

### 常见的文件上传格式

#### 1. Multipart/form-data（最常见）

```http
POST /api/upload/avatar HTTP/1.1
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary

------WebKitFormBoundary
Content-Disposition: form-data; name="avatar"; filename="avatar.jpg"
Content-Type: image/jpeg

[文件二进制数据]
------WebKitFormBoundary--
```

#### 2. 其他可能的格式

- **application/octet-stream** - 二进制流
- **image/jpeg** - 直接发送图片
- **application/x-www-form-urlencoded** - 表单（不适合文件）

## 🚀 Android 文件上传实现方式

### 方式一：Retrofit + Multipart（推荐）

#### 1. API 接口定义

```kotlin
interface UserApi {
    /**
     * 上传头像 - Multipart 方式
     * @param avatar 头像文件
     */
    @Multipart
    @POST("api/upload/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part
    ): Response<AvatarUploadResponse>
    
    // 如果还需要其他参数
    @Multipart
    @POST("api/upload/avatar")
    suspend fun uploadAvatarWithParams(
        @Part avatar: MultipartBody.Part,
        @Part("userId") userId: RequestBody,
        @Part("token") token: RequestBody
    ): Response<AvatarUploadResponse>
}

data class AvatarUploadResponse(
    val error_code: String,
    val error_msg: String,
    val data: AvatarData?
)

data class AvatarData(
    val url: String  // 上传成功后的头像 URL
)
```

#### 2. Repository 实现

```kotlin
class UserRepository @Inject constructor(
    private val userApi: UserApi
) {
    suspend fun uploadAvatar(file: File): Result<String> {
        return try {
            // 创建 RequestBody
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            
            // 创建 MultipartBody.Part
            val body = MultipartBody.Part.createFormData(
                "avatar",           // 参数名（根据后端要求）
                file.name,          // 文件名
                requestFile
            )
            
            // 调用接口
            val response = userApi.uploadAvatar(body)
            
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
```

#### 3. ViewModel 调用

```kotlin
@HiltViewModel
class ProfileUploadAvatarViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    /**
     * 上传头像文件
     */
    fun uploadAvatar(file: File) {
        viewModelScope.launch {
            try {
                _uploadState.value = UploadState.Loading
                
                val result = userRepository.uploadAvatar(file)
                
                if (result.isSuccess) {
                    _uploadState.value = UploadState.Success(result.getOrNull()!!)
                } else {
                    _uploadState.value = UploadState.Error(
                        result.exceptionOrNull()?.message ?: "上传失败"
                    )
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                _uploadState.value = UploadState.Error(e.message ?: "上传失败")
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
```

---

### 方式二：OkHttp 直接上传

```kotlin
object OkHttpUploadHelper {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 使用 OkHttp 上传文件
     */
    suspend fun uploadFile(url: String, file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "avatar",                                    // 参数名
                    file.name,                                   // 文件名
                    file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                // 如果需要添加其他参数
                // .addFormDataPart("userId", "123")
                // .addFormDataPart("token", "your_token")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                // 如果需要添加 Header
                // .addHeader("Authorization", "Bearer token")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                // 解析返回的 JSON
                Result.success(responseBody ?: "")
            } else {
                Result.failure(Exception("上传失败: ${response.code}"))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
```

---

### 方式三：Volley 上传（不推荐，较老）

```kotlin
class VolleyMultipartRequest(
    method: Int,
    url: String,
    private val file: File,
    listener: Response.Listener<NetworkResponse>,
    errorListener: Response.ErrorListener
) : Request<NetworkResponse>(method, url, errorListener) {

    private val boundary = "apiclient-${System.currentTimeMillis()}"

    override fun getBodyContentType(): String {
        return "multipart/form-data; boundary=$boundary"
    }

    override fun getBody(): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val dataOutputStream = DataOutputStream(byteArrayOutputStream)

        try {
            // 添加文件
            dataOutputStream.writeBytes("--$boundary\r\n")
            dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"avatar\"; filename=\"${file.name}\"\r\n")
            dataOutputStream.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            
            val fileInputStream = FileInputStream(file)
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                dataOutputStream.write(buffer, 0, bytesRead)
            }
            fileInputStream.close()
            
            dataOutputStream.writeBytes("\r\n")
            dataOutputStream.writeBytes("--$boundary--\r\n")

            return byteArrayOutputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            return ByteArray(0)
        }
    }
}
```

---

## 📱 在 Activity 中使用

### 完整示例

```kotlin
/**
 * 处理裁剪结果
 */
private fun handleCropResult() {
    try {
        if (mUri == null) {
            Toast.makeText(this, "裁剪失败", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. 从 Uri 读取 Bitmap
        val originalBitmap = MediaStore.Images.Media.getBitmap(contentResolver, mUri)
        if (originalBitmap == null) {
            Toast.makeText(this, "无法获取图片", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. 显示圆形头像
        val circleBitmap = AvatarUploadHelper.getCircleBitmap(originalBitmap)
        ivAvatar.setImageBitmap(circleBitmap)

        // 3. 在后台处理文件保存和上传
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 处理 Bitmap：转圆形 → 压缩 → 保存
                val savedFile = AvatarUploadHelper.processCroppedBitmap(
                    this@ProfileSettingsActivity,
                    originalBitmap
                )

                if (savedFile == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ProfileSettingsActivity,
                            "保存图片失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                // 4. 上传文件
                withContext(Dispatchers.Main) {
                    uploadAvatarFile(savedFile)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ProfileSettingsActivity,
                        "处理图片失败: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(this, "处理图片出错: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 上传头像文件
 */
private fun uploadAvatarFile(file: File) {
    if (isUploadingAvatar) {
        Toast.makeText(this, "正在上传中，请稍候", Toast.LENGTH_SHORT).show()
        return
    }

    android.util.Log.d("ProfileSettings", "开始上传头像文件")
    android.util.Log.d("ProfileSettings", "文件路径: ${file.absolutePath}")
    android.util.Log.d("ProfileSettings", "文件大小: ${file.length() / 1024}KB")

    // 调用 ViewModel 上传文件
    uploadViewModel.uploadAvatar(file)
}
```

---

## 🔍 如何确定后端需要的格式？

### 1. 查看后端文档

```yaml
# Swagger/OpenAPI 示例
/api/upload/avatar:
  post:
    consumes:
      - multipart/form-data
    parameters:
      - in: formData
        name: avatar
        type: file
        description: 头像文件
```

### 2. 询问后端同事

需要确认以下信息：
- ✅ 参数名是什么？（如：`avatar`, `file`, `image`）
- ✅ 是否需要其他参数？（如：`userId`, `token`）
- ✅ Content-Type 是什么？（通常是 `multipart/form-data`）
- ✅ 文件大小限制？（如：最大 5MB）
- ✅ 支持的文件格式？（如：jpg, png）
- ✅ 返回的数据格式？

### 3. 使用 Postman 测试

```bash
# Postman 配置
POST http://your-api.com/api/upload/avatar
Body:
  - form-data
  - Key: avatar (type: File)
  - Value: [选择文件]
```

---

## 📝 常见的参数名

不同后端可能使用不同的参数名：

```kotlin
// 参数名示例
"avatar"     // 最常见
"file"       // 通用文件
"image"      // 图片
"headimg"    // 头像
"photo"      // 照片
"picture"    // 图片
```

---

## 🎯 完整的文件上传流程

```
裁剪图片
    ↓
获取 Bitmap (MediaStore.Images.Media.getBitmap)
    ↓
转换为圆形 (AvatarUploadHelper.getCircleBitmap)
    ↓
显示到界面 (ivAvatar.setImageBitmap)
    ↓
压缩图片 (AvatarUploadHelper.compressBitmap)
    ↓
保存到文件 (AvatarUploadHelper.saveBitmapToFile)
    ↓
创建 MultipartBody.Part
    ↓
调用 Retrofit 接口上传
    ↓
监听上传结果
```

---

## ⚠️ 注意事项

1. **文件路径**：使用应用专属目录，不需要存储权限
2. **文件大小**：上传前压缩，避免超过服务器限制
3. **内存管理**：及时释放 Bitmap
4. **网络超时**：设置合理的超时时间（建议 30-60 秒）
5. **错误处理**：捕获网络异常、文件异常等
6. **清理临时文件**：上传成功后删除临时文件

---

## 🚀 推荐配置

### Retrofit 配置

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)  // 上传文件需要更长时间
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://your-api.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi {
        return retrofit.create(UserApi::class.java)
    }
}
```

---

## 📚 依赖项

```gradle
dependencies {
    // Retrofit
    implementation "com.squareup.retrofit2:retrofit:2.9.0"
    implementation "com.squareup.retrofit2:converter-gson:2.9.0"
    
    // OkHttp
    implementation "com.squareup.okhttp3:okhttp:4.11.0"
    implementation "com.squareup.okhttp3:logging-interceptor:4.11.0"
    
    // Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
}
```
