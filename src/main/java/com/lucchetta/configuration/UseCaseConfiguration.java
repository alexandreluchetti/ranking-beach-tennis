package com.lucchetta.configuration;

import com.lucchetta.core.port.AdministratorDirectory;
import com.lucchetta.core.port.DeviceTokenRepository;
import com.lucchetta.core.port.MatchAuditLog;
import com.lucchetta.core.port.NotificationRepository;
import com.lucchetta.core.port.PushSender;
import com.lucchetta.core.usecase.notification.MatchNotificationComposer;
import com.lucchetta.core.usecase.notification.MatchNotifier;
import com.lucchetta.core.usecase.notification.NotificationInbox;
import com.lucchetta.core.usecase.notification.PushDispatcher;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.scoring.GamesPlusWinBonusScoringPolicy;
import com.lucchetta.core.scoring.RankingEligibilityPolicy;
import com.lucchetta.core.scoring.ScoringPolicy;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;
import com.lucchetta.core.usecase.match.MatchValidator;
import com.lucchetta.core.usecase.match.admin.MatchAdministration;
import com.lucchetta.core.usecase.match.QueryMatch;
import com.lucchetta.core.usecase.match.RegisterMatch;
import com.lucchetta.core.usecase.match.SearchMatches;
import com.lucchetta.core.usecase.player.QueryPlayers;
import com.lucchetta.core.usecase.ranking.GetPlayerProfile;
import com.lucchetta.core.usecase.ranking.GetSeasonRanking;
import com.lucchetta.core.port.SeasonAuditLog;
import com.lucchetta.core.port.SeasonRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;
import com.lucchetta.core.usecase.player.UpdateDisplayName;
import com.lucchetta.core.usecase.season.CreateSeason;
import com.lucchetta.core.usecase.season.QuerySeasons;
import com.lucchetta.core.usecase.season.UpdateSeason;
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
    MatchNotifier matchNotifier(NotificationRepository notificationRepository,
                                AdministratorDirectory administratorDirectory,
                                Clock clock) {
        return new MatchNotifier(notificationRepository, administratorDirectory, new MatchNotificationComposer(), clock);
    }

    @Bean
    PushDispatcher pushDispatcher(DeviceTokenRepository deviceTokenRepository, PushSender pushSender) {
        return new PushDispatcher(deviceTokenRepository, pushSender);
    }

    @Bean
    NotificationInbox notificationInbox(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                                        NotificationRepository notificationRepository,
                                        DeviceTokenRepository deviceTokenRepository,
                                        Clock clock) {
        return new NotificationInbox(getOrCreateCurrentPlayer, notificationRepository, deviceTokenRepository, clock);
    }

    @Bean
    RegisterMatch registerMatch(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                                MatchRepository matchRepository,
                                MatchValidator matchValidator,
                                MatchDetailsAssembler matchDetailsAssembler,
                                MatchNotifier matchNotifier,
                                PushDispatcher pushDispatcher,
                                TransactionRunner transactionRunner,
                                Clock clock) {
        return new RegisterMatch(getOrCreateCurrentPlayer, matchRepository, matchValidator, matchDetailsAssembler,
                matchNotifier, pushDispatcher, transactionRunner, clock);
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
                                            MatchNotifier matchNotifier,
                                            PushDispatcher pushDispatcher,
                                            TransactionRunner transactionRunner,
                                            Clock clock) {
        return new MatchAdministration(getOrCreateCurrentPlayer, matchRepository, matchAuditLog, matchValidator,
                matchDetailsAssembler, matchNotifier, pushDispatcher, transactionRunner, clock);
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
