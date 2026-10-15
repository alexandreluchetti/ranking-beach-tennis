package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.domain.league.LeagueVisibility;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.LeagueRepository;
import com.lucchetta.core.port.TransactionRunner;

import java.time.Clock;
import java.time.Instant;

/**
 * Any signed-in user creates a league and becomes its administrator (decision D-017).
 */
public class CreateLeague {

    public record Command(String name, String description, LeagueVisibility visibility) {
    }

    private final LeagueAccess leagueAccess;
    private final LeagueRepository leagueRepository;
    private final LeagueMemberRepository memberRepository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public CreateLeague(LeagueAccess leagueAccess,
                        LeagueRepository leagueRepository,
                        LeagueMemberRepository memberRepository,
                        TransactionRunner transactionRunner,
                        Clock clock) {
        this.leagueAccess = leagueAccess;
        this.leagueRepository = leagueRepository;
        this.memberRepository = memberRepository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public LeagueView execute(AuthenticatedUser user, Command command) {
        Player creator = leagueAccess.player(user);
        Instant now = clock.instant();
        League league = League.create(command.name(), command.description(), command.visibility(), creator.id(), now);

        League created = transactionRunner.inTransaction(() -> {
            if (!user.admin()
                    && leagueRepository.countActiveCreatedBy(creator.id()) >= League.MAX_ACTIVE_CREATED_PER_USER) {
                throw new RuleViolationException("LEAGUE_LIMIT_REACHED");
            }
            if (leagueRepository.existsByName(league.name(), null)) {
                throw new ConflictException("LEAGUE_NAME_TAKEN");
            }
            League inserted = leagueRepository.insert(league);
            memberRepository.insert(new LeagueMember(inserted.id(), creator.id(), LeagueRole.ADMIN, now));
            return inserted;
        });
        return new LeagueView(created, 1, LeagueRole.ADMIN, true);
    }
}
