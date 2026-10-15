package com.lucchetta.core.usecase.league;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.Player;
import com.lucchetta.core.domain.league.League;
import com.lucchetta.core.domain.league.LeagueMember;
import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.exception.ForbiddenException;
import com.lucchetta.core.port.LeagueMemberRepository;
import com.lucchetta.core.port.LeagueRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The leagues screen: the caller's leagues and the search over all leagues. Anyone may see a
 * league's summary (name, description, visibility, member count); its content is for members
 * only (decision D-020).
 */
public class QueryLeagues {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

    public record Page(List<LeagueView> items, int page, int size, boolean hasNext) {
    }

    private final LeagueAccess leagueAccess;
    private final LeagueRepository leagueRepository;
    private final LeagueMemberRepository memberRepository;

    public QueryLeagues(LeagueAccess leagueAccess,
                        LeagueRepository leagueRepository,
                        LeagueMemberRepository memberRepository) {
        this.leagueAccess = leagueAccess;
        this.leagueRepository = leagueRepository;
        this.memberRepository = memberRepository;
    }

    /** Leagues the caller is a member of, archived ones included, ordered by name. */
    public List<LeagueView> mine(AuthenticatedUser user) {
        Player player = leagueAccess.player(user);
        Map<UUID, LeagueRole> roles = rolesOf(player);
        List<League> leagues = leagueRepository.findAllByIds(roles.keySet()).stream()
                .sorted(Comparator.comparing(league -> league.name().toLowerCase()))
                .toList();
        return views(user, player, leagues, roles);
    }

    /**
     * Leagues whose name contains {@code query}. Archived leagues are hidden; only the system
     * administrator may list them (to unarchive).
     */
    public Page search(AuthenticatedUser user, String query, boolean archived, int page, int size) {
        if (archived && !user.admin()) {
            throw new ForbiddenException();
        }
        Player player = leagueAccess.player(user);
        Map<UUID, LeagueRole> roles = rolesOf(player);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        List<League> found = leagueRepository.search(query, archived, safePage * safeSize, safeSize + 1);
        List<League> pageItems = found.subList(0, Math.min(found.size(), safeSize));
        return new Page(views(user, player, pageItems, roles), safePage, safeSize, found.size() > safeSize);
    }

    public LeagueView get(AuthenticatedUser user, UUID leagueId) {
        return leagueAccess.view(leagueAccess.context(user, leagueId));
    }

    private Map<UUID, LeagueRole> rolesOf(Player player) {
        return memberRepository.findByPlayer(player.id()).stream()
                .collect(Collectors.toMap(LeagueMember::leagueId, LeagueMember::role));
    }

    private List<LeagueView> views(AuthenticatedUser user, Player player, List<League> leagues,
                                   Map<UUID, LeagueRole> roles) {
        Map<UUID, Integer> counts = memberRepository.countByLeagues(leagues.stream().map(League::id).toList());
        return leagues.stream()
                .map(league -> {
                    LeagueRole role = roles.get(league.id());
                    return new LeagueView(league, counts.getOrDefault(league.id(), 0), role,
                            user.admin() || role == LeagueRole.ADMIN);
                })
                .toList();
    }
}
