package com.hsu.oneclickmeet.domain.memo.dto.response;

import java.util.List;

public record MemoListResponse(List<MemoResponse> items, String nextCursor, boolean hasNext) {
}
