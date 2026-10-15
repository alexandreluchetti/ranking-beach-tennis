package com.lucchetta.entrypoint.rest.league;

import com.lucchetta.core.domain.league.LeagueRole;
import com.lucchetta.core.usecase.league.CreateLeague;
import com.lucchetta.core.usecase.league.LeagueArchiving;
import com.lucchetta.core.usecase.league.LeagueMembership;
import com.lucchetta.core.usecase.league.MemberView;
import com.lucchetta.core.usecase.league.QueryLeagues;
import com.lucchetta.core.usecase.league.UpdateLeague;
import com.lucchetta.entrypoint.rest.league.LeagueRequests.ChangeRoleRequest;
import com.lucchetta.entrypoint.rest.league.LeagueRequests.CreateLeagueRequest;
import com.lucchetta.entrypoint.rest.league.LeagueRequests.UpdateLeagueRequest;
import com.lucchetta.entrypoint.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leagues")
public class LeagueController {

    public record LeaguePageResponse(List<LeagueResponse> items, int page, int size, boolean hasNext) {
    }

    public record MemberResponse(UUID playerId, String displayName, LeagueRole role, Instant joinedAt) {

        static MemberResponse from(MemberView view) {
            return new MemberResponse(view.member().playerId(), view.displayName(), view.member().role(),
                    view.member().joinedAt());
        }
    }

    private final CurrentUserProvider currentUserProvider;
    private final CreateLeague createLeague;
    private final UpdateLeague updateLeague;
    private final LeagueArchiving leagueArchiving;
    private final QueryLeagues queryLeagues;
    private final LeagueMembership leagueMembership;

    public LeagueController(CurrentUserProvider currentUserProvider,
                            CreateLeague createLeague,
                            UpdateLeague updateLeague,
                            LeagueArchiving leagueArchiving,
                            QueryLeagues queryLeagues,
                            LeagueMembership leagueMembership) {
        this.currentUserProvider = currentUserProvider;
        this.createLeague = createLeague;
        this.updateLeague = updateLeague;
        this.leagueArchiving = leagueArchiving;
        this.queryLeagues = queryLeagues;
        this.leagueMembership = leagueMembership;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creates a league; the caller becomes its administrator")
    public LeagueResponse create(@RequestBody CreateLeagueRequest request) {
        return LeagueResponse.from(createLeague.execute(currentUserProvider.get(),
                new CreateLeague.Command(request.name(), request.description(), request.visibility())));
    }

    @GetMapping("/mine")
    @Operation(summary = "Leagues the caller is a member of (archived included)")
    public List<LeagueResponse> mine() {
        return queryLeagues.mine(currentUserProvider.get()).stream().map(LeagueResponse::from).toList();
    }

    @GetMapping
    @Operation(summary = "Searches leagues by name; archived ones only for the system administrator")
    public LeaguePageResponse search(@RequestParam(required = false) String query,
                                     @RequestParam(defaultValue = "false") boolean archived,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "" + QueryLeagues.DEFAULT_PAGE_SIZE) int size) {
        QueryLeagues.Page result = queryLeagues.search(currentUserProvider.get(), query, archived, page, size);
        return new LeaguePageResponse(result.items().stream().map(LeagueResponse::from).toList(),
                result.page(), result.size(), result.hasNext());
    }

    @GetMapping("/{id}")
    @Operation(summary = "League summary, visible to every user")
    public LeagueResponse get(@PathVariable UUID id) {
        return LeagueResponse.from(queryLeagues.get(currentUserProvider.get(), id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Changes name, description or visibility (league administrator)")
    public LeagueResponse update(@PathVariable UUID id, @RequestBody UpdateLeagueRequest request) {
        return LeagueResponse.from(updateLeague.execute(currentUserProvider.get(), id,
                new UpdateLeague.Command(request.name(), request.description(), request.visibility())));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archives the league (league administrator or system administrator)")
    public LeagueResponse archive(@PathVariable UUID id) {
        return LeagueResponse.from(leagueArchiving.archive(currentUserProvider.get(), id));
    }

    @PostMapping("/{id}/unarchive")
    @Operation(summary = "Unarchives the league (system administrator)")
    public LeagueResponse unarchive(@PathVariable UUID id) {
        return LeagueResponse.from(leagueArchiving.unarchive(currentUserProvider.get(), id));
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "Joins a public league")
    public LeagueResponse join(@PathVariable UUID id) {
        return LeagueResponse.from(leagueMembership.join(currentUserProvider.get(), id));
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Leaves the league")
    public void leave(@PathVariable UUID id) {
        leagueMembership.leave(currentUserProvider.get(), id);
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "Members of the league (members only)")
    public List<MemberResponse> members(@PathVariable UUID id) {
        return leagueMembership.members(currentUserProvider.get(), id).stream().map(MemberResponse::from).toList();
    }

    @DeleteMapping("/{id}/members/{playerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Removes a member (league administrator)")
    public void removeMember(@PathVariable UUID id, @PathVariable UUID playerId) {
        leagueMembership.remove(currentUserProvider.get(), id, playerId);
    }

    @PutMapping("/{id}/members/{playerId}/role")
    @Operation(summary = "Promotes a member to league administrator or revokes it (league administrator)")
    public MemberResponse changeRole(@PathVariable UUID id, @PathVariable UUID playerId,
                                     @RequestBody ChangeRoleRequest request) {
        return MemberResponse.from(leagueMembership.changeRole(currentUserProvider.get(), id, playerId,
                request.role()));
    }
}
