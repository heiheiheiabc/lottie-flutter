# 头像上传代码修正指南

## ❌ 原代码的问题

### 1. API 接口定义错误

```kotlin
// ❌ 错误：不能同时使用 @FormUrlEncoded 和 @Multipart
@POST("image/upload")
@FormUrlEncoded  // ❌ 删除这个
@Multipart
suspend fun userUploadAvatar(
    @Part("image") image: MultipartBody.Part,
    @Field("bucket_name") buketName: String? = null,  // ❌ @Field 不能用于 Multipart
    @Field("custom_path") customPath: String? = null,
    @Field("custom_name") customName: String? = null
): ResponseData<UserSetAvatarBean>
```

**问题说明**：
- `@FormUrlEncoded` 用于表单提交（application/x-www-form-urlencoded）
- `@Multipart` 用于文件上传（multipart/form-data）
- **这两个注解不能同时使用！**
- 使用 `@Multipart` 时，所有参数都要用 `@Part`，不能用 `@Field`

---

### 2. Repository 参数名错误

```kotlin
// ❌ 错误：参数名应该是 "image" 不是 "avatar"
val body = MultipartBody.Part.createFormData("avatar", image.name, requestFile)
//                                            ^^^^^^
//                                            应该是 "image"
```

---

### 3. 其他参数类型错误

```kotlin
// ❌ 错误：String 类型不能直接传给 @Part
mApi.userUploadAvatar(
    image = body,
    buketName = buketName,        // ❌ 应该转换为 RequestBody
    customPath = customPath,      // ❌ 应该转换为 RequestBody
    customName = customName       // ❌ 应该转换为 RequestBody
)
```

---

## ✅ 修正后的代码

### 1. API 接口（UserCommonApiService.kt）

```kotlin
interface UserCommonApiService {

    /**
     * 用户上传头像
     */
    @POST("image/upload")
    @Multipart  // ✅ 只保留 @Multipart
    suspend fun userUploadAvatar(
        @Part image: MultipartBody.Part,                           // ✅ 文件参数
        @Part("bucket_name") bucketName: RequestBody? = null,     // ✅ 改为 @Part + RequestBody
        @Part("custom_path") customPath: RequestBody? = null,     // ✅ 改为 @Part + RequestBody
        @Part("custom_name") customName: RequestBody? = null      // ✅ 改为 @Part + RequestBody
    ): ResponseData<UserSetAvatarBean>
}
```

---

### 2. Repository（CommonRepository.kt）

```kotlin
class CommonRepository @Inject constructor(
    private val mApi: UserCommonApiService
) : BaseRepository() {

    fun userUploadAvatar(
        image: File,
        bucketName: String? = null,
        customPath: String? = null,
        customName: String? = null
    ) = requestResult {
        // 1. 创建文件 Part
        val requestFile = image.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val imagePart = MultipartBody.Part.createFormData(
            "image",        // ✅ 参数名修正为 "image"
            image.name,
            requestFile
        )

        // 2. 创建其他参数的 RequestBody（如果有值）
        val bucketNameBody = bucketName?.toRequestBody("text/plain".toMediaTypeOrNull())
        val customPathBody = customPath?.toRequestBody("text/plain".toMediaTypeOrNull())
        val customNameBody = customName?.toRequestBody("text/plain".toMediaTypeOrNull())

        // 3. 调用 API
        mApi.userUploadAvatar(
            image = imagePart,
            bucketName = bucketNameBody,      // ✅ 传递 RequestBody
            customPath = customPathBody,      // ✅ 传递 RequestBody
            customName = customNameBody       // ✅ 传递 RequestBody
        )
    }
}
```

---

### 3. ViewModel（ProfileUploadAvatarViewModel.kt）

```kotlin
@HiltViewModel
class ProfileUploadAvatarViewModel @Inject constructor(
    private val commonRepository: CommonRepository
) : ViewModel() {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    fun uploadAvatar(
        imageFile: File,
        bucketName: String? = null,
        customPath: String? = null,
        customName: String? = null
    ) {
        viewModelScope.launch {
            try {
                // 检查文件
                if (!imageFile.exists()) {
                    _uploadState.value = UploadState.Error("文件不存在")
                    return@launch
                }

                // 开始上传
                _uploadState.value = UploadState.Loading

                // 调用 Repository
                commonRepository.userUploadAvatar(
                    image = imageFile,
                    bucketName = bucketName,
                    customPath = customPath,
                    customName = customName
                ).catch { e ->
                    // 捕获异常
                    _uploadState.value = UploadState.Error(e.message ?: "上传失败")
                }.collect { result ->
                    if (result.isSuccess) {
                        // ✅ 获取返回的头像 URL（根据实际结构调整）
                        val data = result.getOrNull()
                        val avatarUrl = data?.data?.avatarUrl ?: data?.data?.url ?: ""
                        _uploadState.value = UploadState.Success(avatarUrl)
                    } else {
                        val error = result.exceptionOrNull()
                        _uploadState.value = UploadState.Error(error?.message ?: "上传失败")
                    }
                }

            } catch (e: Exception) {
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
```

---

### 4. Activity 中调用

