# 错误修正对比

## 🚨 错误 1：API 接口定义

### ❌ 你的代码（会报错）

```kotlin
@POST("image/upload")
@Multipart
suspend fun userUploadAvatar(
    @Part("image") image: MultipartBody.Part,  // ❌ 错误：不能有 "image"
    // ...
)
```

**报错信息**：
```
java.lang.IllegalArgumentException: @Part parameters using the MultipartBody.Part 
must not include a part name in the annotation. (parameter #1)
```

### ✅ 正确代码

```kotlin
@POST("image/upload")
@Multipart
suspend fun userUploadAvatar(
    @Part image: MultipartBody.Part,  // ✅ 正确：去掉 ("image")
    @Part("bucket_name") bucketName: RequestBody? = null,
    @Part("custom_path") customPath: RequestBody? = null,
    @Part("custom_name") customName: RequestBody? = null
): ResponseData<UserSetAvatarBean>
```

**为什么**：
- `MultipartBody.Part` 在创建时已经指定了参数名：
  ```kotlin
  MultipartBody.Part.createFormData("image", ...)
  //                                ^^^^^^^ 参数名在这里
  ```
- 所以 `@Part` 注解不需要再写参数名
- 其他普通参数（`RequestBody`）需要写参数名：`@Part("bucket_name")`

---

## 🚨 错误 2：Repository 参数类型和转换

### ❌ 你的代码（会报红）

```kotlin
fun userUploadAvatar(
    image: File, 
    buketName: RequestBody? = null,  // ❌ 错误：类型应该是 String?
    customPath: RequestBody? = null,
    customName: RequestBody? = null
) = requestResult {
    // ...
    val bucketNameBody = buketName?.toRequestBody(...)  // ❌ 报红！RequestBody 没有 toRequestBody() 方法
    
    mApi.userUploadAvatar(
        image = body,
        buketName = buketName,  // ❌ 参数名拼写错误（buket 应该是 bucket）
        // ...
    )
}
```

### ✅ 正确代码

```kotlin
fun userUploadAvatar(
    image: File,
    bucketName: String? = null,  // ✅ 正确：接收 String? 类型
    customPath: String? = null,
    customName: String? = null
) = requestResult {
    // 1. 创建文件 Part
    val requestFile = image.asRequestBody("image/jpeg".toMediaTypeOrNull())
    val imagePart = MultipartBody.Part.createFormData("image", image.name, requestFile)

    // 2. 将 String? 转换为 RequestBody?
    val bucketNameBody = bucketName?.toRequestBody("text/plain".toMediaTypeOrNull())
    val customPathBody = customPath?.toRequestBody("text/plain".toMediaTypeOrNull())
    val customNameBody = customName?.toRequestBody("text/plain".toMediaTypeOrNull())

    // 3. 调用 API
    mApi.userUploadAvatar(
        image = imagePart,
        bucketName = bucketNameBody,  // ✅ 拼写修正
        customPath = customPathBody,
        customName = customNameBody
    )
}
```

---

## 📋 完整的修改清单

### 1️⃣ API 接口（UserCommonApiService.kt）

```kotlin
interface UserCommonApiService {
    @POST("image/upload")
    @Multipart
    suspend fun userUploadAvatar(
        @Part image: MultipartBody.Part,                          // ✅ 去掉 ("image")
        @Part("bucket_name") bucketName: RequestBody? = null,     // ✅ 保留参数名
        @Part("custom_path") customPath: RequestBody? = null,
        @Part("custom_name") customName: RequestBody? = null
    ): ResponseData<UserSetAvatarBean>
}
```

### 2️⃣ Repository（CommonRepository.kt）

```kotlin
class CommonRepository @Inject constructor(
    private val mApi: UserCommonApiService
) : BaseRepository() {

    fun userUploadAvatar(
        image: File,
        bucketName: String? = null,  // ✅ String? 类型
        customPath: String? = null,
        customName: String? = null
    ) = requestResult {
        val requestFile = image.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val imagePart = MultipartBody.Part.createFormData("image", image.name, requestFile)

        val bucketNameBody = bucketName?.toRequestBody("text/plain".toMediaTypeOrNull())
        val customPathBody = customPath?.toRequestBody("text/plain".toMediaTypeOrNull())
        val customNameBody = customName?.toRequestBody("text/plain".toMediaTypeOrNull())

        mApi.userUploadAvatar(
            image = imagePart,
            bucketName = bucketNameBody,
            customPath = customPathBody,
            customName = customNameBody
        )
    }
}
```

### 3️⃣ ViewModel（保持不变）

```kotlin
// ViewModel 中调用时，传 String? 类型或 null
uploadViewModel.uploadAvatar(
    imageFile = savedFile,
    bucketName = null,  // ✅ 传 null 即可
    customPath = null,
    customName = null
)
```

---

## 🎯 关键规则

### 规则 1：`MultipartBody.Part` 的 `@Part` 不加参数名

```kotlin
// ✅ 正确
@Part image: MultipartBody.Part

// ❌ 错误
@Part("image") image: MultipartBody.Part
```

### 规则 2：`RequestBody` 的 `@Part` 要加参数名

```kotlin
// ✅ 正确
@Part("bucket_name") bucketName: RequestBody?

// ❌ 错误
@Part bucketName: RequestBody?
```

### 规则 3：参数类型转换在 Repository 内部进行

```kotlin
// ✅ 正确
fun upload(bucketName: String?) {
    val body = bucketName?.toRequestBody(...)
}

// ❌ 错误
fun upload(bucketName: RequestBody?) {
    val body = bucketName?.toRequestBody(...)  // RequestBody 没有这个方法
}
```

---

## 🧪 测试

修改完成后，运行项目，应该不会再报错。

查看 Logcat 日志：
```
D/UploadViewModel: === 开始上传头像 ===
D/UploadViewModel: 文件: /storage/.../avatar_xxx.jpg
D/UploadViewModel: 大小: 234KB
D/UploadViewModel: bucket_name: null
D/UploadViewModel: custom_path: null
D/UploadViewModel: custom_name: null
```

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

## ✅ 总结

**两个关键修改**：

1. API 接口：`@Part image: MultipartBody.Part`（去掉参数名）
2. Repository：接收 `String?` 类型，内部转换为 `RequestBody?`

完成这两个修改后，代码应该可以正常运行了！
