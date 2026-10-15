# Decision Log

Business and architecture decisions approved by the product owner. Anything not listed here
as **Approved** must not be implemented without discussion.

## Approved

### D-001 — Open sign-up (superseded by D-017)
Any person can create an account. ~~Every account automatically participates in the ranking.~~
Since leagues (D-017), an account takes part in the rankings of the leagues it is a member of;
a new account belongs to no league.

### D-002 — Valid final scores
| Format | Valid final scores (winner–loser) |
|---|---|
| `STANDARD` (to 6 games) | 6–0, 6–1, 6–2, 6–3, 6–4, 7–5, 7–6 (tie-break) |
| `SHORT` (to 4 games) | 4–0, 4–1, 4–2, 5–3, 5–4 (tie-break) |

- 6–5 (standard) and 4–3 (short) are not final scores.
- A tie-break at 6–6 / 4–4 is recorded as 7–6 / 5–4: **the tie-break counts as one game** won
  by the tie-break winner.
- Tie-break points may be stored for history only; they never affect points or statistics.

### D-003 — Ranking points (replaces the provisional rule of the original requirements)
For each registered player in an eligible match:

```
points = games won by the player's team + (3 if the team won the match, otherwise 0)
```

| Score | Winners | Losers |
|---|---|---|
| 6–4 | 9 | 4 |
| 7–5 | 10 | 5 |
| 7–6 (TB) | 10 | 6 |
| 6–0 | 9 | 0 |
| 4–2 (short) | 7 | 2 |
| 5–4 (TB, short) | 8 | 4 |

- No other bonuses, multipliers or criteria.
- Scoring logic lives in a single isolated component.

### D-004 — Guests
If at least one of the four participants is a guest, the match is saved (after a warning) but
**no participant receives any points** (neither games nor bonus). The match still counts for
statistics (matches played, wins, losses, game difference). Guest status is never changed
retroactively.

### D-005 — Game difference
Computed from the recorded final score, tie-break counted as one game (7–6 → +1 / −1).

### D-006 — Seasons
- Start and end dates are **inclusive**, evaluated in the `America/Sao_Paulo` time zone.
- A closed season **cannot be reopened**.
- While a season is active, the admin may change its end date (extend or close early).

### D-007 — Points are derived, never accumulated
Only matches are stored. Rankings, points and statistics are computed at query time from
valid matches within the season period. Corrections, voids, void reversals and season date
changes are reflected automatically and points can never be applied twice.

### D-008 — Seasons never overlap
At most one season covers any given day. Enforced by the database (exclusion constraint) and
by the use cases.

### D-009 — Firebase project
Firebase project ID: `ranking-beach-tennis`.

### D-010 — Season management rules
- Season name: 2–60 characters.
- A season's end date can never be set before today (creation or change), so already-played
  days are never silently removed from a season. Closing early means "ends today".
- A season may be created with a start date in the past (e.g. created a few days after it
  started); matches already played in that period will count.
- Active season: name and end date may change; start date is locked.
  Planned season: name and both dates may change. Closed season: no changes.
- Seasons cannot be deleted.

### D-011 — Match date
Matches dated more than 10 minutes in the future are rejected (tolerance for phone clock drift).
There is no lower limit.

### D-012 — Possible duplicates require confirmation
When registering a match, if a valid match already exists on the same day (Brasília) with the
same two teams, the server answers "possible duplicate" and the user must explicitly confirm
that it is a different match. Resending the same match id is idempotent and is not a duplicate.

### D-013 — Ranking order and tie-breaks
1. More points.
2. More wins.
3. Better game difference.
4. **Fewer** matches played (same result with fewer matches ranks higher).
5. Still tied: shared position.

### D-014 — Late registrations into closed seasons
A match dated inside a closed season still counts for that season, even if registered or
synced after the season ended. A closed season's final ranking may therefore change.

### D-015 — Notifying removed participants
When a correction removes a registered player from a match, that player is notified too, in
addition to the current participants (implemented with notifications, stage 6).

### D-016 — Pending (offline) matches
A match saved on the phone and not yet sent can be edited or discarded on that phone, only by
the user who registered it. Once the server has it, only the administrator can change it.

### D-017 — Leagues
- A league is a space where its members compete in that league's own ranking. Seasons,
  matches, rankings and statistics belong to exactly one league and are independent from other
  leagues. A user may be a member of several leagues at the same time (one ranking per league).
- Any signed-in user can create a league; the creator becomes a league administrator.
- League data: name 3–60 characters, **unique** (case-insensitive); optional description up to
  500 characters; visibility `PUBLIC` or `PRIVATE`. League administrators may edit all of it,
  including the visibility. No picture for now.
- Leagues are never deleted, only **archived** (read-only, hidden from search). Any league
  administrator or the system administrator can archive; only the system administrator can
  unarchive.
- A user may have at most 5 non-archived leagues they created; the system administrator has
  no limit.
- A league has at most **50 members**, administrators included. The member count is visible to
  every user as `n/50`. A full league accepts nobody (direct join, invite code, accepted
  request or accepted invitation are rejected).
- Rules that were global now apply per league: one season at a time and no overlapping seasons
  (D-006, D-008, D-010), possible duplicates (D-012), ranking order (D-013).

