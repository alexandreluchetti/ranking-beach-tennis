package com.beachtennis.ranking.entrypoint.rest.me;

import jakarta.validation.constraints.NotNull;

public record UpdateMeRequest(@NotNull String displayName) {
}
