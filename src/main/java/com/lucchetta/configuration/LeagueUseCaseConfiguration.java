package com.lucchetta.configuration;

import com.lucchetta.core.port.InviteCodeGenerator;
import com.lucchetta.core.port.JoinRequestRepository;
import com.lucchetta.core.port.LeagueInvitationRepository;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.LeagueRepository;
import com.lucchetta.core.port.NotificationRepository;
import com.lucchetta.core.port.PlayerRepository;
import com.lucchetta.core.port.TransactionRunner;
import com.lucchetta.core.usecase.league.CreateLeague;
import com.lucchetta.core.usecase.league.InviteCodes;
import com.lucchetta.core.usecase.league.JoinRequests;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.league.LeagueAdmission;
import com.lucchetta.core.usecase.league.LeagueArchiving;
import com.lucchetta.core.usecase.league.LeagueInvitations;
import com.lucchetta.core.usecase.league.LeagueMembership;
import com.lucchetta.core.usecase.league.QueryLeagues;
import com.lucchetta.core.usecase.league.UpdateLeague;
import com.lucchetta.core.usecase.notification.LeagueNotifier;
import com.lucchetta.core.usecase.notification.PushDispatcher;
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
                              LeagueMemberRepository leagueMemberRepository,
                              JoinRequestRepository joinRequestRepository) {
        return new LeagueAccess(getOrCreateCurrentPlayer, leagueRepository, leagueMemberRepository,
                joinRequestRepository);
    }

    @Bean
    LeagueAdmission leagueAdmission(LeagueMemberRepository leagueMemberRepository,
                                    JoinRequestRepository joinRequestRepository,
                                    LeagueInvitationRepository leagueInvitationRepository,
                                    Clock clock) {
        return new LeagueAdmission(leagueMemberRepository, joinRequestRepository, leagueInvitationRepository, clock);
    }

    @Bean
    LeagueNotifier leagueNotifier(NotificationRepository notificationRepository, Clock clock) {
        return new LeagueNotifier(notificationRepository, clock);
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
                              LeagueMemberRepository leagueMemberRepository,
                              JoinRequestRepository joinRequestRepository) {
        return new QueryLeagues(leagueAccess, leagueRepository, leagueMemberRepository, joinRequestRepository);
    }

    @Bean
    LeagueMembership leagueMembership(LeagueAccess leagueAccess,
                                      LeagueMemberRepository leagueMemberRepository,
                                      PlayerRepository playerRepository,
                                      LeagueAdmission leagueAdmission,
                                      TransactionRunner transactionRunner) {
        return new LeagueMembership(leagueAccess, leagueMemberRepository, playerRepository, leagueAdmission,
                transactionRunner);
    }

    @Bean
    JoinRequests joinRequests(LeagueAccess leagueAccess,
                              JoinRequestRepository joinRequestRepository,
                              LeagueMemberRepository leagueMemberRepository,
                              PlayerRepository playerRepository,
                              LeagueAdmission leagueAdmission,
                              LeagueNotifier leagueNotifier,
                              PushDispatcher pushDispatcher,
                              TransactionRunner transactionRunner,
                              Clock clock) {
        return new JoinRequests(leagueAccess, joinRequestRepository, leagueMemberRepository, playerRepository,
                leagueAdmission, leagueNotifier, pushDispatcher, transactionRunner, clock);
    }

    @Bean
    LeagueInvitations leagueInvitations(LeagueAccess leagueAccess,
                                        LeagueInvitationRepository leagueInvitationRepository,
                                        LeagueRepository leagueRepository,
                                        LeagueMemberRepository leagueMemberRepository,
                                        PlayerRepository playerRepository,
                                        LeagueAdmission leagueAdmission,
                                        LeagueNotifier leagueNotifier,
                                        PushDispatcher pushDispatcher,
                                        TransactionRunner transactionRunner,
                                        Clock clock) {
        return new LeagueInvitations(leagueAccess, leagueInvitationRepository, leagueRepository,
                leagueMemberRepository, playerRepository, leagueAdmission, leagueNotifier, pushDispatcher,
                transactionRunner, clock);
    }

    @Bean
    InviteCodes inviteCodes(LeagueAccess leagueAccess,
                            LeagueRepository leagueRepository,
                            InviteCodeGenerator inviteCodeGenerator,
                            LeagueAdmission leagueAdmission,
                            TransactionRunner transactionRunner,
                            Clock clock) {
        return new InviteCodes(leagueAccess, leagueRepository, inviteCodeGenerator, leagueAdmission,
                transactionRunner, clock);
    }
}
