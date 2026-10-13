package com.lucchetta.entrypoint.rest.me;

import jakarta.validation.constraints.NotNull;

public record UpdateMeRequest(@NotNull String displayName) {
}
