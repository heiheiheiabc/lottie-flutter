package com.smzdm.android.module.user.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 头像上传 ViewModel 示例
 */
@HiltViewModel
class ProfileUploadAvatarViewModel @Inject constructor(
    // private val repository: UserRepository // 注入你的 Repository
) : ViewModel() {

    // 上传状态
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    /**
     * 上传头像
     * @param base64String Base64 编码的图片字符串
     */
    fun uploadAvatar(base64String: String) {
        viewModelScope.launch {
            try {
                _uploadState.value = UploadState.Loading

                // 调用上传接口
                // val response = repository.uploadAvatar(base64String)
                
                // 模拟网络请求（实际使用时替换为真实接口调用）
                kotlinx.coroutines.delay(2000) // 模拟网络延迟
                
                // 假设上传成功返回头像 URL
                val avatarUrl = "https://example.com/avatar/xxx.jpg"
                
                _uploadState.value = UploadState.Success(avatarUrl)

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

/**
 * 实际使用的 Repository 示例
 */
/*
class UserRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun uploadAvatar(base64String: String): AvatarResponse {
        return apiService.uploadAvatar(
            UploadAvatarRequest(avatar = base64String)
        )
    }
}

data class UploadAvatarRequest(
    val avatar: String // Base64 字符串
)

data class AvatarResponse(
    val error_code: String,
    val error_msg: String,
    val data: AvatarData?
)

data class AvatarData(
    val url: String // 上传成功后的头像 URL
)
*/
