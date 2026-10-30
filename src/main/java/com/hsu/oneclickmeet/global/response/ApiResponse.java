package com.hsu.oneclickmeet.global.response;

import com.hsu.oneclickmeet.global.exception.ErrorResponse;

public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> failure(ErrorResponse error) {
		return new ApiResponse<>(false, null, error);
	}
}
