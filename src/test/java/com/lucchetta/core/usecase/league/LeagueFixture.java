package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.domain.league.LeagueVisibility;
import com.lucchetta.core.support.DirectTransactionRunner;
import com.lucchetta.core.support.InMemoryLeagues;
import com.lucchetta.core.support.InMemoryPlayers;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** Leagues, players and the league use cases wired with in-memory fakes. */
class LeagueFixture {

    static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    final InMemoryPlayers players = new InMemoryPlayers();
    final InMemoryLeagues leagues = new InMemoryLeagues();
    final DirectTransactionRunner transactions = new DirectTransactionRunner();
    final LeagueAccess access = new LeagueAccess(new GetOrCreateCurrentPlayer(players, CLOCK), leagues, leagues);

    final CreateLeague createLeague = new CreateLeague(access, leagues, leagues, transactions, CLOCK);
    final UpdateLeague updateLeague = new UpdateLeague(access, leagues, transactions, CLOCK);
    final LeagueArchiving archiving = new LeagueArchiving(access, leagues, transactions, CLOCK);
    final QueryLeagues queryLeagues = new QueryLeagues(access, leagues, leagues);
    final LeagueMembership membership = new LeagueMembership(access, leagues, players, transactions, CLOCK);

    Player player(String name) {
        return players.add(name);
    }

    static AuthenticatedUser user(Player player) {
        return new AuthenticatedUser(player.identity(), null, player.displayName().value(), false);
    }

    static AuthenticatedUser systemAdmin(Player player) {
        return new AuthenticatedUser(player.identity(), null, player.displayName().value(), true);
    }

    /** A league whose administrator is {@code admin}; the other players are members. */
    League league(String name, LeagueVisibility visibility, Player admin, Player... members) {
        League league = new League(UUID.randomUUID(), name, null, visibility, null, admin.id(), NOW, NOW, null,
                null, 0);
        leagues.add(league, new LeagueMember(league.id(), admin.id(), LeagueRole.ADMIN, NOW));
        for (Player member : members) {
            leagues.insert(new LeagueMember(league.id(), member.id(), LeagueRole.MEMBER, NOW));
        }
        return league;
    }
}
