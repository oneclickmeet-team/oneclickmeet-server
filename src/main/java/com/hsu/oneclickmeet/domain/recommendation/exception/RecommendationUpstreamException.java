package com.hsu.oneclickmeet.domain.recommendation.exception;

import com.hsu.oneclickmeet.global.exception.BusinessException;

/** AI 서버의 코스 추천 호출 실패. 응답에는 원인을 구분하지 않고 {@link RecommendationErrorCode#UPSTREAM_FAILURE}만 노출한다. */
public class RecommendationUpstreamException extends BusinessException {

	public RecommendationUpstreamException(Throwable cause) {
		super(RecommendationErrorCode.UPSTREAM_FAILURE);
		if (cause != null) {
			initCause(cause);
		}
	}
}
