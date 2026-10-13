create table app_user (
    id            uuid         primary key,
    auth_provider varchar(30)  not null,
    auth_subject  varchar(128) not null,
    email         varchar(320),
    display_name  varchar(40)  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    constraint uk_app_user_identity unique (auth_provider, auth_subject)
);
