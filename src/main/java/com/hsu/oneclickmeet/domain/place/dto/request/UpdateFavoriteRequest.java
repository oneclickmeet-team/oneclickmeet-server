package com.hsu.oneclickmeet.domain.place.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateFavoriteRequest(@NotNull Boolean favorite) {
}
