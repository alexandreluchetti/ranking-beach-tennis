# Contributing

## Branches

| Branch | Purpose |
|---|---|
| `main` | Production. Every release is tagged `vX.Y.Z` (version from `pom.xml`). |
| `dev` | Integration branch, created from `main`. Changes arrive only through pull requests. |
| `feat/<name>`, `fix/<name>`, `refactor/<name>`, `chore/<name>` | Short-lived work branches, created from `dev`. |

Work branch names are lowercase kebab-case in English, e.g. `feat/player-deactivation`,
`fix/duplicate-match-check`, `chore/git-workflow`.

| Prefix | Use for |
|---|---|
| `feat/` | New behavior visible to players, the administrator or API clients |
| `fix/` | Bug fixes |
| `refactor/` | Code changes without behavior changes |
| `chore/` | Build, tooling, infrastructure, dependencies, documentation |

## Flow

1. Update `dev` and branch from it:

   ```bash
   git switch dev
   git pull
   git switch -c feat/<name>
   ```

2. Commit (messages in English, imperative mood) and run the checks (unit and integration
   tests; Docker must be running):

   ```bash
   ./mvnw verify
   ```

3. Push and open a pull request into `dev`:

   ```bash
   git push -u origin feat/<name>
   gh pr create --base dev --title "<title>" --body "<description>"
   ```

4. After approval, merge the pull request and delete the work branch (on GitHub and locally):

   ```bash
   git switch dev
   git pull
   git branch -d feat/<name>
   ```

5. Release: raise `<version>` in `pom.xml` (through a work branch into `dev`), then open a pull
   request from `dev` into `main`. After it is merged, tag `main` and deploy it to the server
   (see `docs/deployment.md`):

   ```bash
   git switch main
   git pull
   git tag -a vX.Y.Z -m "Version X.Y.Z"
   git push origin vX.Y.Z
   ```

6. Sync `dev` with `main` by hand after every merge into `main` (merge commit; no content
   changes), then check that `git log origin/dev..origin/main` is empty and update your local
   `dev`:

   ```bash
   gh pr create --base dev --head main --title "Sync dev with main" --body "Sync after release."
   gh pr merge --merge <number>
   git switch dev
   git pull
   ```

Never commit directly to `dev` or `main`. `main` only accepts pull requests from `dev`: no check
enforces it any more, so confirm the source branch before merging a pull request into `main`.
The server only runs `main` (or a release tag), never `dev` or a work branch.

## Repository settings (GitHub)

Branch protection needs a public repository or GitHub Pro.

- Ruleset `protected-branches`, active, targets `main` (default branch) and `dev` (pattern
  `dev`): restrict deletions, block force pushes, require a pull request before merging with
  0 approvals (nobody can approve their own pull request).
- Ruleset `main-only-from-dev`, active, target `main` (default branch): restrict deletions and
  block force pushes. Its required status check `source-branch-is-dev` was removed on
  2026-11-06, when the account's GitHub Actions were locked by a billing issue.
- "Automatically delete head branches" on, so merged work branches are removed from GitHub.
- GitHub Actions: the workflows `main-only-from-dev` and `sync-dev-with-main` are disabled
  (`gh workflow enable <file>` turns them back on). If they are enabled again, `sync-dev-with-main`
  needs Settings → Actions → General → Workflow permissions: "Allow GitHub Actions to create and
  approve pull requests".
