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

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enriches matches with season, ranking eligibility, points and player names. This is the only
 * place where the eligibility and scoring policies are applied, so every screen (match, history,
 * ranking, profile) shows the same numbers.
 */
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
        return assembleAll(List.of(match)).getFirst();
    }

    /** Batch version: one query for seasons and one for player names. Keeps the input order. */
    public List<MatchDetails> assembleAll(List<Match> matches) {
        if (matches.isEmpty()) {
            return List.of();
        }
        List<Season> seasons = seasonRepository.findAll();
        Map<UUID, String> names = playerRepository.findAllByIds(matches.stream()
                        .flatMap(m -> m.participants().stream())
                        .map(Participant::playerId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Player::id, p -> p.displayName().value()));
        return matches.stream().map(match -> assemble(match, seasons, names)).toList();
    }

    private MatchDetails assemble(Match match, List<Season> seasons, Map<UUID, String> allNames) {
        Season season = seasonContaining(seasons, match.playedOn());
        RankingEligibility eligibility = eligibilityPolicy.evaluate(match, season);

        Map<Team, Integer> points = new EnumMap<>(Team.class);
        for (Team team : Team.values()) {
            points.put(team, eligibility.counted() ? scoringPolicy.pointsPerPlayer(match, team) : 0);
        }
        Map<UUID, String> names = match.participants().stream()
                .map(Participant::playerId)
                .filter(allNames::containsKey)
                .collect(Collectors.toMap(id -> id, allNames::get));
        return new MatchDetails(match, season, eligibility, names, points);
    }

    private static Season seasonContaining(List<Season> seasons, LocalDate day) {
        return seasons.stream()
                .filter(s -> !day.isBefore(s.startDate()) && !day.isAfter(s.endDate()))
                .findFirst()
                .orElse(null);
    }
}
