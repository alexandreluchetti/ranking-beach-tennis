-- Decision D-025: only matches played on the season's playing days (ISO day of the week,
-- 1 = Monday ... 7 = Sunday, Brasília) count. Existing seasons keep counting every day.
alter table season
    add column playing_days smallint[] not null default '{1,2,3,4,5,6,7}',
    add constraint ck_season_playing_days
        check (cardinality(playing_days) between 1 and 7 and playing_days <@ '{1,2,3,4,5,6,7}'::smallint[]);

-- Playing days changed while the season was active apply from that day on: the earlier days
-- keep the playing days they had, up to last_day (inclusive).
create table season_past_playing_days (
    season_id    uuid       not null references season (id),
    last_day     date       not null,
    playing_days smallint[] not null,
    primary key (season_id, last_day),
    constraint ck_season_past_playing_days
        check (cardinality(playing_days) between 1 and 7 and playing_days <@ '{1,2,3,4,5,6,7}'::smallint[])
);
