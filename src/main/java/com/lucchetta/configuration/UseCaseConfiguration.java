package com.lucchetta.configuration;

import com.lucchetta.core.port.DeviceTokenRepository;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.MatchAuditLog;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.port.NotificationRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.port.PushSender;
import com.lucchetta.core.port.SeasonAuditLog;
import com.lucchetta.core.port.SeasonRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.scoring.GamesPlusWinBonusScoringPolicy;
import com.lucchetta.core.scoring.RankingEligibilityPolicy;
import com.lucchetta.core.scoring.ScoringPolicy;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;
import com.lucchetta.core.usecase.match.MatchValidator;
import com.lucchetta.core.usecase.match.QueryMatch;
import com.lucchetta.core.usecase.match.RegisterMatch;
import com.lucchetta.core.usecase.match.SearchMatches;
import com.lucchetta.core.usecase.match.admin.MatchAdministration;
import com.lucchetta.core.usecase.notification.MatchNotificationComposer;
import com.lucchetta.core.usecase.notification.MatchNotifier;
import com.lucchetta.core.usecase.notification.NotificationInbox;
import com.lucchetta.core.usecase.notification.PushDispatcher;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;
import com.lucchetta.core.usecase.player.QueryPlayers;
import com.lucchetta.core.usecase.player.UpdateDisplayName;
import com.lucchetta.core.usecase.ranking.GetPlayerProfile;
import com.lucchetta.core.usecase.ranking.GetSeasonRanking;
import com.lucchetta.core.usecase.season.CreateSeason;
import com.lucchetta.core.usecase.season.QuerySeasons;
import com.lucchetta.core.usecase.season.UpdateSeason;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the framework-free use cases from the core package. Leagues: see
 * {@link LeagueUseCaseConfiguration}.
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
    CreateSeason createSeason(LeagueAccess leagueAccess,
                              SeasonRepository seasonRepository,
                              SeasonAuditLog seasonAuditLog,
                              TransactionRunner transactionRunner,
                              Clock clock) {
        return new CreateSeason(leagueAccess, seasonRepository, seasonAuditLog, transactionRunner, clock);
    }

    @Bean
    UpdateSeason updateSeason(LeagueAccess leagueAccess,
                              SeasonRepository seasonRepository,
                              SeasonAuditLog seasonAuditLog,
                              TransactionRunner transactionRunner,
                              Clock clock) {
        return new UpdateSeason(leagueAccess, seasonRepository, seasonAuditLog, transactionRunner, clock);
    }

    @Bean
    QuerySeasons querySeasons(LeagueAccess leagueAccess, SeasonRepository seasonRepository, Clock clock) {
        return new QuerySeasons(leagueAccess, seasonRepository, clock);
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
    MatchValidator matchValidator(LeagueMemberRepository leagueMemberRepository) {
        return new MatchValidator(leagueMemberRepository);
    }

    @Bean
    MatchNotifier matchNotifier(NotificationRepository notificationRepository,
                                LeagueMemberRepository leagueMemberRepository,
                                Clock clock) {
        return new MatchNotifier(notificationRepository, leagueMemberRepository, new MatchNotificationComposer(),
                clock);
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
    RegisterMatch registerMatch(LeagueAccess leagueAccess,
                                MatchRepository matchRepository,
                                MatchValidator matchValidator,
                                MatchDetailsAssembler matchDetailsAssembler,
                                MatchNotifier matchNotifier,
                                PushDispatcher pushDispatcher,
                                TransactionRunner transactionRunner,
                                Clock clock) {
        return new RegisterMatch(leagueAccess, matchRepository, matchValidator, matchDetailsAssembler, matchNotifier,
                pushDispatcher, transactionRunner, clock);
    }

    @Bean
    QueryMatch queryMatch(LeagueAccess leagueAccess, MatchRepository matchRepository, MatchAuditLog matchAuditLog,
                          MatchDetailsAssembler matchDetailsAssembler) {
        return new QueryMatch(leagueAccess, matchRepository, matchAuditLog, matchDetailsAssembler);
    }

    @Bean
    MatchAdministration matchAdministration(LeagueAccess leagueAccess,
                                            MatchRepository matchRepository,
                                            MatchAuditLog matchAuditLog,
                                            MatchValidator matchValidator,
                                            MatchDetailsAssembler matchDetailsAssembler,
                                            MatchNotifier matchNotifier,
                                            PushDispatcher pushDispatcher,
                                            TransactionRunner transactionRunner,
                                            Clock clock) {
        return new MatchAdministration(leagueAccess, matchRepository, matchAuditLog, matchValidator,
                matchDetailsAssembler, matchNotifier, pushDispatcher, transactionRunner, clock);
    }

    @Bean
    SearchMatches searchMatches(LeagueAccess leagueAccess, MatchRepository matchRepository, QuerySeasons querySeasons,
                                MatchDetailsAssembler matchDetailsAssembler) {
        return new SearchMatches(leagueAccess, matchRepository, querySeasons, matchDetailsAssembler);
    }

    @Bean
    QueryPlayers queryPlayers(LeagueAccess leagueAccess, LeagueMemberRepository leagueMemberRepository,
                              PlayerRepository playerRepository) {
        return new QueryPlayers(leagueAccess, leagueMemberRepository, playerRepository);
    }

    @Bean
    GetSeasonRanking getSeasonRanking(LeagueAccess leagueAccess, QuerySeasons querySeasons,
                                      MatchRepository matchRepository, PlayerRepository playerRepository,
                                      LeagueMemberRepository leagueMemberRepository,
                                      MatchDetailsAssembler matchDetailsAssembler) {
        return new GetSeasonRanking(leagueAccess, querySeasons, matchRepository, playerRepository,
                leagueMemberRepository, matchDetailsAssembler);
    }

    @Bean
    GetPlayerProfile getPlayerProfile(LeagueAccess leagueAccess, PlayerRepository playerRepository,
                                      LeagueMemberRepository leagueMemberRepository, MatchRepository matchRepository,
                                      QuerySeasons querySeasons, MatchDetailsAssembler matchDetailsAssembler) {
        return new GetPlayerProfile(leagueAccess, playerRepository, leagueMemberRepository, matchRepository,
                querySeasons, matchDetailsAssembler);
    }
}
