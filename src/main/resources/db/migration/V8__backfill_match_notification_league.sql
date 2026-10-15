-- Match notifications created before leagues (V6) have no league: take it from their match, so the
-- app opens them in the right league.
update notification n
set league_id = m.league_id
from match m
where n.match_id = m.id
  and n.league_id is null;
