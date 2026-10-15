package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.port.InviteCodeGenerator;
import com.lucchetta.core.port.LeagueRepository;
import com.lucchetta.core.port.TransactionRunner;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

/**
 * League invite codes (decision D-019): league administrators share the code (or a link with
 * it); whoever uses it joins directly, private league or not. Generating a new code invalidates
 * the previous one.
 */
public class InviteCodes {

    private final LeagueAccess leagueAccess;
    private final LeagueRepository leagueRepository;
    private final InviteCodeGenerator generator;
    private final LeagueAdmission admission;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public InviteCodes(LeagueAccess leagueAccess,
                       LeagueRepository leagueRepository,
                       InviteCodeGenerator generator,
                       LeagueAdmission admission,
                       TransactionRunner transactionRunner,
                       Clock clock) {
        this.leagueAccess = leagueAccess;
        this.leagueRepository = leagueRepository;
        this.generator = generator;
        this.admission = admission;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    /** The current code, or null if none was generated yet (league administrators). */
    public String current(AuthenticatedUser user, UUID leagueId) {
        LeagueAccess.Context context = leagueAccess.context(user, leagueId);
        LeagueAccess.requireAdministrator(context);
        return context.league().inviteCode();
    }

    /** Generates a new code; the previous one stops working (league administrators). */
    public String regenerate(AuthenticatedUser user, UUID leagueId) {
        return transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, leagueId);
            LeagueAccess.requireAdministrator(context);
            LeagueAccess.requireNotArchived(context.league());
            League saved = leagueRepository.update(context.league().withInviteCode(generator.next(), clock.instant()));
            return saved.inviteCode();
        });
    }

    /** Joins the league of the code. Using it while already a member changes nothing. */
    public LeagueView join(AuthenticatedUser user, String code) {
        String normalized = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        UUID leagueId = normalized.isEmpty() ? null : leagueRepository.findByInviteCode(normalized)
                .map(League::id)
                .orElse(null);
        if (leagueId == null) {
            throw new NotFoundException("INVITE_CODE_INVALID");
        }
        return transactionRunner.inTransaction(() -> {
            LeagueAccess.Context context = leagueAccess.lockedContext(user, leagueId);
            if (context.member()) {
                return leagueAccess.view(context);
            }
            if (!normalized.equals(context.league().inviteCode())) {
                throw new NotFoundException("INVITE_CODE_INVALID");
            }
            LeagueMember member = admission.admit(context.league(), context.player().id());
            return leagueAccess.view(new LeagueAccess.Context(user, context.player(), context.league(), member));
        });
    }
}
