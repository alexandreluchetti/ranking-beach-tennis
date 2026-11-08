# API overview

Every endpoint needs a Firebase ID token (`Authorization: Bearer ...`) except
`/actuator/health` and `/actuator/info`. Errors are RFC 7807 problems with a stable `code` and a
pt-BR `detail` for the user. Swagger UI (`/swagger-ui.html`, local only) has the full contracts.

Roles (decision D-018): **member** of a league; **league administrator** (role `ADMIN` in that
league); **system administrator** (`ADMIN_AUTH_SUBJECT`), who may act as league administrator
in any league without being a member.

## Me

| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/api/me` | any user | Profile (`displayName` = nickname, `fullName` or null); created on first access. |
| PATCH | `/api/me` | any user | `displayName` and/or `fullName` (D-024). |
| GET | `/api/me/statistics` | any user | Own statistics over every league, with `winRate`; no points (D-022). |
| GET | `/api/me/invitations` | any user | Own pending league invitations. |
| POST | `/api/me/invitations/{id}/accept` · `/reject` | invitee | Accepting joins the league. |
| GET | `/api/notifications` · POST `/api/notifications/{id}/read` · `/read-all` | any user | In-app notifications. |
| PUT · DELETE | `/api/me/devices[/{token}]` | any user | Push devices. |

## Leagues

| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/api/leagues` | any user | Creator becomes league administrator; 5 active leagues per creator. `scoreEntry` (D-029): `MEMBERS` (default) or `ADMINS`; `sharedMatches` (D-031): receive matches of other leagues, default `true`. |
| GET | `/api/leagues/mine` | any user | Own leagues, archived included. |
| GET | `/api/leagues?query=&page=&size=` | any user | Search by name; `archived=true` for the system administrator. |
| GET | `/api/leagues/{id}` | any user | Summary: name, description, visibility, `memberCount`/`memberLimit`, `myRole`, `canAdminister`, `canEnterScores`, `joinRequestPending`; `scoreEntry` and `sharedMatches` for members only. |
| PATCH | `/api/leagues/{id}` | league admin | Name, description, visibility, `scoreEntry`, `sharedMatches`. |
| POST | `/api/leagues/{id}/archive` · `/unarchive` | league admin · system admin | Archived leagues are read-only. |
| POST | `/api/leagues/{id}/join` | any user | Public leagues only. |
| POST | `/api/leagues/{id}/leave` | member | The last administrator cannot leave. |
| GET | `/api/leagues/{id}/members` | member | Administrators first; `fullName` only for league administrators. |
| DELETE | `/api/leagues/{id}/members/{playerId}` | league admin | Remove a member. |
| PUT | `/api/leagues/{id}/members/{playerId}/role` | league admin | `{"role": "ADMIN" \| "MEMBER"}`. |
| POST · DELETE | `/api/leagues/{id}/join-requests` · `/join-requests/mine` | non-member · requester | Request to join a private league; cancel it. |
| GET | `/api/leagues/{id}/join-requests` | league admin | Pending requests. |
| POST | `/api/leagues/{id}/join-requests/{requestId}/accept` · `/reject` | league admin | |
| POST | `/api/leagues/{id}/invitations` | league admin | `{"email"}`; always 202, never reveals whether the account exists. |
| GET · POST | `/api/leagues/{id}/invite-code` | league admin | Current code / generate a new one. |
| POST | `/api/leagues/join-by-code` | any user | `{"code"}`; private leagues too. |

## Inside a league (`/api/leagues/{leagueId}/...`, members only)

| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `seasons` · `seasons/current` · `seasons/{id}` | member | `current` answers 204 without an active season. |
| POST · PATCH | `seasons` · `seasons/{id}` | league admin | D-006, D-008, D-010 per league. `playingDays` (D-025): `["TUESDAY", "THURSDAY"]`, omitted = every day; in an active season a change applies from today on (earlier days keep the previous days). `rankingType` (D-026): `MATCH_POINTS` (default) or `DAILY_TOURNAMENT`, with `placementPoints` `{champion, runnerUp, thirdPlace, fourthPlace, quarterfinal, roundOf16, groupStage}` (default 100/70/55/45/35/25/20, D-030); both locked once the season starts. |
| GET | `seasons/current/ranking` · `seasons/{id}/ranking` | member | D-013; in a daily tournament season, D-028 with placement `points` and `stagesPlayed`, `titles`, `finals` (null for other seasons). |
| POST | `matches` | member | Registered players must be members; others as guests (D-021). League administrators only when `scoreEntry` is `ADMINS` (403 `SCORE_ENTRY_ADMINS_ONLY`, D-029). In a daily tournament season the match gives no season points (`NOT_A_TOURNAMENT_MATCH`). |
| GET | `matches` · `matches/{id}` · `matches/{id}/audit` | member | History with filters, including matches of other leagues that count here (D-031). `leagueId` is the league where the match was registered; `stageId` marks the game of a daily tournament stage. |
| PUT · POST | `matches/{id}` · `matches/{id}/void` · `matches/{id}/revert-void` | league admin | Corrections and voids, audited. Only in the league where the match was registered (404 elsewhere, D-031). Not for stage matches (`STAGE_MATCH`): use the stage endpoints. |
| GET | `players` · `players/{id}/profile` | member | Members only; profile over the league's matches. Season standings carry `tournament` (`stagesPlayed`, `titles`, `finals`) in a daily tournament season. |
| GET | `stages?seasonId=` · `stages/{id}` | member | Daily tournament stages (D-027); without `seasonId`, of the active season. The detail has players, pairs (with `placement` once finished), group standings and games (`round`: `GROUP`, `ROUND_OF_16`, `QUARTERFINAL`, `SEMIFINAL`, `THIRD_PLACE`, `FINAL`; `groupRound` for group games; `score` with `matchId`/`matchVersion`). `placement`: `CHAMPION`, `RUNNER_UP`, `THIRD`, `FOURTH`, `QUARTERFINAL`, `ROUND_OF_16`, `GROUP_STAGE` (`SEMIFINAL` only for a final drawn before D-030). |
| POST | `stages` | league admin | `{"date"?, "format", "players": [{"playerId"} \| {"guestName"}]}`: 8–64 players, even; draws pairs and groups. |
| POST · DELETE | `stages/{id}/draw` · `stages/{id}` | league admin | Draw again (`{"format"?, "players"?}`) or delete, only before the first score. |
| POST | `stages/{id}/reopen-groups` | league admin | Removes the knockout so group scores can change; only before any knockout score. |
| PUT | `stages/{id}/fixtures/{number}/score` | member (D-029) · league admin | `{"teamAGames", "teamBGames", "tiebreakTeamAPoints"?, "tiebreakTeamBPoints"?}`; correcting an existing score (league admin) takes `expectedMatchVersion` and `reason`. The next round is drawn when the current one is complete. |
| POST | `stages/{id}/fixtures/{number}/clear` | league admin | `{"reason"}`: voids the game's match. |

## Endpoints without a league (app before leagues)

`/api/seasons/**`, `/api/matches/**`, `/api/players/**` and `/api/admin/seasons|matches/**`
(system administrator) keep their contracts and act on the "Beach Tennis Arena" league
(`00000000-0000-0000-0000-000000000001`), which the caller joins on first use (D-023). They will
be removed once the app with leagues is published.

## Public

| Method | Path | Notes |
|---|---|---|
| GET | `/actuator/health` | `{"status":"UP"}` only. |
| GET | `/actuator/info` | `{"build":{"version","time"}}` only. |
