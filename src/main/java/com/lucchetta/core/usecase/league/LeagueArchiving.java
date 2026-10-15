package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.LeagueRepository;
import com.lucchetta.core.port.TransactionRunner;

import java.time.Clock;
import java.util.UUID;

/**
 * Leagues are never deleted, only archived (decision D-017): any league administrator or the
 * system administrator archives; only the system administrator unarchives.
 */
public class LeagueArchiving {

    private final LeagueAccess leagueAccess;
    private final LeagueRepository leagueRepository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public LeagueArchiving(LeagueAccess leagueAccess,
                           LeagueRepository leagueRepository,
                           TransactionRunner transactionRunner,
                           Clock clock) {
        this.leagueAccess = leagueAccess;
        this.leagueRepository = leagueRepository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public LeagueView archive(AuthenticatedUser user, UUID leagueId) {
        return transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, leagueId);
            LeagueAccess.requireAdministrator(context);
            LeagueAccess.requireNotArchived(context.league());
            return save(context, context.league().archive(context.player().id(), clock.instant()));
        });
    }

    public LeagueView unarchive(AuthenticatedUser user, UUID leagueId) {
        if (!user.admin()) {
            throw new ForbiddenException();
        }
        return transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, leagueId);
            if (!context.league().archived()) {
                throw new RuleViolationException("LEAGUE_NOT_ARCHIVED");
            }
            return save(context, context.league().unarchive(clock.instant()));
        });
    }

    private LeagueView save(LeagueAccess.Context context, League changed) {
        League saved = leagueRepository.update(changed);
        return leagueAccess.view(new LeagueAccess.Context(context.user(), context.player(), saved,
                context.membership()));
    }
}
