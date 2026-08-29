---
name: compendium-build
description: Rebuild the bundled SRD compendium and golden fixtures from the pinned sources, run the data tests, and summarize what changed — use after editing pipeline/, bumping sources.lock.json, or when assets look stale.
disable-model-invocation: true
allowed-tools: Bash(python3 -m pipeline *), Bash(python3 -m pytest *), Bash(git diff*), Bash(git status*), Bash(sha256sum *), Read
---

Current state of the generated files:

!`git status --porcelain tool/src/main/assets fixtures pipeline/sources.lock.json 2>/dev/null | head -20`

Steps:
1. `python3 -m pipeline validate` — stop and report if it fails (counts, schema, integrity).
2. `python3 -m pipeline all` — assets, legal files, fixtures.
3. `python3 -m pytest pipeline/tests -q`.
4. Run `python3 -m pipeline build` a second time and confirm `sha256sum tool/src/main/assets/compendium/*.json`
   is unchanged (reproducibility).
5. `git diff --stat tool/src/main/assets fixtures pipeline/sources.lock.json` and explain
   every changed file in one line each (which rule/source/normalizer change caused it).
6. If `fixtures/*.json` changed, list the Kotlin fixture tests that will now fail and why.
Never hand-edit generated files; never add a source that `license_check` does not allow.