```kotlin
class ProfileSettingsActivity : BaseActivity() {

    private val uploadViewModel by viewModels<ProfileUploadAvatarViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ...
        observeUploadState()
    }

    /**
     * 监听上传状态
     */
    private fun observeUploadState() {
        lifecycleScope.launch {
            uploadViewModel.uploadState.collectLatest { state ->
                when (state) {
                    is ProfileUploadAvatarViewModel.UploadState.Idle -> {
                        // 空闲状态
                    }
                    is ProfileUploadAvatarViewModel.UploadState.Loading -> {
                        // 上传中
                        ivAvatar.alpha = 0.5f
                    }
                    is ProfileUploadAvatarViewModel.UploadState.Success -> {
                        // 上传成功
                        ivAvatar.alpha = 1.0f
                        Toast.makeText(this@ProfileSettingsActivity, "上传成功", Toast.LENGTH_SHORT).show()
                        
                        // 更新头像 URL
                        avatar = state.avatarUrl
                        LoginManager.instance.notifyUserInfoChanged()
                    }
                    is ProfileUploadAvatarViewModel.UploadState.Error -> {
                        // 上传失败
                        ivAvatar.alpha = 1.0f
                        Toast.makeText(
                            this@ProfileSettingsActivity,
                            "上传失败: ${state.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * 处理裁剪结果
     */
    private fun handleCropResult() {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, mUri)
            
            // 显示圆形头像
            val circleBitmap = AvatarUploadHelper.getCircleBitmap(bitmap)
            ivAvatar.setImageBitmap(circleBitmap)

            // 后台处理并上传
            lifecycleScope.launch(Dispatchers.IO) {
                // 保存到文件
                val savedFile = AvatarUploadHelper.processCroppedBitmap(
                    this@ProfileSettingsActivity,
                    bitmap
                )

                if (savedFile != null) {
                    withContext(Dispatchers.Main) {
                        // 上传文件
                        uploadViewModel.uploadAvatar(
                            imageFile = savedFile,
                            bucketName = null,      // 根据需要传值
                            customPath = null,      // 根据需要传值
                            customName = null       // 根据需要传值
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ProfileSettingsActivity, "保存文件失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "处理图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
```

---

## 📊 Multipart 请求格式说明

### HTTP 请求示例

```http
POST /image/upload HTTP/1.1
Host: your-api.com
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary

------WebKitFormBoundary
Content-Disposition: form-data; name="image"; filename="avatar.jpg"
Content-Type: image/jpeg

[图片二进制数据]
------WebKitFormBoundary
Content-Disposition: form-data; name="bucket_name"

my-bucket
------WebKitFormBoundary
Content-Disposition: form-data; name="custom_path"

avatars/
------WebKitFormBoundary
Content-Disposition: form-data; name="custom_name"

user_123.jpg
------WebKitFormBoundary--
```

---

## 🔍 关键点总结

### 1. Multipart 请求的规则

| 注解 | 用途 | 参数类型 |
|------|------|----------|
| `@Multipart` | 文件上传 | - |
| `@Part` | 文件参数 | `MultipartBody.Part` |
| `@Part("name")` | 普通参数 | `RequestBody` |
| `@Field` | ❌ 不能用于 Multipart | - |

### 2. 参数类型转换

```kotlin
// 文件参数
val file = File("path/to/image.jpg")
val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
val filePart = MultipartBody.Part.createFormData("image", file.name, requestFile)

// 字符串参数
val bucketName = "my-bucket"
val bucketNameBody = bucketName.toRequestBody("text/plain".toMediaTypeOrNull())
```

### 3. 可空参数处理

```kotlin
// 如果参数可能为 null
val bucketNameBody = bucketName?.toRequestBody("text/plain".toMediaTypeOrNull())
```

---

## 🧪 测试步骤

### 1. 使用 Logcat 查看日志

```
上传前：
D/UploadViewModel: === 开始上传头像 ===
D/UploadViewModel: 文件路径: /storage/.../avatar_1234567890.jpg
D/UploadViewModel: 文件大小: 234KB

上传成功：
D/UploadViewModel: 上传成功，返回数据: UserSetAvatarBean(avatarUrl=...)

上传失败：
E/UploadViewModel: 上传失败: 网络错误
```

### 2. 使用 Postman 测试

```
POST https://your-api.com/image/upload

Body (form-data):
├─ image: [选择文件] avatar.jpg
├─ bucket_name: my-bucket
├─ custom_path: avatars/
└─ custom_name: user_123.jpg
```

---

## ⚠️ 常见错误

### 错误 1: 400 Bad Request
**原因**：参数名不匹配  
**解决**：检查 `createFormData("image", ...)` 第一个参数是否和后端一致

### 错误 2: ClassCastException
**原因**：参数类型不匹配（String 传给了需要 RequestBody 的参数）  
**解决**：使用 `.toRequestBody()` 转换

### 错误 3: IllegalArgumentException
**原因**：同时使用了 `@FormUrlEncoded` 和 `@Multipart`  
**解决**：删除 `@FormUrlEncoded`

---

## ✅ 修改清单

- [x] 删除 API 接口的 `@FormUrlEncoded`
- [x] 将 `@Field` 改为 `@Part`，类型改为 `RequestBody`
- [x] Repository 中参数名改为 `"image"`
- [x] Repository 中字符串参数转换为 `RequestBody`
- [x] ViewModel 添加详细日志
- [x] ViewModel 正确获取返回的头像 URL

---

## 📦 需要导入的包

```kotlin
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
```

---

## 🎯 完整流程

```
用户选择图片
    ↓
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
创建其他参数的 RequestBody
    ↓
调用 Retrofit API 上传
    ↓
监听上传结果
    ↓
更新头像 URL
```

---

现在代码应该可以正常工作了！如果还有问题，检查 Logcat 日志定位具体错误。
