package com.lucchetta.core.usecase.match;

import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.domain.match.Team;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.port.SeasonRepository;
import com.lucchetta.core.scoring.RankingEligibility;
import com.lucchetta.core.scoring.RankingEligibilityPolicy;
import com.lucchetta.core.scoring.ScoringPolicy;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
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

    /**
     * Batch version: one query for the seasons of each league involved and one for player names.
     * Keeps the input order.
     */
    public List<MatchDetails> assembleAll(List<Match> matches) {
        return assemble(matches, null);
    }

    /**
     * Matches of other leagues as they count in {@code leagueId} (D-031): its seasons, playing days
     * and ranking type decide, not those of the league where each match was registered.
     */
    public List<MatchDetails> assembleIn(UUID leagueId, List<Match> matches) {
        return assemble(matches, leagueId);
    }

    /** @param viewingLeague the league whose seasons apply to every match, or null for each match's own */
    private List<MatchDetails> assemble(List<Match> matches, UUID viewingLeague) {
        if (matches.isEmpty()) {
            return List.of();
        }
        // One query per league whose seasons apply.
        Map<UUID, List<Season>> seasonsByLeague = new HashMap<>();
        Function<Match, List<Season>> seasonsOf = match -> seasonsByLeague.computeIfAbsent(
                viewingLeague == null ? match.leagueId() : viewingLeague, seasonRepository::findAll);
        Map<UUID, String> names = playerRepository.findAllByIds(matches.stream()
                        .flatMap(m -> m.participants().stream())
                        .map(Participant::playerId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Player::id, p -> p.displayName().value()));
        return matches.stream().map(match -> assemble(match, seasonsOf.apply(match), names)).toList();
    }

    private MatchDetails assemble(Match match, List<Season> seasons, Map<UUID, String> allNames) {
        Season season = seasonContaining(seasons, match.playedOn());
        RankingEligibility eligibility = eligibilityPolicy.evaluate(match, season);

        Map<Team, Integer> points = new EnumMap<>(Team.class);
        for (Team team : Team.values()) {
            // A daily tournament gives points per stage placement (D-028), never per match.
            boolean matchPoints = eligibility.counted() && !season.dailyTournament();
            points.put(team, matchPoints ? scoringPolicy.pointsPerPlayer(match, team) : 0);
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
