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

## Proposed (awaiting approval)

- Seasons must not overlap (at most one active season).
- Display names unique, case-insensitive.
- Matches dated in the future are rejected.
- Tie-break order for the ranking (must be revisited now that games count as points).
- Offline conflict strategy (probable-duplicate detection with user confirmation).
- Notification recipients on corrections (include removed participants?).
- Player deactivation for people who leave the group.
- Infrastructure and distribution choices.
