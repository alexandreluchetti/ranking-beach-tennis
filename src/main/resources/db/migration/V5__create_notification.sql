create table notification (
    id           uuid          primary key,
    recipient_id uuid          not null references app_user (id),
    type         varchar(30)   not null,
    match_id     uuid          references match (id),
    title        varchar(120)  not null,
    body         varchar(2000) not null,
    created_at   timestamptz   not null,
    read_at      timestamptz,
    -- One notification per event and recipient: retries and repeated syncs never notify twice.
    dedup_key    varchar(200)  not null,
    constraint uk_notification_dedup unique (dedup_key),
    constraint ck_notification_type check (type in
        ('MATCH_REGISTERED', 'MATCH_CORRECTED', 'MATCH_VOIDED', 'MATCH_VOID_REVERTED'))
);

create index ix_notification_recipient on notification (recipient_id, created_at desc);

create table device_token (
    token      varchar(512) primary key,
    player_id  uuid         not null references app_user (id),
    platform   varchar(10)  not null,
    updated_at timestamptz  not null,
    constraint ck_device_platform check (platform in ('ANDROID', 'IOS'))
);

create index ix_device_token_player on device_token (player_id);
