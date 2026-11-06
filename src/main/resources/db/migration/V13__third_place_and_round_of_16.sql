-- Decision D-030: third place game and round of 16 in daily tournament stages. The placement
-- points become champion, runner-up, third, fourth, quarterfinal, round of 16 and group stage.
-- Existing tables keep their points: third and fourth take the semifinal value, the round of
-- 16 takes the quarterfinal value.
alter table season drop constraint ck_season_placement_points;

update season
set placement_points = array[placement_points[1], placement_points[2], placement_points[3], placement_points[3],
                             placement_points[4], placement_points[4], placement_points[5]]
where placement_points is not null;

alter table season add constraint ck_season_placement_points check (
    (ranking_type = 'DAILY_TOURNAMENT') = (placement_points is not null)
    and (placement_points is null or cardinality(placement_points) = 7));

alter table tournament_fixture
    drop constraint ck_tournament_fixture_round,
    add constraint ck_tournament_fixture_round
        check (round in ('GROUP', 'ROUND_OF_16', 'QUARTERFINAL', 'SEMIFINAL', 'THIRD_PLACE', 'FINAL'));
