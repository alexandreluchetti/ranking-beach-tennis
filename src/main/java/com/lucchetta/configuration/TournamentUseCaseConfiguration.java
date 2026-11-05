package com.lucchetta.configuration;

import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.MatchAuditLog;
import com.lucchetta.core.port.MatchRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.port.SeasonRepository;
import com.lucchetta.core.port.TournamentStageRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.match.MatchDetailsAssembler;
import com.lucchetta.core.usecase.match.MatchValidator;
import com.lucchetta.core.usecase.notification.MatchNotifier;
import com.lucchetta.core.usecase.notification.PushDispatcher;
import com.lucchetta.core.usecase.season.QuerySeasons;
import com.lucchetta.core.usecase.tournament.ManageStages;
import com.lucchetta.core.usecase.tournament.QueryStages;
import com.lucchetta.core.usecase.tournament.StageScores;
import com.lucchetta.core.usecase.tournament.StageViewAssembler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;

/** Daily tournament use cases (decisions D-026 to D-028). */
@Configuration
public class TournamentUseCaseConfiguration {

    @Bean
    StageViewAssembler stageViewAssembler(MatchRepository matchRepository,
                                          MatchDetailsAssembler matchDetailsAssembler,
                                          PlayerRepository playerRepository) {
        return new StageViewAssembler(matchRepository, matchDetailsAssembler, playerRepository);
    }

    @Bean
    QueryStages queryStages(LeagueAccess leagueAccess, QuerySeasons querySeasons,
                            TournamentStageRepository stageRepository, StageViewAssembler stageViewAssembler) {
        return new QueryStages(leagueAccess, querySeasons, stageRepository, stageViewAssembler);
    }

    /** The draw is random and unpredictable (D-027). */
    @Bean
    ManageStages manageStages(LeagueAccess leagueAccess,
                              SeasonRepository seasonRepository,
                              LeagueMemberRepository leagueMemberRepository,
                              TournamentStageRepository stageRepository,
                              StageViewAssembler stageViewAssembler,
                              TransactionRunner transactionRunner,
                              Clock clock) {
        return new ManageStages(leagueAccess, seasonRepository, leagueMemberRepository, stageRepository,
                stageViewAssembler, transactionRunner, new SecureRandom(), clock);
    }

    @Bean
    StageScores stageScores(LeagueAccess leagueAccess,
                            TournamentStageRepository stageRepository,
                            MatchRepository matchRepository,
                            MatchAuditLog matchAuditLog,
                            MatchValidator matchValidator,
                            MatchDetailsAssembler matchDetailsAssembler,
                            StageViewAssembler stageViewAssembler,
                            MatchNotifier matchNotifier,
                            PushDispatcher pushDispatcher,
                            TransactionRunner transactionRunner,
                            Clock clock) {
        return new StageScores(leagueAccess, stageRepository, matchRepository, matchAuditLog, matchValidator,
                matchDetailsAssembler, stageViewAssembler, matchNotifier, pushDispatcher, transactionRunner, clock);
    }
}
