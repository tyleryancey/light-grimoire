#!/usr/bin/env bash
# UserPromptSubmit: reminders injected as context when work is in flight (a Stop hook's
# stdout is not shown to Claude, so reminders live here).
set -uo pipefail
ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$ROOT" 2>/dev/null || exit 0
CHANGED=$(git status --porcelain 2>/dev/null || true)
[ -n "$CHANGED" ] || exit 0
if printf '%s\n' "$CHANGED" | grep -qE 'tool/src/(main|test)/kotlin/.*/rules/'; then
  echo "reminder: rules/ changed — run ./gradlew :tool:testDebugUnitTest (fixture replay) before committing."
fi
if printf '%s\n' "$CHANGED" | grep -qE 'pipeline/(reference|normalize)/|pipeline/sources.lock.json'; then
  echo "reminder: pipeline changed — run python3 -m pipeline all && python3 -m pytest pipeline/tests -q, and commit the regenerated assets/fixtures."
fi
if printf '%s\n' "$CHANGED" | grep -qE 'tool/lighttool.toml'; then
  echo "reminder: lighttool.toml changed — mirror the block in CLAUDE.md (it must byte-match) and SUBMISSION.md."
fi
if printf '%s\n' "$CHANGED" | grep -qE 'docs/(PRD|UI-SPEC|DATA-MODEL)\.md'; then
  echo "reminder: a spec changed — does docs/VETTING-DEFENSE.md or CLAUDE.md's plan of record need the same change?"
fi
exit 0
