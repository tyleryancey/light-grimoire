# plugins/

`fifth-edition/` packages the domain knowledge that is not specific to this repo (rules
math, dice contract, SRD licensing guardrails, the read-only `rules-lawyer` agent) so it can
be reused by a future tool (a GM screen, a 2024-rules pack, a different platform). The
files are kept identical to their `.claude/skills/` and `.claude/agents/` twins (a pytest
checks the diff); the plugin directory is simply the shape another repo can install. They
reference this repo's files (`pipeline/reference/rules.py`, `fixtures/`, `docs/DATA-MODEL.md`)
— adapt those paths when carrying the plugin elsewhere.

Try it locally: `claude --plugin-dir ./plugins/fifth-edition`. The three skills are
background knowledge (`user-invocable: false`) that loads on demand; the `rules-lawyer`
agent is what you invoke. To share, publish this directory as a marketplace
(`plugins/.claude-plugin/marketplace.json`) — e.g. from light-workspace — and install with
`/plugin marketplace add <repo>` then `/plugin install fifth-edition@tyler-light-tools`.

The `rules-lawyer` agent references `mcp__compendium__*` tools; in another repo, either run
this repo's `pipeline/mcp/compendium_server.py` (copy the assets) or edit the agent's
`tools:` line to drop the MCP tools and rely on files.
