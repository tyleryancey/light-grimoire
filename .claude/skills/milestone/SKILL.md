---
name: milestone
description: Show where the build stands and propose this session's plan from the checkbox roadmap — the next unchecked tasks, gates that must be green first, and the agents to involve.
disable-model-invocation: true
allowed-tools: Read, Bash(git log*), Bash(git status*), Bash(./gradlew :tool:testDebugUnitTest*), Bash(python3 -m pytest *)
---

Next unchecked roadmap tasks:

!`grep -n -E '^- \[ \]' docs/ROADMAP.md | head -12`

Recent commits:

!`git log --oneline -8 2>/dev/null`

Working tree:

!`git status --porcelain | head -15`

Produce a session plan: (1) the milestone in progress and the exact next task, (2) the
gates that must be green before UI work (`./gradlew :tool:testDebugUnitTest`,
`python3 -m pytest pipeline/tests -q`) — run them if the tree is clean, (3) which agents to
use for the task (`test-oracle` for rules, `mono-designer` for a new screen,
`sdk-verifier` for any SDK assumption, `lp3-code-reviewer` before committing), (4) the
`CLAUDE.md` checkbox to tick when done. Keep it to ten lines; commit per task.
