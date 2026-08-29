---
name: srd-licensing
description: Licensing guardrails for bundling 5E rules content — what is CC-BY-4.0 (SRD 5.1, SRD 5.2.1, Black Flag RD, A5ESRD, Lazy GM RD), what is forbidden (5e.tools data, FightClub XML Sources, D&D Beyond services, WotC art), the exact WotC attribution sentences, the CC-BY modification notice, and the trademark posture ("5E compatible" only). Load whenever adding, ingesting, quoting, or shipping rules content in any project.
user-invocable: false
---

# SRD licensing guardrails

**Bundle only** text from a System Reference Document under CC-BY-4.0, from a pinned,
hash-verified source, with the prescribed attribution and a modification notice.

| Source | Licence | Verdict |
|---|---|---|
| SRD 5.1 (2014 rules) — WotC PDF; 5e-bits `src/2014`; Open5e `wotc-srd`/`srd-2014` | CC-BY-4.0 | allowed |
| SRD 5.2.1 (2024 rules) — WotC PDF; Open5e `srd-2024` (no magic items); 5e-bits `src/2024` (no spells) | CC-BY-4.0 | allowed; merge sources and assert counts |
| Black Flag Reference Document (Kobold), A5ESRD (EN Publishing), Lazy GM's Resource Document (Sly Flourish) | CC-BY-4.0 (also ORC/OGL) | allowed under CC-BY, with each publisher's sentence |
| Kobold OGL books (Tome of Beasts…), Tal'dorei CS | OGL 1.0a | avoid (full OGL text + §15 + no compatibility claims) |
| 5e.tools data, FightClub5eXML `Sources/`, D&D Beyond compendium/character services, WotC art, Fan Site Kit | unlicensed / proprietary | **forbidden** |

Attribution — verbatim, one per SRD used, and nothing else about Wizards:

> This work includes material taken from the System Reference Document 5.1 ("SRD 5.1") by Wizards of the Coast LLC and available at https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License available at https://creativecommons.org/licenses/by/4.0/legalcode.

> This work includes material from the System Reference Document 5.2.1 ("SRD 5.2.1") by Wizards of the Coast LLC, available at https://www.dndbeyond.com/srd. The SRD 5.2.1 is licensed under the Creative Commons Attribution 4.0 International License, available at https://creativecommons.org/licenses/by/4.0/legalcode.

Also required by CC-BY-4.0 §3(a)(1)(B): say that the text was modified/reformatted.
Permitted branding: "compatible with fifth edition" / "5E compatible". Not permitted in
product identity: "D&D", "Dungeons & Dragons", "DM", book titles, logos, trade dress, or a
"™ Wizards" line (WotC asks for no attribution beyond the sentence).
