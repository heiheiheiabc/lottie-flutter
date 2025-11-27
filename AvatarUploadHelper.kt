package com.smzdm.android.module.user.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

/**
 * 头像上传辅助类
 * 负责 Bitmap 的处理和文件保存
 */
object AvatarUploadHelper {

    // 图片压缩质量 (0-100)
    private const val COMPRESS_QUALITY = 90
    
    // 最大图片尺寸（像素）
    private const val MAX_IMAGE_SIZE = 1024

    /**
     * 处理裁剪后的 Bitmap：转圆形 → 压缩 → 保存到文件
     * @param context Context
     * @param bitmap 裁剪后的 Bitmap
     * @return 保存的文件，失败返回 null
     */
    fun processCroppedBitmap(context: Context, bitmap: Bitmap): File? {
        try {
            // 1. 转换为圆形
            val circleBitmap = getCircleBitmap(bitmap)
            
            // 2. 压缩图片
            val compressedBitmap = compressBitmap(circleBitmap)
            
            // 3. 保存到文件
            return saveBitmapToFile(context, compressedBitmap)
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 将 Bitmap 转换为圆形
     */
    fun getCircleBitmap(bitmap: Bitmap): Bitmap {
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
     * 压缩 Bitmap
     * 如果图片尺寸过大，则缩小到指定大小
     */
    fun compressBitmap(bitmap: Bitmap, maxSize: Int = MAX_IMAGE_SIZE): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 如果图片尺寸小于最大尺寸，直接返回
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        // 计算缩放比例
        val scale = if (width > height) {
            maxSize.toFloat() / width
        } else {
            maxSize.toFloat() / height
        }

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * 将 Bitmap 保存到文件
     * @param context Context
     * @param bitmap 要保存的 Bitmap
     * @param quality 压缩质量 (0-100)，默认 90
     * @return 保存的文件，失败返回 null
     */
    fun saveBitmapToFile(
        context: Context, 
        bitmap: Bitmap,
        quality: Int = COMPRESS_QUALITY
    ): File? {
        var outputStream: FileOutputStream? = null
        try {
            // 创建文件（保存在应用专属目录）
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
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
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.flush()

            android.util.Log.d(
                "AvatarUploadHelper", 
                "图片已保存: ${file.absolutePath}\n" +
                "文件大小: ${file.length() / 1024}KB\n" +
                "图片尺寸: ${bitmap.width} x ${bitmap.height}"
            )

            return file

        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("AvatarUploadHelper", "保存图片失败", e)
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
     * 删除临时文件
     */
    fun deleteTempFile(file: File?) {
        try {
            if (file != null && file.exists()) {
                file.delete()
                android.util.Log.d("AvatarUploadHelper", "临时文件已删除: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
