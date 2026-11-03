package com.hsu.oneclickmeet.domain.place.client;

import java.util.List;

public record PlaceSearchResult(List<PlaceCandidate> candidates, int page, boolean hasNext) {
}
