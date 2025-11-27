package com.smzdm.android.module.user.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smzdm.android.module.user.repository.CommonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileUploadAvatarViewModel @Inject constructor(
    private val commonRepository: CommonRepository
) : ViewModel() {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    /**
     * 上传头像
     * 
     * @param imageFile 图片文件
     * @param bucketName 存储桶名称（可选）
     * @param customPath 自定义路径（可选）
     * @param customName 自定义名称（可选）
     */
    fun uploadAvatar(
        imageFile: File,
        bucketName: String? = null,
        customPath: String? = null,
        customName: String? = null
    ) {
        viewModelScope.launch {
            try {
                // 检查文件是否存在
                if (!imageFile.exists()) {
                    android.util.Log.e("UploadViewModel", "文件不存在: ${imageFile.absolutePath}")
                    _uploadState.value = UploadState.Error("文件不存在")
                    return@launch
                }

                android.util.Log.d("UploadViewModel", "=== 开始上传头像 ===")
                android.util.Log.d("UploadViewModel", "文件路径: ${imageFile.absolutePath}")
                android.util.Log.d("UploadViewModel", "文件大小: ${imageFile.length() / 1024}KB")
                android.util.Log.d("UploadViewModel", "bucket_name: $bucketName")
                android.util.Log.d("UploadViewModel", "custom_path: $customPath")
                android.util.Log.d("UploadViewModel", "custom_name: $customName")

                // 设置为加载状态
                _uploadState.value = UploadState.Loading

                // 调用上传接口
                commonRepository.userUploadAvatar(
                    image = imageFile,
                    bucketName = bucketName,
                    customPath = customPath,
                    customName = customName
                ).catch { e ->
                    // 捕获 Flow 异常
                    android.util.Log.e("UploadViewModel", "上传异常", e)
                    _uploadState.value = UploadState.Error(e.message ?: "上传失败")
                }.collect { result ->
                    if (result.isSuccess) {
                        // 上传成功
                        val data = result.getOrNull()
                        android.util.Log.d("UploadViewModel", "上传成功，返回数据: $data")
                        
                        // 获取头像 URL（根据你的实际返回结构调整）
                        val avatarUrl = data?.data?.avatarUrl ?: data?.data?.url ?: ""
                        
                        if (avatarUrl.isNotEmpty()) {
                            _uploadState.value = UploadState.Success(avatarUrl)
                        } else {
                            android.util.Log.w("UploadViewModel", "上传成功但未返回头像 URL")
                            _uploadState.value = UploadState.Success("上传成功")
                        }
                        
                    } else {
                        // 上传失败
                        val error = result.exceptionOrNull()
                        android.util.Log.e("UploadViewModel", "上传失败", error)
                        _uploadState.value = UploadState.Error(error?.message ?: "上传失败")
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("UploadViewModel", "上传发生异常", e)
                e.printStackTrace()
                _uploadState.value = UploadState.Error(e.message ?: "未知错误")
            }
        }
    }

    /**
     * 重置上传状态
     */
    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }

    /**
     * 上传状态
     */
    sealed class UploadState {
        object Idle : UploadState()
        object Loading : UploadState()
        data class Success(val avatarUrl: String) : UploadState()
        data class Error(val message: String) : UploadState()
    }
}
