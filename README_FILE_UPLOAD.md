# 头像上传 - 文件上传方式（Multipart）

## 📝 概述

后端需要的是 **文件上传（Multipart/form-data）**，不是 Base64 字符串。

## 🎯 核心流程

```
裁剪图片
    ↓
获取 Bitmap
    ↓
转换为圆形并显示
    ↓
后台处理：
    1. 压缩 Bitmap
    2. 保存到文件 (File)
    ↓
创建 MultipartBody.Part
    ↓
调用 Retrofit API 上传
    ↓
监听上传结果
```

## 📁 文件结构

```
AvatarUploadHelper.kt              # 图片处理工具类（压缩、保存）
UploadAvatarApi.kt                 # API 接口定义
UserRepository_upload.kt           # Repository 层（上传逻辑）
ProfileUploadAvatarViewModel_file.kt  # ViewModel（状态管理）
ProfileSettingsActivity_simple.kt  # Activity（UI 层）
```

## 🚀 快速开始

### 1️⃣ 在 Activity 中处理裁剪结果

```kotlin
private fun handleCropResult() {
    try {
        val originalBitmap = MediaStore.Images.Media.getBitmap(contentResolver, mUri)
        
        // 显示圆形头像
        val circleBitmap = AvatarUploadHelper.getCircleBitmap(originalBitmap)
        ivAvatar.setImageBitmap(circleBitmap)

        // 后台处理：压缩 → 保存 → 上传
        lifecycleScope.launch(Dispatchers.IO) {
            // 保存到文件
            val savedFile = AvatarUploadHelper.processCroppedBitmap(
                this@ProfileSettingsActivity,
                originalBitmap
            )

            if (savedFile != null) {
                // 切换到主线程上传
                withContext(Dispatchers.Main) {
                    uploadAvatarFile(savedFile)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun uploadAvatarFile(file: File) {
    // 调用 ViewModel 上传
    uploadViewModel.uploadAvatar(file)
}
```

### 2️⃣ 监听上传状态

```kotlin
private fun observeUploadState() {
    lifecycleScope.launch {
        uploadViewModel.uploadState.collectLatest { state ->
            when (state) {
                is UploadState.Loading -> {
                    // 显示加载中
                    ivAvatar.alpha = 0.5f
                }
                is UploadState.Success -> {
                    // 上传成功
                    Toast.makeText(this, "上传成功", Toast.LENGTH_SHORT).show()
                    avatar = state.avatarUrl
                }
                is UploadState.Error -> {
                    // 上传失败
                    Toast.makeText(this, "上传失败: ${state.message}", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }
}
```

## 🔧 核心代码

### AvatarUploadHelper - 图片处理工具

```kotlin
object AvatarUploadHelper {
    /**
     * 一键处理：转圆形 → 压缩 → 保存
     */
    fun processCroppedBitmap(context: Context, bitmap: Bitmap): File? {
        val circleBitmap = getCircleBitmap(bitmap)
        val compressedBitmap = compressBitmap(circleBitmap)
        return saveBitmapToFile(context, compressedBitmap)
    }

    /**
     * 保存到文件
     */
    fun saveBitmapToFile(context: Context, bitmap: Bitmap): File? {
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "avatar_${System.currentTimeMillis()}.jpg"
        )
        
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        return file
    }
}
```

### UserApi - API 定义

```kotlin
interface UserApi {
    @Multipart
    @POST("api/upload/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part
    ): Response<AvatarUploadResponse>
}
```

### UserRepository - 上传逻辑

