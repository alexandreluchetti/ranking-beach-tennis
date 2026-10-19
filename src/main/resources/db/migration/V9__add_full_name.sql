-- Full name of the user (decision D-024). Null for accounts created before it until the user fills it in.
alter table app_user add column full_name varchar(100);
