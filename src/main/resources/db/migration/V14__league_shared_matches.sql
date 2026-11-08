-- Decision D-031: a match whose four players are all members of another league also counts in
-- that league, when its season is of points per match. Each league may turn it off; every
-- existing league receives them.
alter table league add column shared_matches boolean not null default true;
