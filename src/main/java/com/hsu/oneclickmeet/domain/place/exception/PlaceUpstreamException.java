package com.hsu.oneclickmeet.domain.place.exception;

/**
 * 외부 장소 검색 호출 실패. 원인 구분은 서버 로그와 지표에만 쓰고 응답에는 노출하지 않는다.
 * TODO: 전역 에러 핸들러(#13)의 기반 예외가 정해지면 그 예외를 상속한다.
 */
public class PlaceUpstreamException extends RuntimeException {

	public enum Reason {
		AUTH,
		RATE_LIMIT,
		REQUEST_REJECTED,
		SERVER_ERROR,
		NETWORK,
		BAD_RESPONSE
	}

	private final Reason reason;

	public PlaceUpstreamException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public PlaceUpstreamException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}
}
