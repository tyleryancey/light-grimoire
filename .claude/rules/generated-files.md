---
paths:
  - "pipeline/**"
  - "tool/src/main/assets/**"
  - "fixtures/**"
---

# Generated data and the pipeline

- `tool/src/main/assets/compendium/*`, `tool/src/main/assets/legal/*`, `fixtures/*.json`
  (not `fixtures/characters/` or `fixtures/journal/`) and `LICENSES/CC-BY-4.0.txt` are generated: edit the
  generator, run `python3 -m pipeline all`, commit the diff.
- New content sources need a row in `docs/LICENSING.md` and a `license_check` verdict of
  `allowed*` before they enter `pipeline/sources.lock.json`. Never 5e.tools, FightClub
  `Sources/`, D&D Beyond content, WotC art.
- Assets must be `.json` ≤ 5 MiB each (Light builder allow-list); counts are asserted in
  `sources.lock.json` and changed deliberately.
- The attribution sentence in `pipeline/legal.py` is verbatim from WotC; never reword; never
  add a trademark line.
