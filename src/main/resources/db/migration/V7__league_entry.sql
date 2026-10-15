-- Joining a private league (decision D-019): requests answered by league administrators and
-- in-app invitations answered by the invitee. The invite code lives in league.invite_code (V6).

create table league_join_request (
    id         uuid        primary key,
    league_id  uuid        not null references league (id),
    player_id  uuid        not null references app_user (id),
    status     varchar(10) not null,
    created_at timestamptz not null,
    decided_by uuid        references app_user (id),
    decided_at timestamptz,
    constraint ck_join_request_status check (status in ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    constraint ck_join_request_decision check ((status = 'PENDING') = (decided_at is null))
);

-- At most one pending request per user and league.
create unique index ux_join_request_pending on league_join_request (league_id, player_id)
    where status = 'PENDING';
create index ix_join_request_player on league_join_request (player_id) where status = 'PENDING';

create table league_invitation (
    id         uuid        primary key,
    league_id  uuid        not null references league (id),
    player_id  uuid        not null references app_user (id), -- the invitee
    invited_by uuid        not null references app_user (id),
    status     varchar(10) not null,
    created_at timestamptz not null,
    decided_at timestamptz,
    constraint ck_invitation_status check (status in ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    constraint ck_invitation_decision check ((status = 'PENDING') = (decided_at is null))
);

create unique index ux_invitation_pending on league_invitation (league_id, player_id) where status = 'PENDING';
create index ix_invitation_player on league_invitation (player_id) where status = 'PENDING';

-- Invitations are looked up by the exact e-mail typed by the league administrator.
create index ix_app_user_email on app_user (lower(email));

-- League notifications point to the league and, when the recipient must act, to the request or
-- invitation.
alter table notification
    add column league_id       uuid references league (id),
    add column join_request_id uuid references league_join_request (id),
    add column invitation_id   uuid references league_invitation (id),
    drop constraint ck_notification_type,
    add constraint ck_notification_type check (type in
        ('MATCH_REGISTERED', 'MATCH_CORRECTED', 'MATCH_VOIDED', 'MATCH_VOID_REVERTED',
         'LEAGUE_JOIN_REQUESTED', 'LEAGUE_JOIN_REQUEST_ACCEPTED', 'LEAGUE_JOIN_REQUEST_REJECTED',
         'LEAGUE_INVITATION_RECEIVED'));
