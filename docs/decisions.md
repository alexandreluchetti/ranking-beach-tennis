# Decision Log

Business and architecture decisions approved by the product owner. Anything not listed here
as **Approved** must not be implemented without discussion.

## Approved

### D-001 — Open sign-up
Any person who installs the app and creates an account automatically participates in the
ranking. No approval, invite code or allowlist.

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

## Proposed (awaiting approval)

- Display names unique, case-insensitive.
- Player deactivation for people who leave the group.
- Infrastructure and distribution choices.
