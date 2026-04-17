package com.example.nossasaudeapp.data.remote

import com.example.nossasaudeapp.data.remote.dto.ApiErrorDto

class ApiException(
    val httpStatus: Int,
    val errorCode: String?,
    val errorMessage: String?,
    val payload: ApiErrorDto? = null,
    cause: Throwable? = null,
) : java.io.IOException("[$httpStatus] ${errorCode ?: "error"}: ${errorMessage ?: ""}", cause) {
    val isNotFound: Boolean get() = httpStatus == 404
    val isValidation: Boolean get() = httpStatus == 422
}
