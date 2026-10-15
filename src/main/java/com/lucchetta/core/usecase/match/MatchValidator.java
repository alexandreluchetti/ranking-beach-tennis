package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.LeagueMemberRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rules that need the outside world (clock, league members), shared by registration and
 * correction. Rules on the match itself live in the domain objects.
 */
public class MatchValidator {

    static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(10);

    private final LeagueMemberRepository memberRepository;

    public MatchValidator(LeagueMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public void validate(Match match, Instant now) {
        if (match.playedAt().isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new RuleViolationException("MATCH_IN_FUTURE");
        }
        // Every registered player must be a member of the match's league (decision D-021);
        // anyone else can only take part as a guest.
        Set<UUID> members = memberRepository.findByLeague(match.leagueId()).stream()
                .map(LeagueMember::playerId)
                .collect(Collectors.toSet());
        if (!members.containsAll(match.playerIds())) {
            throw new RuleViolationException("PLAYER_NOT_LEAGUE_MEMBER");
        }
    }
}
