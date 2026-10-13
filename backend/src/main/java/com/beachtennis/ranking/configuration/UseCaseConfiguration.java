package com.beachtennis.ranking.configuration;

import com.beachtennis.ranking.core.port.MatchAuditLog;
import com.beachtennis.ranking.core.port.MatchRepository;
import com.beachtennis.ranking.core.port.PlayerRepository;
import com.beachtennis.ranking.core.scoring.GamesPlusWinBonusScoringPolicy;
import com.beachtennis.ranking.core.scoring.RankingEligibilityPolicy;
import com.beachtennis.ranking.core.scoring.ScoringPolicy;
import com.beachtennis.ranking.core.usecase.match.MatchDetailsAssembler;
import com.beachtennis.ranking.core.usecase.match.MatchValidator;
import com.beachtennis.ranking.core.usecase.match.admin.MatchAdministration;
import com.beachtennis.ranking.core.usecase.match.QueryMatch;
import com.beachtennis.ranking.core.usecase.match.RegisterMatch;
import com.beachtennis.ranking.core.usecase.match.SearchMatches;
import com.beachtennis.ranking.core.usecase.player.QueryPlayers;
import com.beachtennis.ranking.core.usecase.ranking.GetPlayerProfile;
import com.beachtennis.ranking.core.usecase.ranking.GetSeasonRanking;
import com.beachtennis.ranking.core.port.SeasonAuditLog;
import com.beachtennis.ranking.core.port.SeasonRepository;
import com.beachtennis.ranking.core.port.TransactionRunner;
import com.beachtennis.ranking.core.usecase.player.GetOrCreateCurrentPlayer;
import com.beachtennis.ranking.core.usecase.player.UpdateDisplayName;
import com.beachtennis.ranking.core.usecase.season.CreateSeason;
import com.beachtennis.ranking.core.usecase.season.QuerySeasons;
import com.beachtennis.ranking.core.usecase.season.UpdateSeason;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the framework-free use cases from the core package.
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    GetOrCreateCurrentPlayer getOrCreateCurrentPlayer(PlayerRepository playerRepository, Clock clock) {
        return new GetOrCreateCurrentPlayer(playerRepository, clock);
    }

    @Bean
    UpdateDisplayName updateDisplayName(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                                        PlayerRepository playerRepository,
                                        Clock clock) {
        return new UpdateDisplayName(getOrCreateCurrentPlayer, playerRepository, clock);
    }

    @Bean
    CreateSeason createSeason(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                              SeasonRepository seasonRepository,
                              SeasonAuditLog seasonAuditLog,
                              TransactionRunner transactionRunner,
                              Clock clock) {
        return new CreateSeason(getOrCreateCurrentPlayer, seasonRepository, seasonAuditLog, transactionRunner, clock);
    }

    @Bean
    UpdateSeason updateSeason(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                              SeasonRepository seasonRepository,
                              SeasonAuditLog seasonAuditLog,
                              TransactionRunner transactionRunner,
                              Clock clock) {
        return new UpdateSeason(getOrCreateCurrentPlayer, seasonRepository, seasonAuditLog, transactionRunner, clock);
    }

    @Bean
    QuerySeasons querySeasons(SeasonRepository seasonRepository, Clock clock) {
        return new QuerySeasons(seasonRepository, clock);
    }

    @Bean
    ScoringPolicy scoringPolicy() {
        return new GamesPlusWinBonusScoringPolicy();
    }

    @Bean
    RankingEligibilityPolicy rankingEligibilityPolicy() {
        return new RankingEligibilityPolicy();
    }

    @Bean
    MatchDetailsAssembler matchDetailsAssembler(SeasonRepository seasonRepository,
                                                PlayerRepository playerRepository,
                                                RankingEligibilityPolicy rankingEligibilityPolicy,
                                                ScoringPolicy scoringPolicy) {
        return new MatchDetailsAssembler(seasonRepository, playerRepository, rankingEligibilityPolicy, scoringPolicy);
    }

    @Bean
    MatchValidator matchValidator(PlayerRepository playerRepository) {
        return new MatchValidator(playerRepository);
    }

    @Bean
    RegisterMatch registerMatch(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                                MatchRepository matchRepository,
                                MatchValidator matchValidator,
                                MatchDetailsAssembler matchDetailsAssembler,
                                TransactionRunner transactionRunner,
                                Clock clock) {
        return new RegisterMatch(getOrCreateCurrentPlayer, matchRepository, matchValidator, matchDetailsAssembler,
                transactionRunner, clock);
    }

    @Bean
    QueryMatch queryMatch(MatchRepository matchRepository, MatchAuditLog matchAuditLog,
                          MatchDetailsAssembler matchDetailsAssembler) {
        return new QueryMatch(matchRepository, matchAuditLog, matchDetailsAssembler);
    }

    @Bean
    MatchAdministration matchAdministration(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                                            MatchRepository matchRepository,
                                            MatchAuditLog matchAuditLog,
                                            MatchValidator matchValidator,
                                            MatchDetailsAssembler matchDetailsAssembler,
                                            TransactionRunner transactionRunner,
                                            Clock clock) {
        return new MatchAdministration(getOrCreateCurrentPlayer, matchRepository, matchAuditLog, matchValidator,
                matchDetailsAssembler, transactionRunner, clock);
    }

    @Bean
    SearchMatches searchMatches(MatchRepository matchRepository, QuerySeasons querySeasons,
                                MatchDetailsAssembler matchDetailsAssembler) {
        return new SearchMatches(matchRepository, querySeasons, matchDetailsAssembler);
    }

    @Bean
    QueryPlayers queryPlayers(PlayerRepository playerRepository) {
        return new QueryPlayers(playerRepository);
    }

    @Bean
    GetSeasonRanking getSeasonRanking(QuerySeasons querySeasons, MatchRepository matchRepository,
                                      PlayerRepository playerRepository, MatchDetailsAssembler matchDetailsAssembler) {
        return new GetSeasonRanking(querySeasons, matchRepository, playerRepository, matchDetailsAssembler);
    }

    @Bean
    GetPlayerProfile getPlayerProfile(PlayerRepository playerRepository, MatchRepository matchRepository,
                                      QuerySeasons querySeasons, MatchDetailsAssembler matchDetailsAssembler) {
        return new GetPlayerProfile(playerRepository, matchRepository, querySeasons, matchDetailsAssembler);
    }
}
