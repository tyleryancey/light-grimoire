---
name: sdk-fact
description: Verify a claim about the Light SDK / LightOS / builder against source and record the outcome in docs/sdk-facts-delta.md — "/sdk-fact LightLazyScrollView supports variable row heights"
disable-model-invocation: true
argument-hint: "<claim to verify>"
context: fork
agent: sdk-verifier
---

Verify against source and report with file:line evidence and a verdict
(confirmed / refuted / not determinable from source → hardware test):

$ARGUMENTS

If the verdict changes anything written in `docs/sdk-facts-delta.md`, `docs/ARCHITECTURE.md`
("Verified SDK facts"), `CLAUDE.md`, or `docs/research/04-light-sdk-state.md`,
output the exact dated line(s) to add or replace — the caller applies them.