### D-018 — League roles
- League administrator: any member can be one. Inside the league they do what the single
  administrator did before (create and edit seasons; correct, void and restore matches), plus:
  accept or reject join requests, invite users, promote and revoke league administrators,
  edit and archive the league, remove members.
- A league always has at least one administrator: the last one can be neither revoked nor
  leave. The creator has no special protection. An administrator may revoke themselves unless
  they are the last one.
- System administrator (`ADMIN_AUTH_SUBJECT`, a single person) is a different role: they may
  act as league administrator in **any** league, member or not, and archive or unarchive
  leagues. Otherwise they are a regular user. Acting in a league without being a member does
  not count towards its member limit.

### D-019 — Joining and leaving a league
- Public league: any user joins directly.
- Private league: a user joins by
  - **request**: any league administrator accepts or rejects it; the requester may cancel it;
    at most one pending request per user and league; league administrators are notified of
    the request and the requester of the answer;
  - **invite code / link**: league administrators share it; whoever uses it joins directly;
    an administrator can regenerate it, invalidating the previous one;
  - **in-app invitation**: a league administrator invites a specific registered user, who
    receives an in-app notification and accepts or rejects it there. No e-mail is sent.
- A member may leave; a league administrator may remove a member. Their matches remain valid.
  They may come back later following the league's entry rules.

### D-020 — League visibility
- Non-members (public or private league alike) see only the league's name, description,
  visibility and member count, to decide whether to join or request. Ranking, seasons,
  matches, members and player profiles are visible to members only (and to the system
  administrator).
- Users who are not members of a league are not visible inside it (member lists, player
  pickers).

### D-021 — Matches and rankings inside a league
- A match is registered inside the league the user is in, and belongs to it. The registering
  user must be a member of that league.
- Every registered player in a match must be a member of the league. Guest positions (a typed
  name, D-004) are still allowed; a match with a guest gives no points to anyone but counts for
  statistics.
- Season ranking members: the league's members up to the season's end date, plus anyone who
  played in it. A member who leaves no longer appears in later seasons but stays in the
  seasons they played.
- A player's profile and statistics inside a league cover only that league's matches and are
  visible only to that league's members.
- Notifications about matches go to the participants and to that league's administrators
  (instead of the system administrator).

### D-022 — Global statistics of the user
Each user can see their own global statistics, summed over all leagues: matches played, wins,
losses, game difference and win rate (`Aproveitamento`). No points (each league has its own
ranking). Nobody can see another user's global statistics.

### D-023 — Migration of the existing group
All existing users, seasons and matches move to a new **public** league named
**"Beach Tennis Arena"**, whose league administrator is the current system administrator.
The current endpoints keep working, bound to this league, until the app with leagues is
published; then they are removed.

## Technical defaults (easy to change)

- Display name: 2–40 characters, whitespace normalized. Initial value: name from the login
  provider → e-mail local part → "Jogador". Not unique (uniqueness is still a proposal).
- Administrator identified by Firebase UID (`ADMIN_AUTH_SUBJECT`); roles never come from token
  claims.
- E-mail verification is not required.
- Guest name: optional, up to 40 characters. Any number of the four positions may be guests.
- Tie-break points: optional even for 7–6 / 5–4; when given, both values are required and the
  tie-break winner must be the match winner. No other limit (e.g. "first to 7") is enforced.
- The user registering a match does not need to be one of its participants.
- Match time is kept with whole-second precision.
- Shared positions skip the following ones (1, 1, 3). Inside a shared position, players are
  listed alphabetically (display only, not a tie-break).
- Ranking members of a season: every player registered up to the season's end date, plus anyone
  who played in it. The active season therefore lists everyone; a past season does not list
  people who joined after it ended. The administrator is a player like any other.
- Career statistics include every valid match (inside or outside seasons, with or without
  guests); career points are the sum of points from matches that counted.
- History: 20 matches per page by default, at most 100.
- Corrections: justification optional; voids and void reversals: justification required (up to
  500 characters). A voided match must be restored before it can be corrected.
- A correction is a full replacement of date, format, score and participants and applies the
  same rules as registration (except the possible-duplicate check). It must state the match
  version it was based on; if the match changed meanwhile, it is rejected.
- Every authenticated user can read a match's audit trail (transparency for participants).
- If the administrator changed a match before its author's offline resend arrived, the resend
  returns the current state instead of failing.
- Notifications: every recipient (registered participants, players removed by a correction and
  the administrator) gets an in-app notification; the person who performed the action gets no
  push about it. Notification text is written in pt-BR by the backend so in-app and push match.
- Push is off until a Firebase service account key is configured; push to iPhones additionally
  needs the Apple Developer account and an APNs key in Firebase.
- Rankings and statistics are computed in Java from the matches, so the scoring formula exists
  in exactly one place (`GamesPlusWinBonusScoringPolicy`).
- In-app league invitation: the league administrator types the invitee's **exact e-mail**. The
  answer is always "invitation sent", whether or not an account has that e-mail, so the app
  never reveals who uses it.
- Win rate (`Aproveitamento`): wins ÷ matches played × 100, rounded to an integer (7 of 11 →
  64%); empty (not 0%) when there are no matches.

## Proposed (awaiting approval)

- Display names unique, case-insensitive.
- Player deactivation for people who leave the group.
- Infrastructure and distribution choices.
