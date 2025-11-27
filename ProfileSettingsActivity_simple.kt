package com.smzdm.android.module.user.ui.activity

// ... 其他 import 保持不变

@Route(path = RouteUrl.User.URL_PROFILE_SETTINGS)
@AndroidEntryPoint
class ProfileSettingsActivity: BaseActivity() {

    // ... 其他代码保持不变

    private val uploadViewModel by viewModels<ProfileUploadAvatarViewModel>()
    private var isUploadingAvatar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_activity_profile_settings)

        arouterInject()
        initView()
        setupTextCounter()
        setupClickListeners()
        observeSaveState()
        observeUploadState()  // 监听上传状态
        setupBackPressHandler()
    }

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
                        // 显示加载中
                        ivAvatar.alpha = 0.5f
                    }
                    is ProfileUploadAvatarViewModel.UploadState.Success -> {
                        isUploadingAvatar = false
                        ivAvatar.alpha = 1.0f
                        Toast.makeText(this@ProfileSettingsActivity, "头像上传成功", Toast.LENGTH_SHORT).show()
                        
                        // 更新头像 URL
                        avatar = state.avatarUrl
                        LoginManager.instance.notifyUserInfoChanged()
                    }
                    is ProfileUploadAvatarViewModel.UploadState.Error -> {
                        isUploadingAvatar = false
                        ivAvatar.alpha = 1.0f
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

    /**
     * 处理裁剪结果 - 简化版（只保存文件，然后上传）
     */
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

            // 2. 转换为圆形并显示
            val circleBitmap = AvatarUploadHelper.getCircleBitmap(originalBitmap)
            ivAvatar.setImageBitmap(circleBitmap)

            // 3. 在后台线程处理文件保存和上传
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 处理 Bitmap：转圆形 → 压缩 → 保存到文件
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

                    // 4. 切换到主线程上传文件
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
            Toast.makeText(this, "处理裁剪图片时出错: ${e.message}", Toast.LENGTH_SHORT).show()
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

        android.util.Log.d("ProfileSettings", "=== 开始上传头像文件 ===")
        android.util.Log.d("ProfileSettings", "文件路径: ${file.absolutePath}")
        android.util.Log.d("ProfileSettings", "文件大小: ${file.length() / 1024}KB")
        android.util.Log.d("ProfileSettings", "文件存在: ${file.exists()}")

        // 调用 ViewModel 上传文件（Multipart 方式）
        uploadViewModel.uploadAvatar(file)
    }

    // ... 其他方法保持不变
}
