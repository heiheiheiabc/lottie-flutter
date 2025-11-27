# 头像上传完整实现指南

## 📋 功能概述

实现了从拍照/相册选择图片 → 裁剪 → 保存到文件 → 上传到服务器的完整流程。

## 🔑 核心流程

```
用户选择图片
    ↓
裁剪图片 (startPhotoZoom)
    ↓
获取裁剪后的 Bitmap (handleCropResult)
    ↓
转换为圆形 (getCircleBitmap)
    ↓
显示到界面 (ivAvatar.setImageBitmap)
    ↓
后台处理:
    1. 压缩图片 (compressBitmap)
    2. 保存到文件 (saveBitmapToFile)
    3. 转换为 Base64 (photoTo64BitString)
    4. 上传到服务器 (uploadAvatar)
    ↓
监听上传结果 (observeUploadState)
```

## 💻 关键代码解析

### 1. 处理裁剪结果并上传

```kotlin
private fun handleCropResult() {
    try {
        if (mUri == null) {
            Toast.makeText(this, "裁剪失败，无法获取图片", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. 从 Uri 读取 Bitmap
        val originalBitmap = MediaStore.Images.Media.getBitmap(contentResolver, mUri)
        if (originalBitmap == null) {
            Toast.makeText(this, "裁剪失败，无法获取图片", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. 转换为圆形
        val circleBitmap = getCircleBitmap(originalBitmap)
        
        // 3. 显示到界面
        ivAvatar.setImageBitmap(circleBitmap)

        // 4. 在后台线程处理图片保存和上传
        lifecycleScope.launch {
            try {
                // 压缩
                val compressedBitmap = withContext(Dispatchers.IO) {
                    compressBitmap(circleBitmap)
                }
                
                // 保存到文件
                val savedFile = withContext(Dispatchers.IO) {
                    saveBitmapToFile(compressedBitmap)
                }

                if (savedFile == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ProfileSettingsActivity, "保存图片失败", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 转换为 Base64
                val base64String = withContext(Dispatchers.IO) {
                    CommonUtil.photoTo64BitString(compressedBitmap)
                }

                // 上传
                withContext(Dispatchers.Main) {
                    uploadAvatar(base64String, savedFile)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileSettingsActivity, "处理图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(this, "处理裁剪图片时出错: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
```

### 2. 压缩图片

```kotlin
/**
 * 压缩 Bitmap
 * 如果图片尺寸过大，则缩小到指定大小
 */
private fun compressBitmap(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height

    // 如果图片尺寸小于最大尺寸，直接返回
    if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
        return bitmap
    }

    // 计算缩放比例
    val scale = if (width > height) {
        MAX_IMAGE_SIZE.toFloat() / width
    } else {
        MAX_IMAGE_SIZE.toFloat() / height
    }

    val newWidth = (width * scale).toInt()
    val newHeight = (height * scale).toInt()

    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}
```

### 3. 保存到文件

```kotlin
/**
 * 将 Bitmap 保存到文件
 * @return 保存的文件，如果失败返回 null
 */
private fun saveBitmapToFile(bitmap: Bitmap): File? {
    var outputStream: FileOutputStream? = null
    try {
        // 创建文件
        val file = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "avatar_${System.currentTimeMillis()}.jpg"
        )

        // 确保父目录存在
        file.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

        // 保存文件（质量90%）
        outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, outputStream)
        outputStream.flush()

        Log.d("ProfileSettings", "图片已保存: ${file.absolutePath}, 大小: ${file.length() / 1024}KB")

        return file

    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("ProfileSettings", "保存图片失败", e)
        return null
    } finally {
        try {
            outputStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
```

### 4. 上传头像

```kotlin
/**
 * 上传头像
 * @param base64String Base64 编码的图片字符串
 * @param imageFile 图片文件（可选，用于日志）
 */
private fun uploadAvatar(base64String: String, imageFile: File? = null) {
    if (isUploadingAvatar) {
        Toast.makeText(this, "正在上传中，请稍候", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        Log.d("ProfileSettings", "开始上传头像")
        imageFile?.let {
            Log.d("ProfileSettings", "图片文件: ${it.absolutePath}, 大小: ${it.length() / 1024}KB")
        }
        Log.d("ProfileSettings", "Base64 长度: ${base64String.length}")

        // 调用 ViewModel 上传
        uploadViewModel.uploadAvatar(base64String)

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(this, "上传头像失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
```

### 5. 监听上传状态

