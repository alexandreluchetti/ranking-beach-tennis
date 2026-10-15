-- Leagues (decisions D-017 to D-023). Seasons and matches now belong to a league; everything
-- that existed before moves to the "Beach Tennis Arena" league (D-023).

-- Equality on uuid inside an exclusion constraint (seasons never overlap within a league).
create extension if not exists btree_gist;

create table league (
    id          uuid         primary key,
    name        varchar(60)  not null,
    description varchar(500),
    visibility  varchar(10)  not null,
    invite_code varchar(32),
    created_by  uuid         references app_user (id), -- null only for the migrated league without users
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    archived_at timestamptz,
    archived_by uuid         references app_user (id),
    version     integer      not null default 0,
    constraint ck_league_visibility check (visibility in ('PUBLIC', 'PRIVATE')),
    constraint ck_league_archive_info check ((archived_at is null) = (archived_by is null)),
    constraint uk_league_invite_code unique (invite_code)
);

create unique index ux_league_name on league (lower(name));
create index ix_league_created_by on league (created_by) where archived_at is null;

create table league_member (
    league_id uuid        not null references league (id),
    player_id uuid        not null references app_user (id),
    role      varchar(10) not null,
    joined_at timestamptz not null,
    primary key (league_id, player_id),
    constraint ck_league_member_role check (role in ('ADMIN', 'MEMBER'))
);

create index ix_league_member_player on league_member (player_id);

-- The existing group. Fixed id: the endpoints without a league are bound to it until the app
-- with leagues is published.
insert into league (id, name, visibility, created_by, created_at, updated_at)
values ('00000000-0000-0000-0000-000000000001', 'Beach Tennis Arena', 'PUBLIC',
        -- League administrator: the system administrator (ADMIN_AUTH_SUBJECT); without one, the
        -- creator of the first season; without seasons, the oldest user.
        coalesce(
            (select id from app_user where auth_subject = '${admin_auth_subject}'),
            (select created_by from season order by created_at limit 1),
            (select id from app_user order by created_at limit 1)),
        now(), now());

-- Every existing user is a member since their sign-up, so past season rankings do not change.
insert into league_member (league_id, player_id, role, joined_at)
select l.id, u.id, case when u.id = l.created_by then 'ADMIN' else 'MEMBER' end, u.created_at
from app_user u
cross join league l
where l.id = '00000000-0000-0000-0000-000000000001';

alter table season add column league_id uuid references league (id);
update season set league_id = '00000000-0000-0000-0000-000000000001';
alter table season
    alter column league_id set not null,
    drop constraint ex_season_no_overlap,
    add constraint ex_season_no_overlap
        exclude using gist (league_id with =, daterange(start_date, end_date, '[]') with &&);

alter table match add column league_id uuid references league (id);
update match set league_id = '00000000-0000-0000-0000-000000000001';
alter table match alter column league_id set not null;

create index ix_match_league_played_at on match (league_id, played_at);
