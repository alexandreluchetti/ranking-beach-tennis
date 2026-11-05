package com.lucchetta.core.usecase.tournament;

import com.lucchetta.core.domain.AuthenticatedUser;
import com.lucchetta.core.domain.tournament.TournamentStage;
import com.lucchetta.core.exception.NotFoundException;
import com.lucchetta.core.port.TournamentStageRepository;
import com.lucchetta.core.usecase.league.LeagueAccess;
import com.lucchetta.core.usecase.season.QuerySeasons;
import com.lucchetta.core.usecase.season.SeasonView;

import java.util.List;
import java.util.UUID;

/**
 * Stages of a daily tournament season (D-027), for the league's members.
 */
public class QueryStages {

    private final LeagueAccess leagueAccess;
    private final QuerySeasons querySeasons;
    private final TournamentStageRepository stageRepository;
    private final StageViewAssembler viewAssembler;

    public QueryStages(LeagueAccess leagueAccess,
                       QuerySeasons querySeasons,
                       TournamentStageRepository stageRepository,
                       StageViewAssembler viewAssembler) {
        this.leagueAccess = leagueAccess;
        this.querySeasons = querySeasons;
        this.stageRepository = stageRepository;
        this.viewAssembler = viewAssembler;
    }

    /**
     * @param seasonId the season, or null for the active one (no stages when there is none)
     */
    public List<TournamentStage> list(AuthenticatedUser user, UUID leagueId, UUID seasonId) {
        leagueAccess.requireMember(user, leagueId);
        UUID season = seasonId != null ? querySeasons.byId(leagueId, seasonId).season().id()
                : querySeasons.current(leagueId).map(SeasonView::season).map(s -> s.id()).orElse(null);
        return season == null ? List.of() : stageRepository.findBySeason(season);
    }

    public StageView get(AuthenticatedUser user, UUID leagueId, UUID stageId) {
        LeagueAccess.Context context = leagueAccess.requireMember(user, leagueId);
        return viewAssembler.view(requireInLeague(stageRepository, leagueId, stageId, false), context);
    }

    static TournamentStage requireInLeague(TournamentStageRepository repository, UUID leagueId, UUID stageId,
                                           boolean lock) {
        return (lock ? repository.findByIdForUpdate(stageId) : repository.findById(stageId))
                .filter(stage -> stage.leagueId().equals(leagueId))
                .orElseThrow(() -> new NotFoundException("STAGE_NOT_FOUND"));
    }
}
