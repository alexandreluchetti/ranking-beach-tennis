-- Decision D-026: ranking type of a season. Existing seasons give points per match (D-003).
-- A daily tournament stores its points per placement (D-028): champion, runner-up, semifinal,
-- quarterfinal and group stage, in this order.
alter table season
    add column ranking_type     varchar(20) not null default 'MATCH_POINTS',
    add column placement_points integer[],
    add constraint ck_season_ranking_type check (ranking_type in ('MATCH_POINTS', 'DAILY_TOURNAMENT')),
    add constraint ck_season_placement_points check (
        (ranking_type = 'DAILY_TOURNAMENT') = (placement_points is not null)
        and (placement_points is null or cardinality(placement_points) = 5));

-- Decision D-029: who records match scores. Every existing league stays open to all members.
alter table league
    add column score_entry varchar(10) not null default 'MEMBERS',
    add constraint ck_league_score_entry check (score_entry in ('MEMBERS', 'ADMINS'));
