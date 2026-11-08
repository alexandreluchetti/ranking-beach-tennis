package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.LeagueRepository;
import com.lucchetta.core.port.MatchRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Matches registered in other leagues that also count in a league (decision D-031): no guests,
 * the four players are all current members of the league, it is not a game of a tournament stage,
 * and on its date the league has a points-per-match season with that playing day. The league
 * decides whether it receives them. Points are those of the league's own season; the match is
 * still changed only by the league where it was registered.
 */
public class SharedMatches {

    /** Most recent first, like the match history. */
    public static final Comparator<MatchDetails> HISTORY_ORDER = Comparator
            .comparing((MatchDetails d) -> d.match().playedAt())
            .thenComparing(d -> d.match().registeredAt())
            .reversed();

    private final LeagueRepository leagueRepository;
    private final LeagueMemberRepository memberRepository;
    private final MatchRepository matchRepository;
    private final MatchDetailsAssembler assembler;

    public SharedMatches(LeagueRepository leagueRepository,
                         LeagueMemberRepository memberRepository,
                         MatchRepository matchRepository,
                         MatchDetailsAssembler assembler) {
        this.leagueRepository = leagueRepository;
        this.memberRepository = memberRepository;
        this.matchRepository = matchRepository;
        this.assembler = assembler;
    }

    /**
     * Matches of other leagues that count in this league, evaluated with its seasons.
     *
     * @param from     inclusive lower bound, or null
     * @param to       exclusive upper bound, or null
     * @param playerId only matches this player took part in, or null
     */
    public List<MatchDetails> into(UUID leagueId, Instant from, Instant to, UUID playerId) {
        if (!receives(leagueId)) {
            return List.of();
        }
        List<Match> candidates = matchRepository.findSharedCandidates(leagueId, members(leagueId), from, to).stream()
                .filter(match -> playerId == null || match.playerIds().contains(playerId))
                .toList();
        return assembler.assembleIn(leagueId, candidates).stream().filter(SharedMatches::countsHere).toList();
    }

    /** The match as it counts in this league, when it was registered in another league and counts here. */
    public Optional<MatchDetails> sharedInto(UUID leagueId, Match match) {
        if (match.leagueId().equals(leagueId) || match.stageMatch() || match.hasGuest() || !receives(leagueId)
                || !members(leagueId).containsAll(match.playerIds())) {
            return Optional.empty();
        }
        return Optional.of(assembler.assembleIn(leagueId, List.of(match)).getFirst())
                .filter(SharedMatches::countsHere);
    }

    /** The league's own matches and those shared into it, most recent first. */
    public static List<MatchDetails> merged(List<MatchDetails> own, List<MatchDetails> shared) {
        return shared.isEmpty() ? own : Stream.concat(own.stream(), shared.stream()).sorted(HISTORY_ORDER).toList();
    }

    /** Only where the league ranks by points per match, on one of its playing days. */
    private static boolean countsHere(MatchDetails details) {
        return details.season() != null
                && !details.season().dailyTournament()
                && details.season().isPlayingDay(details.match().playedOn());
    }

    private boolean receives(UUID leagueId) {
        return leagueRepository.findById(leagueId).map(League::sharedMatches).orElse(false);
    }

    private Set<UUID> members(UUID leagueId) {
        return memberRepository.findByLeague(leagueId).stream()
                .map(LeagueMember::playerId)
                .collect(Collectors.toSet());
    }
}
