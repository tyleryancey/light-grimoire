"""Reference implementation of Grimoire's rules engine, in Python.

This is the ORACLE. The Kotlin `rules/` package on the phone must reproduce these
functions exactly; `fixtures/*.json` are generated from here and consumed by the Kotlin
unit tests. When a rule question comes up, fix it here first, regenerate fixtures, then
make Kotlin green again. Never let the two drift.

Edition: 2014 rules (SRD 5.1). Functions take an `edition` parameter where the 2024
rules differ, but only "2014" is implemented and tested today."""
