-- Current void information (null while the match is valid). History lives in match_audit.
alter table match
    add column void_reason varchar(500),
    add column voided_by   uuid references app_user (id),
    add column voided_at   timestamptz,
    add constraint ck_match_void_info check (
        (status = 'VOIDED') = (void_reason is not null and voided_by is not null and voided_at is not null));

create table match_audit (
    id           bigint       generated always as identity primary key,
    match_id     uuid         not null references match (id),
    action       varchar(20)  not null,
    performed_by uuid         not null references app_user (id),
    performed_at timestamptz  not null,
    reason       varchar(500),
    before       jsonb        not null,
    after        jsonb        not null,
    constraint ck_match_audit_action check (action in ('CORRECTED', 'VOIDED', 'VOID_REVERTED')),
    constraint ck_match_audit_reason check (action = 'CORRECTED' or reason is not null)
);

create index ix_match_audit_match on match_audit (match_id);
