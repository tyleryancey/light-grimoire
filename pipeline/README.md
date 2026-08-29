# pipeline/ — compendium build, reference rules engine, MCP server

Desktop-only. Nothing in here runs on the phone or on Light's build server; the
outputs are committed under `tool/src/main/assets/` and `fixtures/`.

```
python3 -m pip install -r pipeline/requirements.txt
python3 -m pipeline all          # fetch → normalize → validate → emit → legal → fixtures
python3 -m pipeline validate     # no writes; CI runs this
python3 -m pytest pipeline/tests -q
python3 pipeline/mcp/compendium_server.py   # the `compendium` MCP server (stdio)
```

| Path | What |
|---|---|
| `sources.lock.json` | pinned upstream commits + sha256 of every input; expected record counts |
| `fetch.py` | raw-URL fetch into `pipeline/cache/<source>@<sha>/`, hash-verified |
| `normalize/fivebits_2014.py` | 5e-bits `src/2014/en` → unified schema (`schema/compendium.schema.json`) |
| `normalize/open5e_v1_sections.py` | 7 SRD 5.1 rule sections 5e-bits lacks (multiclassing, leveling up, …) from Open5e v1 |
| `validate.py` | schema + counts + referential integrity + hygiene + slot/prof sanity |
| `emit.py` | byte-stable JSON chunks + `index.json` (bundle hash). Builder cap: 5 MiB/file, `.json` only |
| `legal.py` | `ATTRIBUTION.md` (prescribed SRD 5.1 sentence + CC-BY modification notice) and the license text |
| `reference/dice.py` | dice grammar + mulberry32 RNG (bit-exact with the JS reference and Sudoku's port) |
| `reference/rules.py` | 2014 rules: derived stats, HP ledger, death saves, rests, slots, HP max |
| `reference/fixtures.py` | writes `fixtures/*.json` — the golden vectors the Kotlin tests replay |
| `schema/` | compendium, character, and journal JSON Schemas |
| `mcp/compendium_server.py` | FastMCP server: kinds/search/get/class_progression/spell_slots/roll/derive/apply_events/license_check/attribution/bundle_info |
| `tests/` | pytest: reference engine, oracle-vs-data (class tables), pipeline reproducibility, legal text |

Reproducibility contract: on a clean clone `python3 -m pipeline all` followed by
`git diff --exit-code tool/src/main/assets fixtures` must be empty. That is what lets a
Tool Library reviewer trust that the committed data equals the pinned public sources.

Never point this pipeline at 5e.tools, FightClub5eXML `Sources/`, or D&D Beyond content
services. See `docs/LICENSING.md` and `license_check` in the MCP server.
