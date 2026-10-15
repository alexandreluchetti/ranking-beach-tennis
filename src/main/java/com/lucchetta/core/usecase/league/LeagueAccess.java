package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.exception.RuleViolationException;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.LeagueRepository;
import com.lucchetta.core.usecase.player.GetOrCreateCurrentPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * Who may do what inside a league (decisions D-018 and D-020). The system administrator may act
 * as league administrator in any league, member or not.
 */
public class LeagueAccess {

    private final GetOrCreateCurrentPlayer getOrCreateCurrentPlayer;
    private final LeagueRepository leagueRepository;
    private final LeagueMemberRepository memberRepository;

    public LeagueAccess(GetOrCreateCurrentPlayer getOrCreateCurrentPlayer,
                        LeagueRepository leagueRepository,
                        LeagueMemberRepository memberRepository) {
        this.getOrCreateCurrentPlayer = getOrCreateCurrentPlayer;
        this.leagueRepository = leagueRepository;
        this.memberRepository = memberRepository;
    }

    /** The caller in a league: their player profile, the league and their membership, if any. */
    public record Context(AuthenticatedUser user, Player player, League league, LeagueMember membership) {

        public boolean member() {
            return membership != null;
        }

        public boolean canAdminister() {
            return user.admin() || (membership != null && membership.admin());
        }

        public LeagueRole role() {
            return membership == null ? null : membership.role();
        }
    }

    public Player player(AuthenticatedUser user) {
        return getOrCreateCurrentPlayer.execute(user);
    }

    public Context context(AuthenticatedUser user, UUID leagueId) {
        return context(user, leagueId, false);
    }

    /** Same as {@link #context}, locking the league until the end of the current transaction. */
    public Context lockedContext(AuthenticatedUser user, UUID leagueId) {
        return context(user, leagueId, true);
    }

    /** Members, and the system administrator, may see the league's content. */
    public Context requireMember(AuthenticatedUser user, UUID leagueId) {
        Context context = context(user, leagueId);
        if (!context.member() && !user.admin()) {
            throw new ForbiddenException();
        }
        return context;
    }

    public static void requireAdministrator(Context context) {
        if (!context.canAdminister()) {
            throw new ForbiddenException();
        }
    }

    public static void requireNotArchived(League league) {
        if (league.archived()) {
            throw new RuleViolationException("LEAGUE_ARCHIVED");
        }
    }

    public LeagueView view(Context context) {
        return new LeagueView(context.league(), memberRepository.count(context.league().id()), context.role(),
                context.canAdminister());
    }

    private Context context(AuthenticatedUser user, UUID leagueId, boolean lock) {
        Player player = getOrCreateCurrentPlayer.execute(user);
        Optional<League> league = lock ? leagueRepository.findByIdForUpdate(leagueId)
                : leagueRepository.findById(leagueId);
        League found = league.orElseThrow(() -> new NotFoundException("LEAGUE_NOT_FOUND"));
        LeagueMember membership = memberRepository.find(leagueId, player.id()).orElse(null);
        return new Context(user, player, found, membership);
    }
}
