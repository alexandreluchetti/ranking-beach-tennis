package com.lucchetta.configuration;

import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.LeagueRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.league.CreateLeague;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.league.LeagueArchiving;
import com.lucchetta.core.usecase.league.LeagueMembership;
import com.lucchetta.core.usecase.league.QueryLeagues;
import com.lucchetta.core.usecase.league.UpdateLeague;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the league use cases (decisions D-017 to D-020).
 */
@Configuration
public class LeagueUseCaseConfiguration {

    @Bean
    LeagueAccess leagueAccess(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                              LeagueRepository leagueRepository,
                              LeagueMemberRepository leagueMemberRepository) {
        return new LeagueAccess(getOrCreateCurrentPlayer, leagueRepository, leagueMemberRepository);
    }

    @Bean
    CreateLeague createLeague(LeagueAccess leagueAccess,
                              LeagueRepository leagueRepository,
                              LeagueMemberRepository leagueMemberRepository,
                              TransactionRunner transactionRunner,
                              Clock clock) {
        return new CreateLeague(leagueAccess, leagueRepository, leagueMemberRepository, transactionRunner, clock);
    }

    @Bean
    UpdateLeague updateLeague(LeagueAccess leagueAccess,
                              LeagueRepository leagueRepository,
                              TransactionRunner transactionRunner,
                              Clock clock) {
        return new UpdateLeague(leagueAccess, leagueRepository, transactionRunner, clock);
    }

    @Bean
    LeagueArchiving leagueArchiving(LeagueAccess leagueAccess,
                                    LeagueRepository leagueRepository,
                                    TransactionRunner transactionRunner,
                                    Clock clock) {
        return new LeagueArchiving(leagueAccess, leagueRepository, transactionRunner, clock);
    }

    @Bean
    QueryLeagues queryLeagues(LeagueAccess leagueAccess,
                              LeagueRepository leagueRepository,
                              LeagueMemberRepository leagueMemberRepository) {
        return new QueryLeagues(leagueAccess, leagueRepository, leagueMemberRepository);
    }

    @Bean
    LeagueMembership leagueMembership(LeagueAccess leagueAccess,
                                      LeagueMemberRepository leagueMemberRepository,
                                      PlayerRepository playerRepository,
                                      TransactionRunner transactionRunner,
                                      Clock clock) {
        return new LeagueMembership(leagueAccess, leagueMemberRepository, playerRepository, transactionRunner, clock);
    }
}
