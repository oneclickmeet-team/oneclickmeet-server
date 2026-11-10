package com.hsu.oneclickmeet.domain.place.client;

import java.util.List;

/**
 * 공유 텍스트나 이미지에서 장소 후보를 찾는 외부 연동(AI 서버).
 * AI 서버가 장소 이름 추출부터 카카오 검색·매칭까지 마친 후보를 돌려준다.
 * 실패하면 {@link com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException}을 던진다.
 */
public interface PlaceRecognizer {

	Recognition recognizeText(String sharedText);

	/** 원본 캡처 한 장과 추가 캡처(없으면 빈 목록)에서 후보를 찾는다. */
	Recognition recognizeImage(RecognitionImage image, List<RecognitionImage> extraImages);
}
