package com.lucchetta.core.usecase.player;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.usecase.league.LeagueAccess;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class QueryPlayers {

    private final LeagueAccess leagueAccess;
    private final LeagueMemberRepository memberRepository;
    private final PlayerRepository playerRepository;

    public QueryPlayers(LeagueAccess leagueAccess, LeagueMemberRepository memberRepository,
                        PlayerRepository playerRepository) {
        this.leagueAccess = leagueAccess;
        this.memberRepository = memberRepository;
        this.playerRepository = playerRepository;
    }

    /**
     * Members of the league, by display name: the players that can be picked for a match. Users
     * who are not members are not visible inside the league (decision D-020).
     */
    public List<Player> list(AuthenticatedUser user, UUID leagueId) {
        leagueAccess.requireMember(user, leagueId);
        return playerRepository.findAllByIds(memberRepository.findByLeague(leagueId).stream()
                        .map(LeagueMember::playerId)
                        .toList())
                .stream()
                .sorted(Comparator.comparing(p -> p.displayName().value(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
