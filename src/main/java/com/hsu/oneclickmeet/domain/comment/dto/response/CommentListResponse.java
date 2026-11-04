package com.hsu.oneclickmeet.domain.comment.dto.response;

import java.util.List;

public record CommentListResponse(List<CommentResponse> items, String nextCursor, boolean hasNext) {
}
