package com.lucchetta.core.support;

import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.domain.league.LeagueVisibility;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;

import java.time.Instant;
import java.util.UUID;

/**
 * The league that seasons and matches of the use case tests belong to, in memory. Members join
 * when the player registered, so "registered before the season ended" keeps its meaning.
 */
public class TestLeague {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final Instant CREATED = Instant.parse("2025-01-01T12:00:00Z");

    public final InMemoryLeagues leagues = new InMemoryLeagues();
    public final InMemoryJoinRequests joinRequests = new InMemoryJoinRequests();

    public TestLeague() {
        leagues.add(new League(ID, "Liga de Teste", null, LeagueVisibility.PUBLIC, null, null, CREATED, CREATED, null,
                null, 0));
    }

    /** Adds the players as members, joined when they registered. */
    public TestLeague members(Player... players) {
        for (Player player : players) {
            leagues.insert(new LeagueMember(ID, player.id(), LeagueRole.MEMBER, player.createdAt()));
        }
        return this;
    }

    /** Adds the player as a league administrator, joined when they registered. */
    public TestLeague administrator(Player player) {
        leagues.insert(new LeagueMember(ID, player.id(), LeagueRole.ADMIN, player.createdAt()));
        return this;
    }

    public LeagueAccess access(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer) {
        return new LeagueAccess(getOrCreateCurrentPlayer, leagues, leagues, joinRequests);
    }
}
