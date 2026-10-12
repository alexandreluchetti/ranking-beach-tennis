package com.beachtennis.ranking.configuration;

import com.beachtennis.ranking.core.port.PlayerRepository;
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
}
