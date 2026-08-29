# ADR-0004 — `permissions = []`, no network, no background work in v1

**Status:** accepted

## Context
The Tool Library review favours tools with an empty permission list; the vetting lens
treats any network content as a potential feed. Every v1 feature works from bundled data
and local state. QR import (CAMERA) and voice memos (RECORD_AUDIO) are the only features
that would need permissions and both are deferred.

## Decision
v1 requests nothing, declares no `capabilities`, schedules no `@LightJob`, and locks
`orientation = "portrait"`. Export is on-screen text (QR display needs no permission).

## Consequences
The vetting one-pager's strongest lines ("Permissions: []", "nothing leaves the device")
hold by construction. Adding a permission later re-opens the one-pager (ROADMAP "Later").
