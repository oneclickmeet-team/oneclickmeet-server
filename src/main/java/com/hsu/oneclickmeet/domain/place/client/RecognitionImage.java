package com.hsu.oneclickmeet.domain.place.client;

/** AI 서버로 보낼 이미지 한 장(파일 내용과 선언된 형식). */
public record RecognitionImage(byte[] bytes, String contentType) {
}
