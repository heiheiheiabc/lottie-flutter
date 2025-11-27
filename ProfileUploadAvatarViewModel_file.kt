package com.smzdm.android.module.user.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smzdm.android.module.user.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 头像上传 ViewModel - 文件上传版本
 */
@HiltViewModel
class ProfileUploadAvatarViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    // 上传状态
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    /**
     * 上传头像文件 - 方式1：只传文件
     * 
     * @param file 头像文件
     */
    fun uploadAvatar(file: File) {
        viewModelScope.launch {
            try {
                // 检查文件是否存在
                if (!file.exists()) {
                    _uploadState.value = UploadState.Error("文件不存在")
                    return@launch
                }

                // 开始上传
                _uploadState.value = UploadState.Loading

                // 调用 Repository
                val result = userRepository.uploadAvatar(file)

                // 处理结果
                if (result.isSuccess) {
                    val avatarUrl = result.getOrNull()!!
                    _uploadState.value = UploadState.Success(avatarUrl)
                    
                    android.util.Log.d("UploadViewModel", "上传成功: $avatarUrl")
                } else {
                    val error = result.exceptionOrNull()
                    _uploadState.value = UploadState.Error(error?.message ?: "上传失败")
                    
                    android.util.Log.e("UploadViewModel", "上传失败", error)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uploadState.value = UploadState.Error(e.message ?: "上传失败")
                
                android.util.Log.e("UploadViewModel", "上传异常", e)
            }
        }
    }

    /**
     * 上传头像文件 - 方式2：带额外参数
     * 
     * @param file 头像文件
     * @param userId 用户ID
     * @param token Token
     */
    fun uploadAvatarWithParams(file: File, userId: String, token: String) {
        viewModelScope.launch {
            try {
                if (!file.exists()) {
                    _uploadState.value = UploadState.Error("文件不存在")
                    return@launch
                }

                _uploadState.value = UploadState.Loading

                val result = userRepository.uploadAvatarWithParams(file, userId, token)

                if (result.isSuccess) {
                    _uploadState.value = UploadState.Success(result.getOrNull()!!)
                } else {
                    _uploadState.value = UploadState.Error(
                        result.exceptionOrNull()?.message ?: "上传失败"
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uploadState.value = UploadState.Error(e.message ?: "上传失败")
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
     * 上传状态密封类
     */
    sealed class UploadState {
        object Idle : UploadState()
        object Loading : UploadState()
        data class Success(val avatarUrl: String) : UploadState()
        data class Error(val message: String) : UploadState()
    }
}
