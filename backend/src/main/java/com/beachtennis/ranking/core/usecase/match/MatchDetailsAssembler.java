package com.beachtennis.ranking.core.usecase.match;

import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.domain.Season;
import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.Participant;
import com.beachtennis.ranking.core.domain.match.Team;
import com.beachtennis.ranking.core.port.PlayerRepository;
import com.beachtennis.ranking.core.port.SeasonRepository;
import com.beachtennis.ranking.core.scoring.RankingEligibility;
import com.beachtennis.ranking.core.scoring.RankingEligibilityPolicy;
import com.beachtennis.ranking.core.scoring.ScoringPolicy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class MatchDetailsAssembler {

    private final SeasonRepository seasonRepository;
    private final PlayerRepository playerRepository;
    private final RankingEligibilityPolicy eligibilityPolicy;
    private final ScoringPolicy scoringPolicy;

    public MatchDetailsAssembler(SeasonRepository seasonRepository,
                                 PlayerRepository playerRepository,
                                 RankingEligibilityPolicy eligibilityPolicy,
                                 ScoringPolicy scoringPolicy) {
        this.seasonRepository = seasonRepository;
        this.playerRepository = playerRepository;
        this.eligibilityPolicy = eligibilityPolicy;
        this.scoringPolicy = scoringPolicy;
    }

    public MatchDetails assemble(Match match) {
        Season season = seasonRepository.findContaining(match.playedOn()).orElse(null);
        RankingEligibility eligibility = eligibilityPolicy.evaluate(match, season);

        Map<Team, Integer> points = new EnumMap<>(Team.class);
        for (Team team : Team.values()) {
            points.put(team, eligibility.counted() ? scoringPolicy.pointsPerPlayer(match, team) : 0);
        }

        Map<UUID, String> names = playerRepository.findAllByIds(match.participants().stream()
                        .map(Participant::playerId)
                        .filter(Objects::nonNull)
                        .toList())
                .stream()
                .collect(Collectors.toMap(Player::id, p -> p.displayName().value()));

        return new MatchDetails(match, season, eligibility, names, points);
    }
}
