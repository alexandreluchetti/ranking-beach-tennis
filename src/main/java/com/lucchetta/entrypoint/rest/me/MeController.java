package com.lucchetta.entrypoint.rest.me;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;
import com.lucchetta.core.usecase.player.UpdateDisplayName;
import com.lucchetta.entrypoint.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final CurrentUserProvider currentUserProvider;
    private final GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;
    private final UpdateDisplayName updateDisplayName;

    public MeController(CurrentUserProvider currentUserProvider,
                        GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                        UpdateDisplayName updateDisplayName) {
        this.currentUserProvider = currentUserProvider;
        this.getOrCreateCurrentPlayer = getOrCreateCurrentPlayer;
        this.updateDisplayName = updateDisplayName;
    }

    @GetMapping
    @Operation(summary = "Returns the caller's profile, creating it on first access")
    public MeResponse get() {
        AuthenticatedUser user = currentUserProvider.get();
        return MeResponse.from(getOrCreateCurrentPlayer.execute(user), user.admin());
    }

    @PatchMapping
    @Operation(summary = "Updates the caller's display name")
    public MeResponse update(@Valid @RequestBody UpdateMeRequest request) {
        AuthenticatedUser user = currentUserProvider.get();
        return MeResponse.from(updateDisplayName.execute(user, request.displayName()), user.admin());
    }
}
