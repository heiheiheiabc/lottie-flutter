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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
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
    }

    private var mUri: Uri? = null
    
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
    private val uploadviewModel by viewModels<ProfileUploadAvatarViewModel>()

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
     * 格式：包名.fileprovider
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
        setupBackPressHandler()
    }

    // ... [其他方法保持不变，只修改涉及 FileProvider 的部分] ...

    /**
     * 启动相机拍照
     */
    private fun startCapture() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            
            // 创建临时文件
            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp_${System.currentTimeMillis()}.jpg")
            
            // 如果父目录不存在，创建它
            file.parentFile?.let {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }
            
            // 使用 FileProvider 获取 Uri
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
     * 处理拍照结果
     */
    private fun handleCameraResult() {
        try {
            if (mUri == null) {
                // 重新构造 Uri（兼容处理）
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
            
            // 检查文件是否存在
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
     * 创建裁剪输出文件的 Uri
     */
    private fun createCropOutputUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 通过 MediaStore API 插入文件
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "smzdm_avatar_${System.currentTimeMillis()}")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        } else {
            // Android 10 及以下使用 FileProvider
            val file = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "temp_crop_${System.currentTimeMillis()}.jpg"
            )
            
            // 确保父目录存在
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
            
            // aspectX aspectY 是宽高的比例
            if (isFixCropRound() && DeviceUtil.getDeviceBrand() == "huawei") {
                intent.putExtra("aspectX", 9998)
                intent.putExtra("aspectY", 9999)
            } else {
                intent.putExtra("aspectX", 1)
                intent.putExtra("aspectY", 1)
            }
            
            // outputX outputY 是裁剪图片宽高
            intent.putExtra("outputX", 240)
            intent.putExtra("outputY", 240)
            intent.putExtra("return-data", false)
            intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString())

            // 创建裁剪输出 Uri
            mUri = createCropOutputUri()
            intent.putExtra(MediaStore.EXTRA_OUTPUT, mUri)

            // 授予裁剪应用的 Uri 权限
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
     * 授予输出文件的读写权限 (参考 Java 代码)
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
     * 处理裁剪结果
     */
    private fun handleCropResult() {
        try {
            if (mUri != null) {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, mUri)
                if (bitmap != null) {
                    val circleBitmap = getCircleBitmap(bitmap)
                    ivAvatar.setImageBitmap(circleBitmap)

                    // 上传头像
                    uploadAvatar(circleBitmap)
                } else {
                    Toast.makeText(this, "裁剪失败，无法获取图片", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "裁剪失败，无法获取图片", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "处理裁剪图片时出错: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 上传头像
     */
    private fun uploadAvatar(bitmap: Bitmap) {
        try {
            val base64String = CommonUtil.photoTo64BitString(bitmap)
            uploadviewModel.uploadAvatar(base64String)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "上传头像失败", Toast.LENGTH_SHORT).show()
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
     * 是否修复裁剪圆形问题（华为设备特殊处理）
     */
    private fun isFixCropRound(): Boolean {
        return true
    }

    // ... [其他生日、性别、文本计数等方法保持不变] ...
}
