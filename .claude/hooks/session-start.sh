#!/usr/bin/env bash
# SessionStart: a 10-line situation report so a fresh session starts oriented.
set -uo pipefail
ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$ROOT" 2>/dev/null || exit 0
echo "== light-grimoire session start =="
echo "branch: $(git symbolic-ref --short HEAD 2>/dev/null || echo detached)  changes: $(git status --porcelain 2>/dev/null | wc -l | tr -d ' ') file(s)"
if [ -n "${JAVA_HOME:-}" ]; then echo "JAVA_HOME: $JAVA_HOME"; else echo "JAVA_HOME unset — run: export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"; fi
if [ -f tool/src/main/assets/compendium/index.json ]; then
  python3 - <<'PY' 2>/dev/null
import json; i=json.load(open("tool/src/main/assets/compendium/index.json"))
print(f"compendium: SRD {i['srdVersion']} ({i['edition']}), bundle {i['bundleSha256'][:12]}, {sum(f['bytes'] for f in i['files'].values())/1e6:.2f} MB, {len(i['files'])} files")
PY
else
  echo "compendium: NOT BUILT — python3 -m pipeline build"
fi
[ -f fixtures/events.json ] && echo "fixtures: present ($(ls fixtures/*.json | wc -l | tr -d ' ') files)" || echo "fixtures: missing — python3 -m pipeline fixtures"
NEXT=$(grep -n -m1 -E '^- \[ \]' docs/ROADMAP.md 2>/dev/null | sed 's/^\([0-9]*\):- \[ \] /line \1: /')
[ -n "$NEXT" ] && echo "next unchecked milestone task: $NEXT"
SP=$(sed -n 's/^serverPackage *= *"\([^"]*\)".*/\1/p' tool/lighttool.toml 2>/dev/null)
[ -n "$SP" ] && [ "$SP" != "com.lightos" ] && echo "WARNING: tool/lighttool.toml serverPackage=$SP — restore com.lightos before committing"
echo "plan of record: CLAUDE.md · specs: docs/PRD.md docs/UI-SPEC.md docs/DATA-MODEL.md · oracle: pipeline/reference/"
exit 0
