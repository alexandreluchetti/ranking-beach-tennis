package com.lucchetta.core.usecase.ranking;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.Season;
import com.lucchetta.core.domain.match.Match;
import com.lucchetta.core.domain.match.MatchFormat;
import com.lucchetta.core.domain.match.MatchStatus;
import com.lucchetta.core.domain.match.Participant;
import com.lucchetta.core.domain.match.Score;
import com.lucchetta.core.domain.match.Team;
import com.lucchetta.core.scoring.GamesPlusWinBonusScoringPolicy;
import com.lucchetta.core.scoring.RankingEligibilityPolicy;
import com.lucchetta.core.support.InMemoryMatches;
import com.lucchetta.core.support.InMemoryPlayers;
import com.lucchetta.core.support.InMemorySeasons;
import com.lucchetta.core.support.TestLeague;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;
import com.lucchetta.core.usecase.season.QuerySeasons;

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
    public final TestLeague league = new TestLeague();
    public final LeagueAccess access = league.access(new GetOrCreateCurrentPlayer(players, CLOCK));
    public final QuerySeasons querySeasons = new QuerySeasons(access, seasons, CLOCK);
    public final MatchDetailsAssembler assembler = new MatchDetailsAssembler(seasons, players,
            new RankingEligibilityPolicy(), new GamesPlusWinBonusScoringPolicy());

    public final Player ana = players.add("Ana");
    public final Player beto = players.add("Beto");
    public final Player caio = players.add("Caio");
    public final Player dani = players.add("Dani");

    {
        league.members(ana, beto, caio, dani);
    }

    public final Season pastSeason = season("Inverno", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30));
    public final Season activeSeason = season("Primavera", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));

    public Season season(String name, LocalDate start, LocalDate end) {
        Instant created = Instant.parse("2026-01-01T12:00:00Z");
        return seasons.add(new Season(UUID.randomUUID(), TestLeague.ID, name, start, end, ana.id(), created,
                created));
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
        return matches.add(new Match(UUID.randomUUID(), TestLeague.ID, playedAt,
                new Score(MatchFormat.STANDARD, teamAGames, teamBGames, null, null), participants, status,
                ana.id(), playedAt, playedAt));
    }

    /** The player as a signed-in caller (not the system administrator). */
    public static AuthenticatedUser user(Player player) {
        return new AuthenticatedUser(player.identity(), null, player.displayName().value(), false);
    }

    /** A registered player who is also a member of the league since registering. */
    public Player member(String name, Instant registeredAt) {
        Player player = players.add(name, registeredAt);
        league.members(player);
        return player;
    }

    public List<Participant> anaAndGuestVsCaioAndDani() {
        return List.of(
                Participant.player(Team.A, 1, ana.id()), Participant.guest(Team.A, 2, "Zé"),
                Participant.player(Team.B, 1, caio.id()), Participant.player(Team.B, 2, dani.id()));
    }
}
