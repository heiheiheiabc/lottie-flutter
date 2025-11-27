# 快速修复总结

## 🔥 3 个关键修改

### 1️⃣ API 接口（最重要！）

```kotlin
// ❌ 错误代码
@POST("image/upload")
@FormUrlEncoded      // ❌ 删除这行！
@Multipart
suspend fun userUploadAvatar(
    @Part("image") image: MultipartBody.Part,
    @Field("bucket_name") buketName: String? = null,  // ❌ 改为 @Part
    // ...
)

// ✅ 正确代码
@POST("image/upload")
@Multipart           // ✅ 只保留这个
suspend fun userUploadAvatar(
    @Part image: MultipartBody.Part,
    @Part("bucket_name") bucketName: RequestBody? = null,  // ✅ @Part + RequestBody
    @Part("custom_path") customPath: RequestBody? = null,
    @Part("custom_name") customName: RequestBody? = null
): ResponseData<UserSetAvatarBean>
```

---

### 2️⃣ Repository

```kotlin
// ❌ 错误代码
val body = MultipartBody.Part.createFormData("avatar", image.name, requestFile)
//                                            ^^^^^^^ 错误！

mApi.userUploadAvatar(
    image = body,
    buketName = buketName,        // ❌ String 类型错误
    customPath = customPath,
    customName = customName
)

// ✅ 正确代码
val imagePart = MultipartBody.Part.createFormData("image", image.name, requestFile)
//                                                 ^^^^^ 改为 "image"

val bucketNameBody = bucketName?.toRequestBody("text/plain".toMediaTypeOrNull())
val customPathBody = customPath?.toRequestBody("text/plain".toMediaTypeOrNull())
val customNameBody = customName?.toRequestBody("text/plain".toMediaTypeOrNull())

mApi.userUploadAvatar(
    image = imagePart,
    bucketName = bucketNameBody,   // ✅ RequestBody 类型
    customPath = customPathBody,
    customName = customNameBody
)
```

---

### 3️⃣ ViewModel（可选优化）

```kotlin
// ✅ 添加日志和错误处理
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
        // 根据你的实际返回结构获取 URL
        val avatarUrl = data?.data?.avatarUrl ?: ""
        _uploadState.value = UploadState.Success(avatarUrl)
    } else {
        _uploadState.value = UploadState.Error("上传失败")
    }
}
```

---

## 📋 修改步骤

1. **修改 API 接口**
   - 删除 `@FormUrlEncoded`
   - `@Field` 改为 `@Part`
   - `String?` 改为 `RequestBody?`

2. **修改 Repository**
   - `"avatar"` 改为 `"image"`
   - String 参数转换为 RequestBody：
     ```kotlin
     val body = string?.toRequestBody("text/plain".toMediaTypeOrNull())
     ```

3. **测试**
   - 运行项目
   - 查看 Logcat 日志
   - 验证上传功能

---

## 🎯 一句话总结

**Multipart 请求不能用 `@FormUrlEncoded`，所有参数都要用 `@Part`！**
