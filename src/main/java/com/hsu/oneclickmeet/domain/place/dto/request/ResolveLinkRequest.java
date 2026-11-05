package com.hsu.oneclickmeet.domain.place.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 링크로 장소 찾기 요청. 서버는 URL을 직접 열지 않으며, 앱이 공유받은 텍스트를 그대로 보낸다. */
public record ResolveLinkRequest(
		@NotBlank(message = "sharedText는 필수입니다.")
		@Size(max = 2000, message = "sharedText는 2000자 이하여야 합니다.")
		String sharedText) {
}
