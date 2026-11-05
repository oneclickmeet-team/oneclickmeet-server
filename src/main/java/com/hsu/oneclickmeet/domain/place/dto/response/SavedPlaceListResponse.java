package com.hsu.oneclickmeet.domain.place.dto.response;

import java.util.List;

public record SavedPlaceListResponse(List<SavedPlaceResponse> items, String nextCursor, boolean hasNext) {
}
