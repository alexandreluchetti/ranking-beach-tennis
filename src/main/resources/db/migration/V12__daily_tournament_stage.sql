-- Decision D-027: stages of a daily tournament season. One stage per playing day; the players
-- present, the pairs and groups drawn and the games. Each game score is a match of the league
-- (match.stage_id), so points and statistics stay derived from matches (D-007).
create table tournament_stage (
    id         uuid        primary key,
    league_id  uuid        not null references league (id),
    season_id  uuid        not null references season (id),
    stage_date date        not null,
    format     varchar(10) not null,
    created_by uuid        not null references app_user (id),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version    integer     not null default 0,
    constraint uk_tournament_stage_day unique (season_id, stage_date),
    constraint ck_tournament_stage_format check (format in ('STANDARD', 'SHORT'))
);

create index ix_tournament_stage_league on tournament_stage (league_id, stage_date);

-- A member, or a guest by name (D-004).
create table tournament_stage_player (
    stage_id   uuid        not null references tournament_stage (id) on delete cascade,
    position   smallint    not null,
    player_id  uuid        references app_user (id),
    guest_name varchar(40),
    primary key (stage_id, position),
    constraint ck_tournament_stage_player check ((player_id is null) <> (guest_name is null))
);

create unique index ux_tournament_stage_player on tournament_stage_player (stage_id, player_id)
    where player_id is not null;

create table tournament_pair (
    stage_id        uuid     not null references tournament_stage (id) on delete cascade,
    pair_number     smallint not null,
    group_number    smallint not null,
    first_position  smallint not null,
    second_position smallint not null,
    primary key (stage_id, pair_number)
);

alter table match add column stage_id uuid references tournament_stage (id);
create index ix_match_stage on match (stage_id) where stage_id is not null;

-- Pair A plays as team A of the match.
create table tournament_fixture (
    stage_id       uuid        not null references tournament_stage (id) on delete cascade,
    fixture_number smallint    not null,
    round          varchar(15) not null,
    group_number   smallint,
    pair_a         smallint    not null,
    pair_b         smallint    not null,
    match_id       uuid        references match (id),
    primary key (stage_id, fixture_number),
    constraint ck_tournament_fixture_round
        check (round in ('GROUP', 'QUARTERFINAL', 'SEMIFINAL', 'FINAL')),
    constraint ck_tournament_fixture_group check ((round = 'GROUP') = (group_number is not null))
);

create unique index ux_tournament_fixture_match on tournament_fixture (match_id) where match_id is not null;
