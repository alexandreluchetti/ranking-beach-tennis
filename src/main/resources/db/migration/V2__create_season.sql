create table season (
    id         uuid        primary key,
    name       varchar(60) not null,
    start_date date        not null,
    end_date   date        not null,
    created_by uuid        not null references app_user (id),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_season_period check (end_date >= start_date),
    -- Decision D-008: at most one season covers any given day (inclusive bounds).
    constraint ex_season_no_overlap exclude using gist (daterange(start_date, end_date, '[]') with &&)
);

create table season_audit (
    id           bigint      generated always as identity primary key,
    season_id    uuid        not null references season (id),
    action       varchar(20) not null,
    performed_by uuid        not null references app_user (id),
    performed_at timestamptz not null,
    before       jsonb,
    after        jsonb       not null
);

create index ix_season_audit_season on season_audit (season_id);
