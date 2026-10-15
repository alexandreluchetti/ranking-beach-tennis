package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueVisibility;
import com.lucchetta.core.exception.ConflictException;
import com.lucchetta.core.port.LeagueRepository;
import com.lucchetta.core.port.TransactionRunner;

import java.time.Clock;
import java.util.UUID;

/**
 * League administrators change the name, description or visibility of a league that is not
 * archived.
 */
public class UpdateLeague {

    /**
     * Null fields are left unchanged; a blank description removes it.
     */
    public record Command(String name, String description, LeagueVisibility visibility) {
    }

    private final LeagueAccess leagueAccess;
    private final LeagueRepository leagueRepository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public UpdateLeague(LeagueAccess leagueAccess,
                        LeagueRepository leagueRepository,
                        TransactionRunner transactionRunner,
                        Clock clock) {
        this.leagueAccess = leagueAccess;
        this.leagueRepository = leagueRepository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public LeagueView execute(AuthenticatedUser user, UUID leagueId, Command command) {
        return transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, leagueId);
            LeagueAccess.requireAdministrator(context);
            League current = context.league();
            LeagueAccess.requireNotArchived(current);

            League changed = current.withDetails(
                    command.name() == null ? current.name() : command.name(),
                    command.description() == null ? current.description() : command.description(),
                    command.visibility() == null ? current.visibility() : command.visibility(),
                    clock.instant());
            if (!changed.name().equalsIgnoreCase(current.name())
                    && leagueRepository.existsByName(changed.name(), current.id())) {
                throw new ConflictException("LEAGUE_NAME_TAKEN");
            }
            League saved = leagueRepository.update(changed);
            return leagueAccess.view(new LeagueAccess.Context(user, context.player(), saved, context.membership()));
        });
    }
}
