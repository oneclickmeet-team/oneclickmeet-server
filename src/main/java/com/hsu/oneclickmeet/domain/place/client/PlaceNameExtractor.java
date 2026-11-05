package com.hsu.oneclickmeet.domain.place.client;

import java.util.List;

/**
 * 공유 텍스트나 이미지에서 장소 이름 후보를 뽑아내는 외부 연동(AI 서버).
 * 실패하면 {@link com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException}을 던진다.
 */
public interface PlaceNameExtractor {

	List<String> fromText(String sharedText);

	List<String> fromImage(byte[] image, String contentType);
}
