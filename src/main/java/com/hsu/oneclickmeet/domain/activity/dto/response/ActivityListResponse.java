package com.hsu.oneclickmeet.domain.activity.dto.response;

import java.util.List;

public record ActivityListResponse(List<ActivityResponse> items, String nextCursor, boolean hasNext) {
}
