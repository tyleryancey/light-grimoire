---
name: vet
description: Run the Tool Library readiness review — the adversarial vetting reviewer plus the licensing audit — and refresh docs/VETTING-DEFENSE.md and README.md's defense section if stale. Use before a release or after adding a screen, permission, dependency or content source.
disable-model-invocation: true
allowed-tools: Read, Grep, Glob, Edit, Agent
---

Run both reviews and merge their findings:

1. Delegate to the `vetting-reviewer` agent: full pass, verdict + numbered changes.
2. Delegate to the `licensing-auditor` agent: checklist + RELEASE-SAFE verdict.
3. Combine into one list ordered BLOCKER → SHOULD FIX → NIT, each with a file reference.
4. If either agent supplied replacement text for `docs/VETTING-DEFENSE.md`, apply it and
   mirror the "Why this is a clean tool to vet" section into `README.md` (that section
   must be identical in both places).
5. Print the final verdict line and remind: README prose and all `#developers` /
   `lightphone/*` communication must be written by Tyler in his own words.
