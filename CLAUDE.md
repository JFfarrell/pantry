# CLAUDE.md

## Git workflow

The user runs all **mutating** git commands themselves — `add`, `commit`, `push`, `branch`, `checkout`, `merge`, `rebase`, `reset`, etc. Never run these on the user's behalf; if a change needs to be committed, stage it in the working tree (or leave it staged) and ask the user to commit.

**Read-only** git commands (`git status`, `git diff`, `git log`, `git reflog`) are fine for Claude to run freely. This distinction matters here specifically: the `telescoping-sdd` plugin's own workflow relies on read-only git introspection internally — e.g. diffing `tasks.md` against the commit of its last `--approve tasks` stamp to decide whether a completion is a pure task-tick or a substantive edit, and classifying a stale content hash's origin (edit vs. `git pull`/`merge`/branch-switch) via `git log`/`git reflog`. Restricting Claude to zero git commands of any kind would degrade that machinery to its conservative fallback (`ambiguous` source, plain `--approve` instead of `--task-tick`) rather than break it, but there's no need to go that far — only commits/pushes/branch changes are reserved for the user.
