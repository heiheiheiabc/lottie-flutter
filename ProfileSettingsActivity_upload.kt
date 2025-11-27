package com.smzdm.android.module.user.ui.activity

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.liuwan.customdatepicker.widget.ZdmWheelView
import com.smzdm.android.module.user.R
import com.smzdm.android.base.mvvm.v.BaseActivity
import com.smzdm.android.base.utils.DateUtils
import com.smzdm.android.common.constant.RouteUrl
import com.smzdm.android.common.core.LoginManager
import com.smzdm.android.common.ktx.arouterInject
import com.smzdm.android.common.util.DeviceUtil
import com.smzdm.android.module.user.helper.CommonUtil
import com.smzdm.android.module.user.helper.ZestFileUtils
import com.smzdm.android.module.user.vm.ProfileSetUserInfoViewModel
import com.smzdm.android.module.user.vm.ProfileUploadAvatarViewModel
import com.smzdm.android.ui.ktx.asResId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar

@Route(path = RouteUrl.User.URL_PROFILE_SETTINGS)
@AndroidEntryPoint
class ProfileSettingsActivity: BaseActivity() {

    companion object {
        private const val REQUEST_PHOTO_ALBUM = 1001
        private const val REQUEST_PHOTO_CAMERA = 1003
        private const val REQUEST_PHOTO_ZOOM = 1005
        
        // 权限请求码
        private const val PERMISSION_REQUEST_CAMERA = 1004
        private const val PERMISSION_REQUEST_READ_MEDIA_IMAGES = 1006
        private const val PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 1002
        
        const val RESULT_CODE_SUCCESS = 100
        private const val IMAGE_UNSPECIFIED = "image/*"
        
        // 图片压缩质量
        private const val COMPRESS_QUALITY = 90
        // 最大图片尺寸（像素）
        private const val MAX_IMAGE_SIZE = 1024
    }

    private var mUri: Uri? = null
    private var isUploadingAvatar = false // 上传状态标志
    
    private val maxNicknameLength = 20
    private val maxPersonalInfoLength = 100

    private lateinit var ivAvatar: ImageView
    private lateinit var nicknameEditText: EditText
    private lateinit var nickNameCountText: TextView
    private lateinit var personalInfoEditText: EditText
    private lateinit var personalInfoCountText: TextView
    private lateinit var currentGenderText: TextView
    private var currentGender: String = ""
    private lateinit var currentBirthdayText: TextView
    private var currentBirthday: String = ""
    private lateinit var femaleOption: TextView
    private lateinit var maleOption: TextView
    private lateinit var cancelOption: TextView
    private lateinit var cameraOption: ImageView
    private lateinit var albumOption: ImageView
    private lateinit var savePersonalAction: TextView

    private val viewModel by viewModels<ProfileSetUserInfoViewModel>()
    private val uploadViewModel by viewModels<ProfileUploadAvatarViewModel>()

    @Autowired(name = "avatar")
    @JvmField
    var avatar: String? = null

    @Autowired(name = "nickname")
    @JvmField
    var nickname: String? = null

    @Autowired(name = "description")
    @JvmField
    var description: String? = null

    @Autowired(name = "birthday")
    @JvmField
    var birthday: String? = null

    @Autowired(name = "gender")
    @JvmField
    var gender: Int = 0

    /**
     * 获取 FileProvider 的 Authority
     */
    private fun getFileProviderAuthority(): String {
        return "${applicationContext.packageName}.fileprovider"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_activity_profile_settings)

