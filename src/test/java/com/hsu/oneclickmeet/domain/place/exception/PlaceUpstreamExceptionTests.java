package com.hsu.oneclickmeet.domain.place.exception;

import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException.Reason;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceUpstreamExceptionTests {

	@Test
	@DisplayName("업무 예외로서 502와 PLACE-0502를 가진다")
	void isBusinessExceptionWithBadGatewayCode() {
		PlaceUpstreamException exception = new PlaceUpstreamException(Reason.RATE_LIMIT, "한도 초과");

		assertThat(exception).isInstanceOf(BusinessException.class);
		assertThat(exception.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertThat(exception.getErrorCode().getCode()).isEqualTo("PLACE-0502");
	}

	@Test
	@DisplayName("원인과 상세는 따로 보관하고 메시지에는 사용자 안내만 담는다")
	void keepsReasonAndDetailSeparatelyFromClientMessage() {
		PlaceUpstreamException exception = new PlaceUpstreamException(Reason.AUTH, "인증 실패 상세");

		assertThat(exception.getReason()).isEqualTo(Reason.AUTH);
		assertThat(exception.getDetail()).isEqualTo("인증 실패 상세");
		assertThat(exception.getMessage()).doesNotContain("인증 실패 상세");
	}

	@Test
	@DisplayName("원인 예외를 보관한다")
	void keepsCause() {
		IllegalStateException cause = new IllegalStateException("원인");

		PlaceUpstreamException exception = new PlaceUpstreamException(Reason.NETWORK, "연결 실패", cause);

		assertThat(exception.getCause()).isSameAs(cause);
	}
}