```kotlin
class UserRepository @Inject constructor(private val userApi: UserApi) {
    
    suspend fun uploadAvatar(file: File): Result<String> {
        return try {
            // 创建 RequestBody
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            
            // 创建 MultipartBody.Part
            val body = MultipartBody.Part.createFormData(
                "avatar",      // 参数名（和后端约定）
                file.name,     // 文件名
                requestFile
            )
            
            // 调用接口
            val response = userApi.uploadAvatar(body)
            
            // 处理结果
            if (response.isSuccessful && response.body()?.error_code == "0") {
                Result.success(response.body()?.data?.url!!)
            } else {
                Result.failure(Exception(response.body()?.error_msg ?: "上传失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### ViewModel - 状态管理

```kotlin
@HiltViewModel
class ProfileUploadAvatarViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    fun uploadAvatar(file: File) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Loading
            
            val result = userRepository.uploadAvatar(file)
            
            _uploadState.value = if (result.isSuccess) {
                UploadState.Success(result.getOrNull()!!)
            } else {
                UploadState.Error(result.exceptionOrNull()?.message ?: "上传失败")
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

## ❓ 需要向后端确认的信息

在开始之前，请向后端同事确认以下信息：

### 1. 参数名称
```kotlin
// 后端接收的参数名是什么？
@Part avatar: MultipartBody.Part         // "avatar" ?
@Part file: MultipartBody.Part           // "file" ?
@Part image: MultipartBody.Part          // "image" ?
```

### 2. 请求格式
```
接口地址：https://your-api.com/api/upload/avatar
请求方式：POST
Content-Type：multipart/form-data
```

### 3. 其他参数
```kotlin
// 是否需要传递其他参数？
@Part("userId") userId: RequestBody      // 用户ID ?
@Part("token") token: RequestBody        // Token ?
@Header("Authorization") auth: String    // 认证信息 ?
```

### 4. 响应格式
```json
{
  "error_code": "0",
  "error_msg": "成功",
  "data": {
    "url": "https://cdn.example.com/avatar/xxx.jpg"
  }
}
```

### 5. 限制条件
- 文件大小限制：最大 5MB？
- 文件格式：支持 jpg, png？
- 图片尺寸：最大 2048x2048？

## 🧪 测试步骤

### 使用 Postman 测试

1. 创建 POST 请求
2. URL: `https://your-api.com/api/upload/avatar`
3. Body 选择 `form-data`
4. 添加字段：
   - Key: `avatar` (类型选择 File)
   - Value: 选择一个图片文件
5. 如果需要 Token，在 Headers 中添加：
   - Key: `Authorization`
   - Value: `Bearer your_token`
6. 发送请求，查看响应

## 📊 日志输出

代码中已添加详细日志，便于调试：

```kotlin
// Activity
android.util.Log.d("ProfileSettings", "文件路径: ${file.absolutePath}")
android.util.Log.d("ProfileSettings", "文件大小: ${file.length() / 1024}KB")

// Helper
android.util.Log.d("AvatarUploadHelper", "图片已保存: ${file.absolutePath}")
android.util.Log.d("AvatarUploadHelper", "文件大小: ${file.length() / 1024}KB")

// ViewModel
android.util.Log.d("UploadViewModel", "上传成功: $avatarUrl")
android.util.Log.e("UploadViewModel", "上传失败", error)
```

## ⚠️ 常见问题

### 1. 参数名不正确
**错误**：后端返回 400 或参数缺失
**解决**：确认后端接收的参数名，修改 `createFormData` 的第一个参数

```kotlin
// 修改这里的 "avatar" 为后端要求的参数名
MultipartBody.Part.createFormData("avatar", file.name, requestFile)
```

### 2. 文件过大
**错误**：上传超时或 413 错误
**解决**：增加压缩程度，降低图片质量

```kotlin
// 修改压缩质量（0-100）
bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)

// 修改最大尺寸
val MAX_IMAGE_SIZE = 800  // 从 1024 改为 800
```

### 3. 超时错误
**错误**：SocketTimeoutException
**解决**：增加超时时间

```kotlin
OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)  // 上传时间更长
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
```

### 4. 401 未授权
**错误**：HTTP 401
**解决**：检查 Token 是否正确，是否过期

```kotlin
// 通过 Header 传递 Token
@Header("Authorization") token: String

// 调用时
uploadAvatarWithToken(file, "Bearer $token")
```

## 📦 依赖项

确保你的 `build.gradle` 包含：

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
    
    // Hilt
    implementation "com.google.dagger:hilt-android:2.48"
    kapt "com.google.dagger:hilt-compiler:2.48"
}
```

## ✅ 使用清单

- [ ] 复制 `AvatarUploadHelper.kt` 工具类
- [ ] 定义 `UserApi` 接口
- [ ] 实现 `UserRepository` 上传逻辑
- [ ] 实现 `ViewModel` 状态管理
- [ ] 在 Activity 中调用上传
- [ ] 向后端确认参数名
- [ ] 使用 Postman 测试接口
- [ ] 添加日志调试
- [ ] 测试完整流程
- [ ] 处理异常情况

## 🎉 完成

现在你已经有了一个完整的文件上传功能！

关键点：
1. ✅ 保存 Bitmap 到文件
2. ✅ 使用 Multipart 方式上传
3. ✅ 详细的日志输出
4. ✅ 完整的错误处理

有任何问题随时联系后端确认！
