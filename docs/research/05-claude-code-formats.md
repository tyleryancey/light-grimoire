# 05 — Claude Code workspace formats (verified against docs, August 2026)

Source: official Claude Code documentation (code.claude.com/docs — memory, sub-agents, skills, hooks-guide,
permissions, mcp, plugins, headless, settings, best-practices), fetched 29 Aug 2026 by the `claude-code-guide`
research agent. Only the parts the scaffold relies on are reproduced here; the scaffold deliberately uses the
*conservative core* of each format (fields that have existed since 2025) so it keeps working if a newer field
is renamed.

## 1. CLAUDE.md

- Load order: managed policy → `~/.claude/CLAUDE.md` → `./CLAUDE.md` (or `./.claude/CLAUDE.md`) →
  `./CLAUDE.local.md` (gitignored). Subdirectory `CLAUDE.md` files load lazily when Claude reads files there.
- Imports: `@path/to/file` (relative to the CLAUDE.md that contains it; max 4 hops; not parsed inside code fences).
- Guidance: keep it under ~200 lines; commands Claude can't infer, conventions that differ from defaults,
  gotchas, verification loops. Exclude anything derivable from the code.
- `.claude/rules/*.md` — always-loaded rule files, optionally path-scoped with frontmatter
  `paths: ["tool/src/main/kotlin/**/*.kt"]` (fires when Claude reads matching files).

## 2. Subagents — `.claude/agents/<name>.md`

Frontmatter fields: `name` (required), `description` (required — drives automatic delegation), `model`
(`sonnet|opus|haiku|inherit`), `tools` (allow-list, comma-separated), `disallowedTools`, `permissionMode`
(`default|acceptEdits|plan|…`), `skills` (pre-loaded skills), `memory` (`user|project|local`), `maxTurns`,
`hooks`. Read-only agent = `tools: Read, Grep, Glob` (+ optionally `permissionMode: plan`).
MCP tools are referenced as `mcp__<server>__<tool>`. Explicit invocation: "Use the X agent…" or `@"X (agent)"`.
Precedence: managed → `--agents` CLI → `.claude/agents/` → `~/.claude/agents/` → plugin agents.

## 3. Skills — `.claude/skills/<name>/SKILL.md`

Frontmatter: `name` (defaults to dir name), `description` (when to use — keep it specific; it is what triggers
model invocation), `argument-hint`, `disable-model-invocation: true` (manual `/name` only),
`user-invocable: false` (background knowledge only), `allowed-tools` (auto-approved during the skill turn),
`model`, `context: fork` (+ `agent:`), `paths` (load only for matching files), `hooks`, `metadata`.
Substitutions in the body: `$ARGUMENTS`, `$0`/`$1`, `${CLAUDE_SKILL_DIR}`, `${CLAUDE_PROJECT_DIR}`,
`${CLAUDE_SESSION_ID}`. Dynamic context: a line `` !`git diff HEAD` `` is replaced with the command output at
load time. Old `.claude/commands/*.md` still work but skills supersede them (invocation control, supporting
files, hooks). Both create `/name`; skills override commands.

## 4. Hooks — `.claude/settings.json` → `hooks`

Events used by this scaffold: `SessionStart`, `PreToolUse`, `PostToolUse`, `Stop`. (Also available:
`UserPromptSubmit`, `PermissionRequest`, `PostToolUseFailure`, `SubagentStart/Stop`, `PreCompact/PostCompact`,
`SessionEnd`, `Notification`, and newer ones — not needed here.)
Matcher = tool-name regex for tool events (`Edit|Write`, `Bash`, `mcp__github__.*`); empty matcher = all.
Hook types: `command` (stdin receives JSON with `tool_name`, `tool_input`, `cwd`, …), `prompt`, `agent`,
`http`. Exit codes: `0` allow (stdout may carry JSON), `2` block (stderr = reason shown to Claude), other =
non-blocking error. PreToolUse JSON decision form:
`{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"…"}}`.
Use `"$CLAUDE_PROJECT_DIR"/.claude/hooks/<script>` so hooks resolve regardless of cwd; set `timeout` seconds.

## 5. Permissions — `settings.json` → `permissions`

`allow` / `deny` / `ask` arrays of rules: `Bash(./gradlew *)`, `Bash(git log *)`, `Edit(tool/src/**)`,
`Read(.env)`, `WebFetch(domain:5e.tools)`. `*` matches any text; a trailing ` *` matches the bare command too.
Deny wins over allow. `defaultMode`: `default|acceptEdits|plan|…`. `additionalDirectories` grants access outside
the cwd (e.g. `../light-sdk`). `env` sets environment variables for every session (e.g. `JAVA_HOME`).

## 6. MCP — `.mcp.json` (project scope, committed)

```json
{"mcpServers": {"name": {"type": "stdio", "command": "python3", "args": ["path"], "env": {"X": "${X}"}},
                "remote": {"type": "http", "url": "https://…"}}}
```
`${VAR}` and `${VAR:default}` interpolate from the environment. CLI: `claude mcp add|list|remove`.
Tool names in agent `tools:` lists: `mcp__<server>__<tool>`.

## 7. Plugins

Layout: `<plugin>/.claude-plugin/plugin.json` (`name`, `description`, `version`, `author`, `license`,
`keywords`) plus `skills/`, `agents/`, `hooks/hooks.json`, `.mcp.json` at the plugin root (not inside
`.claude-plugin/`). Marketplace: `.claude-plugin/marketplace.json` listing `{name, source, description}` entries.
Local dev: `claude --plugin-dir ./plugins/<name>`; skills surface as `/<plugin>:<skill>`.

## 8. Other mechanisms

`claude -p "…" --output-format json` (headless, CI); `--append-system-prompt`; `--bare`; worktrees; `/loop`;
`/context` to see what loaded; auto memory under `~/.claude/projects/<project>/memory/` (`/memory`).

## 9. What goes where (Anthropic guidance)

| Content | Where |
|---|---|
| Build/test commands, conventions, sharp edges | `CLAUDE.md` (short) |
| Domain knowledge loaded on demand | model-invocable skills |
| Repeatable workflows a human triggers | `disable-model-invocation: true` skills (`/name`) |
| Deterministic gates that must run | hooks |
| Isolated, focused, tool-restricted work | subagents |
| External data/tools | MCP servers |
| Bundle to share across repos | plugin |