        arouterInject()
        initView()
        setupTextCounter()
        setupClickListeners()
        observeSaveState()
        observeUploadState() // 监听上传状态
        setupBackPressHandler()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasDataChanged()) {
                    showSaveConfirmDialog()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * 监听个人信息保存状态
     */
    private fun observeSaveState() {
        lifecycleScope.launch {
            viewModel.saveState.collectLatest { state ->
                when (state) {
                    is ProfileSetUserInfoViewModel.SaveState.Idle -> {}
                    is ProfileSetUserInfoViewModel.SaveState.Loading -> {
                        showLoading(true)
                    }
                    is ProfileSetUserInfoViewModel.SaveState.Success -> {
                        showLoading(false)
                        Toast.makeText(this@ProfileSettingsActivity, state.message, Toast.LENGTH_SHORT).show()
                        LoginManager.instance.notifyUserInfoChanged()
                        savePersonalAction.postDelayed({ finishWithSuccess() }, 500)
                    }
                    is ProfileSetUserInfoViewModel.SaveState.Error -> {
                        showLoading(false)
                        Toast.makeText(this@ProfileSettingsActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
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
                        showUploadLoading(true)
                    }
                    is ProfileUploadAvatarViewModel.UploadState.Success -> {
                        isUploadingAvatar = false
                        showUploadLoading(false)
                        Toast.makeText(
                            this@ProfileSettingsActivity,
                            "头像上传成功",
                            Toast.LENGTH_SHORT
                        ).show()
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

    /**
     * 显示上传加载状态
     */
    private fun showUploadLoading(loading: Boolean) {
        // 可以显示一个进度条或者禁用界面
        ivAvatar.alpha = if (loading) 0.5f else 1.0f
    }

    private fun finishWithSuccess() {
        val resultIntent = Intent().apply {
            putExtra("updated", true)
            putExtra("nickname", nicknameEditText.text.toString())
            putExtra("gender", currentGender)
            putExtra("birthday", currentBirthday)
            putExtra("avatar", avatar)
        }
        setResult(RESULT_CODE_SUCCESS, resultIntent)
        finish()
    }

    private fun showLoading(loading: Boolean) {
        savePersonalAction.isEnabled = !loading
        savePersonalAction.alpha = if (loading) 0.5f else 1.0f
        savePersonalAction.text = if (loading) "saving..." else "save"
    }

    private fun initView(){
        ivAvatar = findViewById(R.id.iv_avatar)
        nicknameEditText = findViewById(R.id.nickNameEditText)
        nickNameCountText = findViewById(R.id.nickNameCountText)
        personalInfoEditText = findViewById(R.id.piEditText)
        personalInfoCountText = findViewById(R.id.piCountText)
        currentGenderText = findViewById(R.id.gender)
        currentBirthdayText = findViewById(R.id.birthday)
        loadUserInfo()
    }

    private fun loadUserInfo(){
        avatar?.let {
            ivAvatar.load(it) {
                placeholder(R.drawable.common_img_default_avatar)
                error(R.drawable.common_img_default_avatar)
            }
        }
        nickname?.let {
            nicknameEditText.setText(it)
            updateNicknameCount(it.length)
        }
        description?.let {
            personalInfoEditText.setText(it)
            updatePersonalInfoCount(it.length)
        }

        birthday?.let {
            if (it.isNotEmpty() && it != "Not Set") {
                val datePart = it.substringBefore(" ", it)
                currentBirthday = datePart
                currentBirthdayText.text = datePart
            } else {
                currentBirthday = ""
                currentBirthdayText.text = "Not Set"
            }
        } ?: run {
            currentBirthday = ""
            currentBirthdayText.text = "Not Set"
        }

        when (gender) {
            1 -> {
                currentGenderText.text = "He"
                currentGender = "He"
            }
            2 -> {
                currentGenderText.text = "She"
                currentGender = "She"
            }
            else -> {
                currentGenderText.text = "Not Set"
                currentGender = "Not Set"
            }
        }
    }

    private fun setupClickListeners(){
        findViewById<LinearLayout>(R.id.genderLayout).setOnClickListener {
            showGenderBottomSheet()
        }

        findViewById<TextView>(R.id.changeAvatar).setOnClickListener {
            showAvatarBottomSheet()
        }

        findViewById<LinearLayout>(R.id.birthdayLayout).setOnClickListener {
            showBirthdayBottomSheet()
        }

        savePersonalAction = findViewById(R.id.user_personal_info_save)
        savePersonalAction.setOnClickListener {
            val genderForApi = when (currentGender) {
                "He" -> "1"
                "She" -> "2"
                else -> "0"
            }
            val nickName = nicknameEditText.text.toString()
            val description = personalInfoEditText.text.toString()

            viewModel.setPersonalInfo(
                gender = genderForApi,
                nickname = nickName,
                description = description,
                birthday = currentBirthday
            )
        }

        val backButton = findViewById<ImageView>(R.id.back)
        backButton.setColorFilter(R.color.color333333_FFFFFF.asResId().color)
        backButton.setOnClickListener {
            handleBackPress()
        }
    }

    private fun hasDataChanged(): Boolean {
        val currentNickname = nicknameEditText.text.toString()
        val currentDescription = personalInfoEditText.text.toString()
        val nicknameChanged = nickname != currentNickname
        val descriptionChanged = description != currentDescription

        val originalGender = when (gender) {
            1 -> "He"
            2 -> "She"
            else -> "Not Set"
        }
        val genderChanged = originalGender != currentGender

        val originalBirthday = if (birthday.isNullOrEmpty() || birthday == "Not Set") {
            ""
        } else {
            birthday!!.substringBefore(" ", birthday!!)
        }
        val birthdayChanged = originalBirthday != currentBirthday

        return nicknameChanged || descriptionChanged || genderChanged || birthdayChanged
    }

    private fun handleBackPress() {
        if (hasDataChanged()) {
            showSaveConfirmDialog()
        } else {
            finish()
        }
    }

    private fun showSaveConfirmDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.user_profile_settings_dialog_title)
            .setMessage(R.string.user_profile_settings_dialog_description)
            .setPositiveButton(R.string.base_action_save, null)
            .setNegativeButton(R.string.base_action_cancel, null)
            .setCancelable(false)
            .create()

        dialog.show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(ContextCompat.getColor(context, R.color.color333333_E0E0E0))
            setOnClickListener {
                saveAndFinish()
                dialog.dismiss()
            }
        }
        
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(ContextCompat.getColor(context, R.color.color333333_E0E0E0))
            setOnClickListener {
                dialog.dismiss()
                finish()
            }
        }
    }

    private fun saveAndFinish() {
        val genderForApi = when (currentGender) {
            "He" -> "1"
            "She" -> "2"
            else -> "0"
        }
        val nickName = nicknameEditText.text.toString()
        val description = personalInfoEditText.text.toString()

        viewModel.setPersonalInfo(
            gender = genderForApi,
            nickname = nickName,
            description = description,
            birthday = currentBirthday
        )
    }

    // ==================== 头像选择相关 ====================

    private fun showAvatarBottomSheet() {
        val avatarBottomSheetDialog = BottomSheetDialog(this)
        val avatarView = layoutInflater.inflate(R.layout.user_activity_bottom_sheet_avatar, null)

        avatarBottomSheetDialog.setOnShowListener {
            val bottomSheet = avatarBottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.setBackgroundResource(R.drawable.user_bottom_sheet_background)
        }

        avatarBottomSheetDialog.setContentView(avatarView)
        avatarBottomSheetDialog.show()

        cameraOption = avatarView.findViewById(R.id.camera)
        albumOption = avatarView.findViewById(R.id.album)
        cancelOption = avatarView.findViewById(R.id.cancel)

        cameraOption.setOnClickListener {
            openCamera()
            avatarBottomSheetDialog.dismiss()
        }

        albumOption.setOnClickListener {
            openAlbum()
            avatarBottomSheetDialog.dismiss()
        }

        cancelOption.setOnClickListener {
            avatarBottomSheetDialog.dismiss()
        }
    }

    /**
     * 打开相机
     */
    private fun openCamera() {
        if (!hasSDCard()) {
            Toast.makeText(this, "SD卡不可用", Toast.LENGTH_SHORT).show()
            return
        }

        val permissions = getCameraPermissions()
        val allGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CAMERA)
        } else {
            startCapture()
        }
    }

    /**
     * 启动相机拍照
     */
    private fun startCapture() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            
            val file = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES), 
                "temp_${System.currentTimeMillis()}.jpg"
            )
            
            file.parentFile?.let {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }
            
            mUri = FileProvider.getUriForFile(
                this,
                getFileProviderAuthority(),
                file
            )
            
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, mUri)
            
            startActivityForResult(intent, REQUEST_PHOTO_CAMERA)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "打开相机失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 打开相册
     */
    private fun openAlbum() {
        val permissions = getReadImagePermissions()
        val allGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            val requestCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PERMISSION_REQUEST_READ_MEDIA_IMAGES
            } else {
                PERMISSION_REQUEST_READ_EXTERNAL_STORAGE
            }
            ActivityCompat.requestPermissions(this, permissions, requestCode)
        } else {
            launchGallery()
        }
    }

    /**
     * 启动相册选择
     */
    private fun launchGallery() {
        try {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, IMAGE_UNSPECIFIED)
            startActivityForResult(intent, REQUEST_PHOTO_ALBUM)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "打开相册失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 获取相机所需权限
     */
    private fun getCameraPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.CAMERA)
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * 获取读取图片所需权限
     */
    private fun getReadImagePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * 检查 SD 卡是否可用
     */
    private fun hasSDCard(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    /**
     * 权限请求结果处理
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CAMERA -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startCapture()
                } else {
                    Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
                }
            }
            PERMISSION_REQUEST_READ_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    launchGallery()
                } else {
                    Toast.makeText(this, "需要存储权限才能选择照片", Toast.LENGTH_SHORT).show()
                }
            }
            PERMISSION_REQUEST_READ_MEDIA_IMAGES -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    launchGallery()
                } else {
                    Toast.makeText(this, "需要访问照片权限才能选择图片", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Activity 结果处理
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) {
            return
        }

        when (requestCode) {
            REQUEST_PHOTO_CAMERA -> {
                handleCameraResult()
            }
            REQUEST_PHOTO_ALBUM -> {
                handleAlbumResult(data)
            }
            REQUEST_PHOTO_ZOOM -> {
                handleCropResult()
            }
        }
    }

    /**
     * 处理拍照结果
     */
    private fun handleCameraResult() {
        try {
            if (mUri == null) {
                val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp.jpg")
                mUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        this,
                        getFileProviderAuthority(),
                        file
                    )
                } else {
                    Uri.fromFile(file)
                }
            }
            
            if (mUri != null) {
                try {
                    contentResolver.openInputStream(mUri!!)?.close()
                    startPhotoZoom(mUri)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "照片文件不存在", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "获取照片失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "处理照片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 处理相册结果
     */
    private fun handleAlbumResult(data: Intent?) {
        val targetUri = data?.data
        if (targetUri != null) {
            if (isImageSizeValid(targetUri)) {
                startPhotoZoom(targetUri)
            } else {
                Toast.makeText(this, "图片大小不能超过5M", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "未选择图片", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 调起相片裁剪
     */
    private fun startPhotoZoom(uri: Uri?) {
        if (uri == null) {
            Toast.makeText(this, "图片地址无效", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent("com.android.camera.action.CROP")
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            intent.setDataAndType(uri, IMAGE_UNSPECIFIED)
            intent.putExtra("crop", "true")
            
            if (isFixCropRound() && DeviceUtil.getDeviceBrand() == "huawei") {
                intent.putExtra("aspectX", 9998)
                intent.putExtra("aspectY", 9999)
            } else {
                intent.putExtra("aspectX", 1)
                intent.putExtra("aspectY", 1)
            }
            
            intent.putExtra("outputX", 240)
            intent.putExtra("outputY", 240)
            intent.putExtra("return-data", false)
            intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString())

            mUri = createCropOutputUri()
            intent.putExtra(MediaStore.EXTRA_OUTPUT, mUri)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                grantUriPermission(this, mUri!!, intent)
            }

            startActivityForResult(intent, REQUEST_PHOTO_ZOOM)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "启动裁剪失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 创建裁剪输出文件的 Uri
     */
    private fun createCropOutputUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "smzdm_avatar_${System.currentTimeMillis()}")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        } else {
            val file = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "temp_crop_${System.currentTimeMillis()}.jpg"
            )
            
            file.parentFile?.let {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }
            
            FileProvider.getUriForFile(
                this,
                getFileProviderAuthority(),
                file
            )
        }
    }

    /**
     * 授予输出文件的读写权限
     */
    private fun grantUriPermission(context: Context, fileUri: Uri, intent: Intent) {
        try {
            val resInfoList: List<ResolveInfo> = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(
                    packageName,
                    fileUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 处理裁剪结果（核心方法）
     */
    private fun handleCropResult() {
        try {
            if (mUri == null) {
                Toast.makeText(this, "裁剪失败，无法获取图片", Toast.LENGTH_SHORT).show()
                return
            }

            // 从 Uri 读取 Bitmap
            val originalBitmap = MediaStore.Images.Media.getBitmap(contentResolver, mUri)
            if (originalBitmap == null) {
                Toast.makeText(this, "裁剪失败，无法获取图片", Toast.LENGTH_SHORT).show()
                return
            }

            // 转换为圆形
            val circleBitmap = getCircleBitmap(originalBitmap)
            
            // 显示到界面
            ivAvatar.setImageBitmap(circleBitmap)

            // 在后台线程处理图片保存和上传
            lifecycleScope.launch {
                try {
                    // 1. 压缩并保存图片到文件
                    val compressedBitmap = withContext(Dispatchers.IO) {
                        compressBitmap(circleBitmap)
                    }
                    
                    val savedFile = withContext(Dispatchers.IO) {
                        saveBitmapToFile(compressedBitmap)
                    }

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

                    // 2. 转换为 Base64 并上传
                    val base64String = withContext(Dispatchers.IO) {
                        CommonUtil.photoTo64BitString(compressedBitmap)
                    }

                    // 3. 调用上传接口
                    withContext(Dispatchers.Main) {
                        uploadAvatar(base64String, savedFile)
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

            // 保存文件
            outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, outputStream)
            outputStream.flush()

            android.util.Log.d("ProfileSettings", "图片已保存: ${file.absolutePath}, 大小: ${file.length() / 1024}KB")

            return file

        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("ProfileSettings", "保存图片失败", e)
            return null
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 上传头像
     * @param base64String Base64 编码的图片字符串
     * @param imageFile 图片文件（可选，用于日志或其他用途）
     */
    private fun uploadAvatar(base64String: String, imageFile: File? = null) {
        if (isUploadingAvatar) {
            Toast.makeText(this, "正在上传中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            android.util.Log.d("ProfileSettings", "开始上传头像")
            imageFile?.let {
                android.util.Log.d("ProfileSettings", "图片文件: ${it.absolutePath}, 大小: ${it.length() / 1024}KB")
            }
            android.util.Log.d("ProfileSettings", "Base64 长度: ${base64String.length}")

            // 调用 ViewModel 上传
            uploadViewModel.uploadAvatar(base64String)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "上传头像失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 检查图片大小是否在5M以内
     */
    private fun isImageSizeValid(uri: Uri): Boolean {
        return try {
            val fileSize = getFileSizeFromUri(uri)
            val maxSize = 5 * 1024 * 1024 // 5MB
            fileSize <= maxSize
        } catch (e: Exception) {
            e.printStackTrace()
            true
        }
    }

    /**
     * 获取 Uri 对应文件的大小
     */
    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
                parcelFileDescriptor.statSize
            } ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    /**
     * 将 Bitmap 转换为圆形
     */
    private fun getCircleBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)

        val radius = bitmap.width.coerceAtMost(bitmap.height) / 2f
        canvas.drawCircle(bitmap.width / 2f, bitmap.height / 2f, radius, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }

    /**
     * 是否修复裁剪圆形问题
     */
    private fun isFixCropRound(): Boolean {
        return true
    }

    // ==================== 其他辅助方法 ====================

    private fun setupTextCounter() {
        nicknameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateNicknameCount(s?.length ?: 0)
            }
        })

        personalInfoEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePersonalInfoCount(s?.length ?: 0)
            }
        })

        updateNicknameCount(nicknameEditText.text.length)
        updatePersonalInfoCount(personalInfoEditText.text.length)
    }

    private fun updateNicknameCount(currentLength: Int) {
        nickNameCountText.text = "$currentLength"
        if (currentLength > maxNicknameLength) {
            nickNameCountText.setTextColor(ContextCompat.getColor(this, R.color.colorE62828))
        } else {
            nickNameCountText.setTextColor(ContextCompat.getColor(this, R.color.color333333_E0E0E0))
        }
    }

    private fun updatePersonalInfoCount(currentLength: Int) {
        personalInfoCountText.text = "$currentLength"
        if (currentLength > maxPersonalInfoLength) {
            personalInfoCountText.setTextColor(ContextCompat.getColor(this, R.color.colorE62828))
        } else {
            personalInfoCountText.setTextColor(ContextCompat.getColor(this, R.color.color333333_E0E0E0))
        }
    }

    // 生日和性别选择的方法省略...
    // （保持原有实现不变）
}
