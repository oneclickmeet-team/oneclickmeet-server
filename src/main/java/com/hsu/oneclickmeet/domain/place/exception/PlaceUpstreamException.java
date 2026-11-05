package com.hsu.oneclickmeet.domain.place.exception;

import com.hsu.oneclickmeet.global.exception.BusinessException;

/**
 * 외부 장소 검색 호출 실패. 응답에는 원인을 구분하지 않고 {@link PlaceErrorCode#UPSTREAM_FAILURE}만 노출한다.
 * 원인({@link Reason})과 상세 내용은 서버 로그와 지표에서 구분하는 용도다.
 */
public class PlaceUpstreamException extends BusinessException {

	public enum Reason {
		AUTH,
		RATE_LIMIT,
		REQUEST_REJECTED,
		SERVER_ERROR,
		NETWORK,
		BAD_RESPONSE
	}

	private final Reason reason;
	private final String detail;

	public PlaceUpstreamException(Reason reason, String detail) {
		super(PlaceErrorCode.UPSTREAM_FAILURE);
		this.reason = reason;
		this.detail = detail;
	}

	public PlaceUpstreamException(Reason reason, String detail, Throwable cause) {
		this(reason, detail);
		initCause(cause);
	}

	public Reason getReason() {
		return reason;
	}

	public String getDetail() {
		return detail;
	}
}
