package com.beachtennis.ranking.core.usecase.ranking;

import com.beachtennis.ranking.core.domain.Player;
import com.beachtennis.ranking.core.domain.Season;
import com.beachtennis.ranking.core.domain.match.Match;
import com.beachtennis.ranking.core.domain.match.MatchFormat;
import com.beachtennis.ranking.core.domain.match.MatchStatus;
import com.beachtennis.ranking.core.domain.match.Participant;
import com.beachtennis.ranking.core.domain.match.Score;
import com.beachtennis.ranking.core.domain.match.Team;
import com.beachtennis.ranking.core.scoring.GamesPlusWinBonusScoringPolicy;
import com.beachtennis.ranking.core.scoring.RankingEligibilityPolicy;
import com.beachtennis.ranking.core.support.InMemoryMatches;
import com.beachtennis.ranking.core.support.InMemoryPlayers;
import com.beachtennis.ranking.core.support.InMemorySeasons;
import com.beachtennis.ranking.core.usecase.match.MatchDetailsAssembler;
import com.beachtennis.ranking.core.usecase.season.QuerySeasons;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Shared world for ranking/statistics/history tests. "Today" is 2026-10-12 (Brasília).
 * Past season: 2026-07-01..2026-09-30. Active season: 2026-10-01..2026-12-31.
 */
public class RankingFixture {

    public static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    public static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    public final InMemoryPlayers players = new InMemoryPlayers();
    public final InMemorySeasons seasons = new InMemorySeasons();
    public final InMemoryMatches matches = new InMemoryMatches();
    public final QuerySeasons querySeasons = new QuerySeasons(seasons, CLOCK);
    public final MatchDetailsAssembler assembler = new MatchDetailsAssembler(seasons, players,
            new RankingEligibilityPolicy(), new GamesPlusWinBonusScoringPolicy());

    public final Player ana = players.add("Ana");
    public final Player beto = players.add("Beto");
    public final Player caio = players.add("Caio");
    public final Player dani = players.add("Dani");

    public final Season pastSeason = season("Inverno", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30));
    public final Season activeSeason = season("Primavera", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));

    public Season season(String name, LocalDate start, LocalDate end) {
        Instant created = Instant.parse("2026-01-01T12:00:00Z");
        return seasons.add(new Season(UUID.randomUUID(), name, start, end, ana.id(), created, created));
    }

    /** Ana+Beto (team A) vs Caio+Dani (team B) at noon on the given day. */
    public Match match(LocalDate day, int teamAGames, int teamBGames) {
        return match(day, teamAGames, teamBGames, MatchStatus.VALID, List.of(
                Participant.player(Team.A, 1, ana.id()), Participant.player(Team.A, 2, beto.id()),
                Participant.player(Team.B, 1, caio.id()), Participant.player(Team.B, 2, dani.id())));
    }

    public Match match(LocalDate day, int teamAGames, int teamBGames, MatchStatus status,
                       List<Participant> participants) {
        Instant playedAt = day.atTime(LocalTime.NOON).atZone(BRASILIA).toInstant();
        return matches.add(new Match(UUID.randomUUID(), playedAt,
                new Score(MatchFormat.STANDARD, teamAGames, teamBGames, null, null), participants, status,
                ana.id(), playedAt, playedAt));
    }

    public List<Participant> anaAndGuestVsCaioAndDani() {
        return List.of(
                Participant.player(Team.A, 1, ana.id()), Participant.guest(Team.A, 2, "Zé"),
                Participant.player(Team.B, 1, caio.id()), Participant.player(Team.B, 2, dani.id()));
    }
}
