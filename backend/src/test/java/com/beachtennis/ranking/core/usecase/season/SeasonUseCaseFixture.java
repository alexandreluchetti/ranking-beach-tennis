package com.beachtennis.ranking.core.usecase.season;

import com.beachtennis.ranking.core.domain.AuthIdentity;
import com.beachtennis.ranking.core.domain.AuthenticatedUser;
import com.beachtennis.ranking.core.domain.DisplayName;
import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.domain.Season;
import com.beachtennis.ranking.core.support.InMemorySeasons;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/** Shared fixtures: "today" is 2026-10-12 in Brasília. */
final class SeasonUseCaseFixture {

    static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    static final AuthenticatedUser ADMIN =
            new AuthenticatedUser(new AuthIdentity("firebase", "admin-uid"), null, "Admin", true);
    static final AuthenticatedUser REGULAR_USER =
            new AuthenticatedUser(new AuthIdentity("firebase", "user-uid"), null, "Ana", false);
    static final Player ADMIN_PLAYER =
            new Player(UUID.randomUUID(), ADMIN.identity(), null, new DisplayName("Admin"), NOW, NOW);

    private SeasonUseCaseFixture() {
    }

    static Season storedSeason(InMemorySeasons seasons, String name, LocalDate start, LocalDate end) {
        Instant created = Instant.parse("2026-01-01T12:00:00Z");
        return seasons.add(new Season(UUID.randomUUID(), name, start, end, ADMIN_PLAYER.id(), created, created));
    }
}