```kotlin
/**
 * 监听头像上传状态
 */
private fun observeUploadState() {
    lifecycleScope.launch {
        uploadViewModel.uploadState.collectLatest { state ->
            when (state) {
                is ProfileUploadAvatarViewModel.UploadState.Idle -> {
                    isUploadingAvatar = false
                }
                is ProfileUploadAvatarViewModel.UploadState.Loading -> {
                    isUploadingAvatar = true
                    showUploadLoading(true)
                }
                is ProfileUploadAvatarViewModel.UploadState.Success -> {
                    isUploadingAvatar = false
                    showUploadLoading(false)
                    Toast.makeText(this@ProfileSettingsActivity, "头像上传成功", Toast.LENGTH_SHORT).show()
                    
                    // 更新头像 URL
                    avatar = state.avatarUrl
                    LoginManager.instance.notifyUserInfoChanged()
                }
                is ProfileUploadAvatarViewModel.UploadState.Error -> {
                    isUploadingAvatar = false
                    showUploadLoading(false)
                    Toast.makeText(
                        this@ProfileSettingsActivity,
                        "头像上传失败: ${state.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
```

### 6. ViewModel 实现

```kotlin
@HiltViewModel
class ProfileUploadAvatarViewModel @Inject constructor(
    private val repository: UserRepository
) : ViewModel() {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    /**
     * 上传头像
     */
    fun uploadAvatar(base64String: String) {
        viewModelScope.launch {
            try {
                _uploadState.value = UploadState.Loading

                // 调用 API
                val response = repository.uploadAvatar(base64String)
                
                if (response.error_code == "0" && response.data?.url != null) {
                    _uploadState.value = UploadState.Success(response.data.url)
                } else {
                    _uploadState.value = UploadState.Error(response.error_msg ?: "上传失败")
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

## 📝 常量配置

```kotlin
companion object {
    // 图片压缩质量 (0-100)
    private const val COMPRESS_QUALITY = 90
    
    // 最大图片尺寸（像素）
    private const val MAX_IMAGE_SIZE = 1024
}
```

## 🎯 关键优化点

### 1. **异步处理**
- 使用 `lifecycleScope.launch` 在后台线程处理图片
- 使用 `withContext(Dispatchers.IO)` 执行 IO 操作
- 避免阻塞主线程

### 2. **图片压缩**
- 限制图片最大尺寸为 1024px
- 压缩质量设置为 90%
- 减少内存占用和上传时间

### 3. **错误处理**
- 每个步骤都有 try-catch
- 给用户清晰的错误提示
- 记录详细的日志便于调试

### 4. **内存管理**
- 及时关闭 Stream
- 压缩大图片
- 避免内存泄漏

### 5. **用户体验**
- 实时显示上传进度
- 防止重复上传
- 上传成功后更新头像

## 🔧 调试建议

### 1. 添加日志

```kotlin
android.util.Log.d("ProfileSettings", "开始处理裁剪结果")
android.util.Log.d("ProfileSettings", "Bitmap 尺寸: ${bitmap.width} x ${bitmap.height}")
android.util.Log.d("ProfileSettings", "压缩后尺寸: ${compressed.width} x ${compressed.height}")
android.util.Log.d("ProfileSettings", "文件大小: ${file.length() / 1024}KB")
android.util.Log.d("ProfileSettings", "Base64 长度: ${base64.length}")
```

### 2. 检查点

- ✅ FileProvider 配置是否正确
- ✅ 文件路径是否存在
- ✅ 文件是否保存成功
- ✅ Base64 转换是否成功
- ✅ 网络权限是否授予
- ✅ 上传接口是否正常

### 3. 常见问题

**问题1: FileNotFoundException**
- 检查目录是否创建成功
- 检查 FileProvider 配置

**问题2: OutOfMemoryError**
- 增加图片压缩
- 减小 MAX_IMAGE_SIZE

**问题3: 上传失败**
- 检查网络连接
- 检查 Base64 格式
- 检查服务器接口

## 📱 测试清单

- [ ] 拍照选择头像
- [ ] 相册选择头像
- [ ] 图片裁剪
- [ ] 图片显示
- [ ] 图片保存
- [ ] Base64 转换
- [ ] 上传接口调用
- [ ] 上传成功回调
- [ ] 上传失败提示
- [ ] 重复上传拦截
- [ ] 大图片压缩
- [ ] 内存占用正常

## 🚀 使用方式

1. 复制完整代码到你的 Activity
2. 确保 ViewModel 已正确实现
3. 配置 FileProvider（见之前的配置）
4. 添加必要的权限
5. 运行测试

## 📚 相关文件

- `ProfileSettingsActivity.kt` - Activity 主文件
- `ProfileUploadAvatarViewModel.kt` - ViewModel
- `file_paths.xml` - FileProvider 配置
- `AndroidManifest.xml` - 权限和 Provider 声明

## ⚠️ 注意事项

1. **Base64 大小限制**：某些服务器可能限制请求体大小，注意压缩图片
2. **内存管理**：处理完 Bitmap 后及时 recycle
3. **线程安全**：确保 UI 操作在主线程
4. **异常处理**：捕获所有可能的异常
5. **用户反馈**：给用户明确的状态提示

## 🎉 完成

现在你已经有了一个完整的头像上传功能！
