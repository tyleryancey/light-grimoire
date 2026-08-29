# Player-side tabletop RPG tools: a survey to inform a Light Phone 3 D&D 5e companion

*Research date: 29 August 2026. Scope: character creation, in-play sheet tracking, dice, and rules reference — the player's side of the table. Roughly 90 web fetches/searches; every non-obvious claim carries a link. Where a claim rests on my own reading of a tool rather than a source, it is labelled "inference".*

---

## 0. Executive summary

1. **The market has converged on one shape for "the sheet"**: an always-visible header (HP / temp HP / hit dice / death saves / AC / initiative / speed / conditions), a column of abilities → saves → skills, and a tabbed body of **Actions · Spells · Inventory · Features · Notes**. D&D Beyond, Roll20's 2024 sheet, Fantasy Grounds, Fight Club 5e and Shard all do this; the official 2024 paper sheet does too. ([DDB sheet sections](https://dndbeyond-support.wizards.com/hc/en-us/articles/7747193946388-Sheet-Sections), [Roll20 2024 sheet](https://help.roll20.net/hc/en-us/articles/30748164251287-Dungeons-Dragons-2024-Character-Sheet), [FG 5E sheet](https://fantasygroundsunity.atlassian.net/wiki/spaces/FGCP/pages/996641917))
2. **The most-requested thing nobody ships well is a "combat mode"**: players report that at the table they need "two things: a big list of my actions and a big list of my spells," and that a single sorcerer turn can require three tab switches to spend a slot, a sorcery point and pick a metamagic. ([DDB feature request, 2023](https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/170500-feature-request-combat-mode-for-web-ui))
3. **D&D Beyond itself is now building exactly the product this brief describes**: a new mobile app "likely early 2027," initially focused on *in-person* play — "see what your character can do, track resources like spells and Hit Points, and manage conditions," with support for tracking *physical* dice. ([DDB mid-year 2026 roadmap](https://www.dndbeyond.com/posts/2223-mid-year-update-d-d-beyonds-2026-development)) That validates the scope and tells you what "table companion" means to the incumbent.
4. **Roll20 killed its native mobile app in February 2026** ("the current experience sucks compared to the standards we've committed to") in favour of a mobile-web "Roll20 Characters." ([Roll20 blog](https://blog.roll20.net/posts/were-retiring-the-roll20-mobile-app-to-build-something-better-heres-why/)) Native, offline, phone-first sheets are a gap, not a crowded field.
5. **Offline is where incumbents are weakest.** DDB's app caches only characters previously opened online and has a documented history of characters that never load offline; Play Store reviews cite crashes "especially when modifying health." ([DDB offline thread](https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/d-d-beyond-mobile-app-feedback/115301-cant-view-any-characters-offline), [Play Store](https://play.google.com/store/apps/details?id=com.fandom.playercompanion&hl=en_US&gl=US))
6. **Content licensing is solved for 2024 rules**: SRD 5.2.1 (May 2025) is CC-BY-4.0 and includes classes/subclasses, species, backgrounds, feats, spells, magic items, monsters, and weapon-mastery rules. An LP3 tool can ship a full compendium on-device legally. ([D&D Beyond SRD post](https://www.dndbeyond.com/posts/1949-you-can-now-publish-your-own-creations-using-the))
7. **Import beats on-device entry.** The de-facto interchange format is the unofficial `character-service.dndbeyond.com/character/v5/character/{id}?includeCustomItems=true` JSON (10–15k lines per character; character must be set Public; staff: "no supported, documented, public facing API"). Every VTT importer, Avrae and Beyond20 consume it. ([API questions thread](https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/192944-api-questions), [scraper](https://github.com/MaximeBonnin/dnd-characters-dataset-24))
8. **Two data-model primitives cover almost all in-play state**: a *counter* `{name, value, max, reset ∈ {short, long, dawn}}` and a *roll* `{formula, label}`. Fight Club 5e's XML (`<counter><reset>L|S`), Foundry (`system.uses`, `@spells.spellN.value/max`) and Avrae (`!cc`, `!g ss`) all reduce to this. ([FC5 XSD](https://raw.githubusercontent.com/kinkofer/FightClub5eXML/main/Utilities/compendium.xsd), [dnd5e roll formulas](https://github.com/foundryvtt/dnd5e/wiki/Roll-Formulas))
9. **Dice ergonomics that work: pre-configured rolls, one tap.** Dice Ex Machina's folder + "Roll All" model and Pathbuilder's HP slider are praised; RPG Simple Dice's lack of "attack and damage" presets is its top complaint. ([Dice Ex Machina](http://www.between-worlds.com/dice-ex-machina-instructions), [RPG Simple Dice reviews](https://play.google.com/store/apps/details?id=com.ccp.rpgsimpledice&hl=en_US))
10. **For a 3.92" monochrome phone with a wheel**: cut the builder (import or a 5-picker quickbuild only), cut homebrew editing, cut inventory weight/art/campaigns; keep a *Turn* screen, an HP pad, resource pips, a condition list, rest buttons, a text reader and a skill/save roller. Details in §5.

---

## 1. Tool profiles

Each profile covers: (1) core features and flows, (2) design/IA, (3) data structure where public, (4) known pain points, and a one-line LP3 takeaway.

### 1.1 D&D Beyond (web + iOS/Android)

**Status 2025–26.** The official platform. Content is now labelled "5e" (2014) vs "5.5e" (2024) as of 2 March 2026; 3D shared dice launched 23 Feb 2026 and reached mobile 15 June 2026; a **Quickbuilder** for level-1 characters launched 24 March 2026 and became usable without an account on 24 Aug 2026; **condition tracking syncing to and from character sheets** (from the Maps VTT) shipped 27 July 2026; "condition icons now inline with HP" 24 Aug 2026; Journals (June 2026); an Event Finder / StartPlaying partnership (Aug 2026). ([changelog](https://www.dndbeyond.com/changelog)) The 2026 roadmap is a ground-up "game platform rebuild" (an entity-component-system rules engine) plus builder modernisation; the mid-year update adds the 2027 in-person mobile app noted above. ([roadmap](https://www.dndbeyond.com/posts/2132-d-d-beyonds-2026-development-roadmap), [mid-year](https://www.dndbeyond.com/posts/2223-mid-year-update-d-d-beyonds-2026-development))

**Creation flow (standard builder), in order:** Home (portrait, name, campaign prefs: sources, optional class features, milestone/XP, fixed/manual HP, level-scaled spells, encumbrance, coin weight, privacy) → **Class** (level, multiclass, review features per level, add/prepare spells) → **Species** → **Abilities** (standard array, point buy, manual) → **Background** (plus traits/backstory) → **Equipment** (starting kit or manual; attunement tracked) → **What's Next** (sheet, PDF export, campaign). ([Builder Sections](https://dndbeyond-support.wizards.com/hc/en-us/articles/7747202748436-Builder-Sections)) The **Quickbuilder** collapses that to five picks — class (with subclass), species, background, drag-to-assign ability scores, name — with randomise buttons and "smart defaults" that auto-apply bonuses and pick equipment; level 1 only. ([Behind the Screen](https://www.dndbeyond.com/posts/2135-behind-the-screen-the-new-quickbuilder-and-future))

**Sheet IA.** Header (portrait, name, species, class, level; Short Rest / Long Rest buttons; builder link; campaign "Launch game"; menu with level/XP, settings, game log, PDF export). Body sections: Abilities/Saves/Senses; Skills; **Limited Use** (aggregates anything with a reset condition — Short Rest, Long Rest, Dawn — with top-level "reset all" buttons); Actions (actions/bonus/reactions incl. equipped weapons and spell attacks); Inventory (personal vs *party* inventory, attunement, encumbrance, currency); Spells (slots, prepared/known, expandable details with DC); Speed/Defenses; Features & Traits; Proficiencies & Training; Background; Notes (Allies/Enemies/Backstory/Other); Extras (creatures/companions). ([Sheet Sections](https://dndbeyond-support.wizards.com/hc/en-us/articles/7747193946388-Sheet-Sections), [Character Header](https://dndbeyond-support.wizards.com/hc/en-us/articles/7747193980820-Character-Header)) Party inventory and shared gold arrived Sept 2025. ([changelog](https://www.dndbeyond.com/changelog))

**Actions vs Spells quirk.** Only spells with an attack roll appear under Actions automatically; save-based spells (e.g. Sacred Flame) must be flagged "display as attack" via Customize — a long-standing source of confusion. ([forum](https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/98877-spells-actions-and-beyond-oh-my))

**Mobile app.** "Access your character sheets and purchased sources"; sources are "fully functional offline after you download them"; characters aim for "as much information as possible even when offline." ([support](https://dndbeyond-support.wizards.com/hc/en-us/articles/7747193137684-D-D-Beyond-Mobile-App)) Google Play: 4.6★ from ~129k reviews, updated 11 Aug 2026; App Store: 4.7★, ~89k ratings, v2.90 (July 2025). ([Play](https://play.google.com/store/apps/details?id=com.fandom.playercompanion&hl=en_US&gl=US), [App Store](https://apps.apple.com/us/app/d-d-beyond/id1501810129))

**Pricing (June 2025 support page):** Hero $2.99/mo, $14.99/6 mo, $25.99/yr; Master $5.99/mo, $29.99/6 mo, $54.99/yr. Free: 6 character slots, 6 encounters, homebrew creation, Maps VTT. Hero: unlimited characters, community homebrew access, weekly "DDB Drops." Master: share purchased books with up to 12 people across 5 campaigns, custom map uploads. Books are always bought separately. ([pricing](https://dndbeyond-support.wizards.com/hc/en-us/articles/7747225116820-Subscriptions-Pricing), [subscribe](https://www.dndbeyond.com/en/subscribe))

**Data structure (unofficial).** Two legacy paths exist: `dndbeyond.com/profile/{user}/characters/builder/{id}/json` (2017-era, "not meant to be publicly consumed… will never adhere to public contracts") and, since the June 2020 sheet rebuild, `character-service.dndbeyond.com/character/v3/…` then `v5/character/{id}?includeCustomItems=true`. A character must be set **Public** or the call returns "Unauthorized Access Attempt". ([json docs thread](https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/11540-documentation-for-json-file), [removed endpoints thread](https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/71065-removed-undocumented-api-endpoints-regarding?page=8), [API questions](https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/192944-api-questions)) The response is `{ id, success, message, data: {…} }` and runs 10–15k lines per character. ([scraper](https://github.com/MaximeBonnin/dnd-characters-dataset-24)) Community-confirmed fields under `data`: `baseHitPoints`, `bonusHitPoints`, `overrideHitPoints`, `removedHitPoints` (damage taken — **current HP is derived, not stored**), `temporaryHitPoints`, `stats[]`/`bonusStats[]`/`overrideStats[]` (six entries by ability id), `classes[]` (each with `level`, `hitDiceUsed`, `definition`, `subclassDefinition`, `classFeatures`), `race`, `background`, `inventory[]` (each with `definition`, `quantity`, `equipped`, `isAttuned`, `chargesUsed`), `currencies {cp,sp,gp,ep,pp}`, `spellSlots[]`/`pactMagic[]` (`level, used, available`), `classSpells[]` and `spells{race,class,item,feat}` (each spell with `prepared`, `definition`), `conditions[]`, `deathSaves {failCount, successCount}`, `inspiration`, `actions{…}` and — the important one — **`modifiers` grouped by source** (`race`, `class`, `background`, `item`, `feat`, `condition`), each modifier a `{type, subType, value, statId, restriction, …}` such as `bonus/armor-class` or `proficiency/stealth`. Derived values (AC, skill totals) are *not* in the payload; importers recompute them from modifiers, which the 2017 thread already called the hard part. ([removed endpoints](https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/71065-removed-undocumented-api-endpoints-regarding?page=8), [json docs](https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/11540-documentation-for-json-file); field list beyond those threads is from reading community parsers such as ddb-importer/Beyond20 — treat as inference and re-verify against a live payload.) ddb-importer proxies the calls and needs the user's `CobaltSession` cookie for non-public bulk access. ([ddb-importer](https://github.com/MrPrimate/ddb-importer))

**Pain points.** Sheets taking "upward of 30 seconds" to load (Apr–May 2026 thread); the app freezes "especially when modifying health"; only 6 free character slots; "basic stats on main page" requested; the 2024 rollout doubled every spell in pickers ("almost every goddamn spell" appears twice) with no toggle to hide legacy content, prompting subscription cancellations; homebrew item bonuses "won't apply to your stats"; characters that never load offline. ([slow load](https://www.dndbeyond.com/forums/d-d-beyond-general/bugs-support/239247-slow-load-time-for-character-sheets), [Play Store](https://play.google.com/store/apps/details?id=com.fandom.playercompanion&hl=en_US&gl=US), [ruleset toggle](https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/207343-toggle-2024-ruleset-on-character-builder), [App Store](https://apps.apple.com/us/app/d-d-beyond/id1501810129), [offline](https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/d-d-beyond-mobile-app-feedback/115301-cant-view-any-characters-offline))

**LP3 takeaway.** DDB is the source of truth for most players' characters; the winning move is to *import* from it (public-character JSON) and *out-execute* it at the table: instant load, offline, HP edits that never crash, and no Actions/Spells split.

### 1.2 Roll20 (Charactermancer, 2024 sheet, Roll20 Characters)

**Features.** The D&D 2024 sheet has an always-visible top block — character info (incl. heroic inspiration), HP (current/max/temp/hit dice/death saves as 6 checkboxes), abilities+saves, AC/speed, skills, defenses, **conditions with an exhaustion tracker whose toggles apply modifiers to rolls**, senses — and six tabs: Combat (attacks, masteries, effects, actions), Spells (slots as togglable checkboxes 1st–9th; DC/attack at top), Inventory (equipment toggles equip/unequip, attunement max 3, currency, encumbrance), Features & Traits (with resource trackers), Notes, About. Short/Long Rest buttons open a rest modal. Rolls: click skill row, attack row (hit and damage separate), death-save Roll button, spell row → Cast modal. ([help center, Nov 2025](https://help.roll20.net/hc/en-us/articles/30748164251287-Dungeons-Dragons-2024-Character-Sheet)) The builder "walks you through the steps… showing options from all the content available to you"; equipment, non-standard-array scores and a review screen were promised later (July 2024). ([blog](https://blog.roll20.net/posts/dd-2024-character-sheet-and-builder-now-in-beta-on-roll20/)) **Roll20 Characters** (standalone sheets outside a game) is free. ([StartPlaying guide](https://startplaying.games/blog/posts/how-to-use-roll20-one-dnd-2024-character-sheet)) The older Charactermancer cannot use custom compendium/homebrew entries. ([wiki](https://wiki.roll20.net/Charactermancer))

**Mobile.** Native app delisted the week of 18 Feb 2026; replacement is mobile browser + home-screen shortcut, phone optimisation for the new 5E sheet "completing March 2026." ([Roll20 blog](https://blog.roll20.net/posts/were-retiring-the-roll20-mobile-app-to-build-something-better-heres-why/))

**Dice syntax** (the lingua franca other tools copy): `1d20+5`, keep/drop `kh/kl/dh/dl` (`2d20kh1` = advantage), exploding `!`/`!!`, rerolls `r`/`ro`, inline `[[3d6]]`, crit thresholds `cs>10cf<3`, grouped `{3d6+3d4, 2d8+4}k1`, `floor()/ceil()`. ([Dice Reference](https://help.roll20.net/hc/en-us/articles/360037773133-Dice-Reference))

**Pricing.** Free tier unchanged; last published increase (July 2021) set Plus $5.99/mo ($59.99/yr) and Pro $10.99/mo ($109.99/yr). ([announcement](https://bloghub.roll20.net/posts/roll20-pricing-increase-announcement/)) Content (2024 core books) purchased separately; owned content now also surfaces on Demiplane's D&D Nexus. ([Demiplane](https://www.demiplane.com/blog/announcing-d-d-nexus---part-of-the-connected-d-d-experience-on-roll20))

**Pain points.** StartPlaying: "some choices can be confusing"; no print. ([StartPlaying survey](https://startplaying.games/blog/posts/every-character-maker-dnd)) Native mobile was bad enough to be killed.

**LP3 takeaway.** The 2024 sheet's "always-visible vitals + six tabs" is the cleanest published IA; its condition toggles that *apply modifiers* and exhaustion-in-the-conditions-modal are worth copying.

### 1.3 Foundry VTT — dnd5e system

**Features.** dnd5e 5.3.x requires Foundry 13/14 and ships SRD 5.2 content under CC-BY-4.0; the redesigned sheet has favourites, per-tab filtering, item containers, party grouping, DM "requested rolls," and "Ruletips" (inline rules tooltips for conditions/cover). ([package page](https://foundryvtt.com/packages/dnd5e)) 5.3.0 rebuilt Advancement (level-up) on ApplicationV2; 5.2.5 added premade SRD 5.2 characters per class and tier; 5.2.0 introduced 2024-rules support (modern vs legacy). ([releases](https://github.com/foundryvtt/dnd5e/releases)) The official 2024 PHB module contains "12 classes, 48 subclasses, 10 species, 16 backgrounds, and 75 feats" and 391 spells, with Activities, Active Effects and summoning automation. ([PHB module](https://foundryvtt.com/packages/dnd-players-handbook))

**Data structure.** Actors export/import as JSON via right-click "Export Data"/"Import Data." ([Actors article](https://foundryvtt.com/article/actors/)) The `system` object's shape is visible through the roll-formula variables: `@abilities.{str…cha}.{value,mod,dc,checkBonus,saveBonus}`, `@attributes.prof`, `@attributes.ac.value`, `@attributes.hp.{value,max,temp}`, `@attributes.hd.{value,max,bySize.d8}`, `@attributes.init.total`, `@attributes.spell.{dc,mod,attack}`, `@attributes.exhaustion`, `@details.{level,tier}`, `@classes.wizard.levels`, `@spells.spell1…spell9.{value,max}` and `@spells.pact`, `@skills.{acr,ani,arc,…}.{mod,bonus,passive}`, `@resources.{primary,secondary,tertiary}.value`, `@scale.*`, `@statuses.*`. ([Roll Formulas wiki](https://github.com/foundryvtt/dnd5e/wiki/Roll-Formulas)) The model also carries `attributes.death{success,failure}`, `attributes.inspiration`, `attributes.concentration`, `attributes.movement/senses`, `currency{pp,gp,ep,sp,cp}`, `traits{size,languages,dr,di,dv,ci,weaponProf,armorProf}`; items (class, subclass, background, race, feat, spell, weapon, equipment, consumable, tool, loot, container) embed on the actor with `system.uses{value,max,recovery}`, `activities` and `advancement`. ([DeepWiki data models](https://deepwiki.com/foundryvtt/dnd5e/2-data-models)) *Concentration* and *bloodied* are first-class statuses in 5.x. ([releases](https://github.com/foundryvtt/dnd5e/releases))

**Pricing/platform.** One-time software licence, self-hosted; official 2024 books sold as modules. Not mobile-native.

**LP3 takeaway.** The cleanest *normalised* 5e data model in the wild. Adopt its path vocabulary (`abilities.*.mod`, `attributes.hp.{value,max,temp}`, `spells.spellN.{value,max}`, `uses.{value,max,recovery}`) as the internal schema; it maps mechanically onto FC5 XML and DDB JSON.

### 1.4 Fantasy Grounds Unity

**Features.** Seven-tab sheet — Main (class/level, abilities, saves, AC, init, speed, HP, senses), Skills, Abilities (feats/features/proficiencies), Inventory, Notes, Log, Actions (weapons/spells/powers). HP is modelled as **Wounds + Max + Temp** (damage accumulates as wounds — the same "removedHitPoints" idea as DDB), with Hit Dice double-clicked to roll recovery; death saves are clickable fields; proficiency icons **cycle four states** (none → half → full → double). Creation via Character Wizard, drag-and-drop, or manual; levelling is "drop the Class you want to level up in on the Class & Level line." Rolls: double-click or drag to chat; Ctrl+wheel adds temporary modifiers. ([FG 5E sheet wiki](https://fantasygroundsunity.atlassian.net/wiki/spaces/FGCP/pages/996641917)) 2024 and 2014 coexist as "2024"/"Legacy" variants of one ruleset with a per-entry Version field and warnings on mixing. ([FG 2024 vs Legacy](https://fantasygroundsunity.atlassian.net/wiki/spaces/FGCP/pages/2697691149/5E+50th+Anniversary+2024+vs.+5E+Legacy+2014)) Windows/macOS/Linux; one-time or subscription licence; adventures ~$29.99 each. ([Steam](https://store.steampowered.com/app/1196310/Fantasy_Grounds_Unity/)) Desktop-only; heavy.

**LP3 takeaway.** Two portable micro-patterns: *wounds-not-current-HP* storage (never lose max) and the *cycle-tap proficiency toggle* (zero text entry).

### 1.5 Fight Club 5e / Game Master 5e (Lion's Den) and the XML compendium

**Features.** FC5 is a digital sheet with auto-calculation, spell slot tracking, equipment/AC, custom dice, and a *customisable compendium* that ships only SRD 5.1 by default. Free = 1 character with ads; $2.99 unlock = unlimited characters, no ads. iOS v3.1.1, 4.7★ (1.9k); Android updated 20 Aug 2025, 4.3★ (2.8k). ([App Store](https://apps.apple.com/us/app/fight-club-5th-edition/id901057473), [Play](https://play.google.com/store/apps/details?id=com.lionsden.fc5&hl=en_US)) GM5 (companion DM app) last updated Sept 2020 on iOS; it imports FC5 characters into its combat tracker. ([GM5 App Store](https://apps.apple.com/us/app/game-master-5th-edition/id908176026))

**The XML format** (root `<compendium version auto_indent>`), from the community XSD:
- `<item>`: `name, type, magic, detail, weight, text, roll, value, modifier, ac, strength, stealth, dmg1, dmg2, dmgType, property, range`; type codes `LA MA HA S M R A RD ST WD RG P SC W G $`.
- `<race>`: `name, size (T S M L H G), speed, ability, spellAbility, proficiency, trait, modifier`.
- `<class>`: `name, hd, proficiency, spellAbility, numSkills, autolevel, armor, weapons, tools, slotsReset, wealth`; `<autolevel level scoreImprovement>` holds `<feature optional>` (`name, text, special, modifier, proficiency`), `<slots optional>` (integer list) and **`<counter>` = `name, value, reset ∈ {L, S}`**.
- `<spell>`: `name, level, school (A C D EN EV I N T), ritual, time, range, components, duration, classes, source, text, roll`.
- `<feat>`, `<background>`, `<monster>` (full stat block incl. `legendary`, `reaction`, `spells`, `slots`).
- `<modifier category="bonus | ability score | ability modifier | saving throw | skills">`.
- `<roll>` strings accept dice, `STR…CHA`, `PROF`, `LVL`, `SPELL`, `W/WO`, `%0`.
([compendium.xsd](https://raw.githubusercontent.com/kinkofer/FightClub5eXML/main/Utilities/compendium.xsd), [Sources README](https://github.com/kinkofer/FightClub5eXML/blob/main/Sources/README.md)) Sources are one XML per book, merged by `xsltproc` via `Collections/*.xml` (`<doc href>` lists); class merges append `<autolevel>` blocks by matching `<name>`. ([README](https://raw.githubusercontent.com/kinkofer/FightClub5eXML/main/README.md)) A maintained fork covers "5 and 5.5th Edition" content and targets FC5, GM5 and a newer "Character Craft 5.5e" app. ([vidalvanbergen fork](https://github.com/vidalvanbergen/FightClub5eXML))

**Pain points.** "Base compendium… very lacking"; no proficiency chooser ("manually" enter); no AC override; adding a spell means scrolling "past dozens of other spell types alphabetically" with no class filter; Android lags iOS (can't move items between containers); wild shape/invocations not modelled. ([App Store](https://apps.apple.com/us/app/fight-club-5th-edition/id901057473), [Play](https://play.google.com/store/apps/details?id=com.lionsden.fc5&hl=en_US))

**LP3 takeaway.** The `counter{value, reset L|S}` primitive and `autolevel` structure are the smallest complete model of "limited-use features." The community XML corpus is an import-ready content source (licensing aside), and its 2.8k+1.9k reviews show an offline, one-time-purchase sheet can thrive without a VTT.

### 1.6 DiceCloud v2

Web app; v2 reorganised the sheet as a **tree** — "each class level, background, feat, etc. becomes a folder into which other character properties can be stored," reorderable, with libraries that let "everything that can be stored on a character" be shared. ([Patreon post](https://www.patreon.com/posts/version-2-and-26326856)) Automates modifiers/DCs/weight; custom formulas; Discord webhooks; free core with patron tiers; Avrae imports it (`!import https://dicecloud.com/character/…`). ([HarpsCorp](https://harpscorp.com/dicecloud-digital-character-management/), [Avrae get started](https://avrae.readthedocs.io/en/latest/cheatsheets/get_started.html)) Pain points: learning curve; v2 integrations "still being developed." No offline mode documented. **Takeaway:** the source-folder tree solves "where did this +2 come from," but it is a power-user IA, wrong for a 3.9" screen.

### 1.7 Shard Tabletop

Free tier includes the full SRD; subscriptions from $19.99/yr; "full support for D&D 2024" via a free compatibility pack; the sheet consolidates "actions, bonus actions, reactions, free actions, and features that modify… other actions" plus active spell effects and conditions, with automation for Agonizing Blast, Bardic Inspiration, Sneak Attack. ([offer page](https://www.shardtabletop.com/offer)) Community: "the only VTT I know of that has a good mobile friendly character builder that doesn't require you to load a whole VTT"; a new player built a character "in just a few minutes." ([EN World](https://www.enworld.org/threads/black-flag-and-tales-of-the-valiant-on-shard-tabletop.704288/)) Web-only. **Takeaway:** its grouping by *action economy* (action/bonus/reaction) is the right combat-first lens.

### 1.8 Demiplane / D&D Nexus

D&D Nexus launched 11 Nov 2025 as a **digital reader/compendium** for the 2024 PHB/DMG and Forgotten Realms books, cross-linked with Roll20 purchases; it had **no character builder** at launch ("Doesn't seem to have a character builder. Any idea when that will be available?"), and its text wasn't searchable like Roll20's. ([Demiplane blog](https://www.demiplane.com/blog/announcing-d-d-nexus---part-of-the-connected-d-d-experience-on-roll20), [EN World](https://www.enworld.org/threads/d-d-nexus-at-demiplane-with-wotc-core-books.716176/)) Demiplane's other Nexuses (Pathfinder, Starfinder, Daggerheart, Vampire, Marvel, ALIEN, Cosmere, Cyberpunk RED, Fallout) have step-by-step builders, in-sheet rolling, PDF export and "Edit by Others"; the Roll20 integration beta (30 Apr 2025) syncs six of them, not D&D. ([Q1 2025 review](https://www.demiplane.com/blog/demiplane-q1-2025-review), [integration beta](https://www.enworld.org/threads/roll20-demiplane-integration-beta-is-live.713151/)) Per-book purchases plus an optional subscription for sharing. **Takeaway:** a reference-first product; confirms that a rules reader with cross-links is a distinct, valued surface.

### 1.9 Alchemy RPG

Browser/desktop VTT (min 1366×768; "not yet optimized for gameplay on mobile devices"); SRD 5e; the sheet "elevates the most-used parts of a character sheet into prominent UI panels"; creation = modal with Create / Library / Premades tabs; HP-style **trackers** are editable bars flanking the portrait with custom labels; JSON export/import per character; Free = 3 characters, Unlimited $8/mo or $88/yr; one-click DDB import (converter: ddb2alchemy). ([D&D page](https://alchemyrpg.squarespace.com/dungeonsanddragons), [help](https://help.alchemyrpg.com/en/articles/9821403-creating-a-character), [ddb2alchemy](https://github.com/alchemyrpg/ddb2alchemy), [CBR import guide](https://www.cbr.com/dnd-beyond-alternatives-import-guide/)) **Takeaway:** "generic trackers" (label + current/max) are exactly the counter primitive again.

### 1.10 Dungeon Master's Vault (ex-Orcpub)

Free, web, SRD-only (2014); guided questionnaire for new players; `.orcbrew` (EDN) homebrew files; PDF export. Known issues: files vanish from "My Content," PDF overflow on Features & Traits, prepared casters must configure daily spells manually, weapon attacks limited to STR/DEX, only the creator can edit. ([known issues](https://www.dungeonmastersvault.com/help/known-issues-site-limitations/), [StartPlaying](https://startplaying.games/blog/posts/every-character-maker-dnd), [GitHub](https://github.com/Orcpub/orcpub)) No 2024 rules. **Takeaway:** a cautionary example of builder-first design that never got a good in-play mode.

### 1.11 Aurora Builder

Windows-only; XML "elements" pulled from index URLs; PDF export with optional spell/item cards; development "postponed indefinitely" since 25 Oct 2020 (v1.0.3). ([aurorabuilder.com](https://aurorabuilder.com/)) Effectively dead; community element repos persist. **Takeaway:** its *cards* export (one spell/item per card) is a relevant small-format idea.

### 1.12 5e Companion App (Android/iOS) and Fifth Edition Character Sheet

**5e Companion App** (blastervla): sheet manager (50+ races/backgrounds), 760+ monster bestiary, homebrew creator, spell/item compendium, encounter generator, initiative tracker, dice with advantage; free with $2.99 ad removal; iOS 4.0★ (430), last iOS update May 2025 pointing users to a successor "RPG Companion App." Complaints: class/subrace selection bugs resetting levels, crashes on share, iPad blank screens, wrong weapon dice. ([App Store](https://apps.apple.com/us/app/5e-companion-app/id1507254451)) **Fifth Edition Character Sheet** (Kammerer): 1M+ installs, 4.4★ (38k), updated Sept 2025; multi-page sheet with spellbook/slots, weapons, notes, currency; complaints that creation hides what race/class grant, "you can't add spells" from a list, items are free text. ([Play](https://play.google.com/store/apps/details?id=com.wgkammerer.testgui.basiccharactersheet.app&hl=en_US)) Both offline-capable. **Takeaway:** the reliable failure mode of indie sheet apps is *data-entry burden* — no pickers, free-text spells. The LP3 must never ask for typed spell names.

### 1.13 Avrae (Discord bot)

The purest expression of "what do players actually *do* in play," because every action is a typed command:
- Sheet: `!import <ddb.ac | dicecloud | gsheet URL>`, `!update`, `!sheet`, `!char <name>`.
- Checks/saves: `!check arcana` / `!c`, `!save dexterity` / `!s`.
- Attacks/actions: `!attack longsword -t <target> [adv|dis] [hit|miss|crit] [-phrase "…"]`, `!action <name>` / `!a`.
- Spells: `!cast <spell> -t <target> -l <slot level>` (multiple `-t` for AoE).
- Resources: `!game hp <±n>` / `!g hp set <n>` / `!g hp max`, `!g thp <n>`, `!g ss 1 -1` (spell slots), `!g ds` (death save), `!rest short [XdY]`, `!rest long` (`!g sr`, `!g lr`).
- Counters: `!cc <name>`, `!cc <name> +1/-1`, `!cc reset <name>` (create with `-reset short|long`, `-min`, `-max`, `-type bubble`).
- Initiative: `!i join`, `!i next`, `!init effect <who> <effect>`, `!init re <status>`, `!i hp <who> max <n>`.
- Dice: `!r 2d20kh1+4` (advantage).
([HackMD cheat sheet](https://hackmd.io/@pontifexexmachina/Hy9hkXBFt), [DW guide](https://dndworld.github.io/start-playing/avrae/), [pc_combat.rst](https://github.com/avrae/avrae/blob/master/docs/cheatsheets/pc_combat.rst), [get_started](https://avrae.readthedocs.io/en/latest/cheatsheets/get_started.html))
**Takeaway:** that command list *is* the LP3 feature list: attack, cast, check, save, hp±, thp, slot±, counter±, death save, rest, roll. Everything else is builder or DM.

### 1.14 Beyond20 (browser extension)

Chrome/Firefox; adds roll buttons to the DDB sheet (initiative, skills, attacks, spells, hit dice) and makes dice formulas in descriptions clickable; sends to Roll20, Foundry (v10–13), Discord, Astral and DDB's own game log. ([GitHub](https://github.com/kakaroto/Beyond20)) Very actively maintained: v2.21.0 (25 Aug 2026) added 2024 Bladesong/Unarmed Fighting; v2.18.0 (Mar 2026) HP sync with Roll20's 2024 sheet; 2.20.x fixed Roll20 URL changes — i.e. it breaks and is patched every time DDB or Roll20 changes markup. ([release notes](https://beyond20.here-for-more.info/release_notes)) **Takeaway:** anything scraping DDB is a treadmill; import a snapshot, don't live-bind.

### 1.15 Owlbear Rodeo

Extension-based VTT. "Sheet from Beyond" attaches a URL (e.g. a public DDB sheet) to a token and opens it in a popover iframe, falling back to a new tab when the site blocks framing; community 5e sheet extensions exist. ([Sheet from Beyond](https://extensions.owlbear.rodeo/sheet-from-beyond), [extensions](https://extensions.owlbear.rodeo/)) Owl20 bridges Beyond20 rolls into Owlbear. ([Owl20](https://owl20.uberdragon.org/)) **Takeaway:** no player-facing sheet of its own; irrelevant beyond confirming DDB-as-source-of-truth.

### 1.16 Hero Lab (Online / Classic)

Hero Lab Online lists Pathfinder 2e, Starfinder and Shadowrun 6 — **no 5e**; tiers Apprentice (free), Standard $9.99/6 mo, Patron $24.99/6 mo, browser-based, no offline. ([wolflair HLO](https://www.wolflair.com/hlo/)) 5e lives in Hero Lab *Classic* (Jan 2016, SRD-only, $29.99), which drew "no way I would pay for an incomplete database" reactions. ([EN World](https://www.enworld.org/threads/herolabs-5e-update-is-here.663579/)) **Takeaway:** paid SRD-only tools get punished; content completeness matters more than automation.

### 1.17 Pathbuilder 2e (Pathfinder, studied for UX)

Web (desktop/large tablets only), Android (5.98k reviews, 4.7★, updated 11 Aug 2026), iOS (4.9★, "IOS Full Unlock $6.99"); web and Android are separate purchases; paid tier adds Google Drive cloud saves, variant rules, companion tabs; exports PDF and **JSON for Foundry**. ([pathbuilder2e.com](https://pathbuilder2e.com/), [Play](https://play.google.com/store/apps/details?id=com.redrazors.pathbuilder2e&hl=en_US), [App Store](https://apps.apple.com/us/app/pathbuilder-2e/id6740821064), [Groupfinder](https://groupfinder.gg/library/pathbuilder-2e)) UX notes from the patch history: content split into tabs (spells; weapons/armor/gear/feats; inventory), **"UI slider to hp controls"** (v60), optional dice rollers "reworked to match the faces of physical dice," light mode, print buttons per tab, aria controls; v109c is 31 July 2026. ([patch notes](https://pathbuilder2e.com/patchnotes.html)) Praised for planning "feats for level 15 while your character is still level 1" and cross-device sync; criticised for VoiceOver not working in creation and custom-item fields clearing. ([App Store](https://apps.apple.com/us/app/pathbuilder-2e/id6740821064)) **Takeaway:** a single developer built the category-leading builder by (a) being complete and current, (b) letting the *builder* double as the *sheet*, and (c) making HP a slider — a wheel-friendly control.

### 1.18 Adventurer's Codex

Open-source (GPL-3.0) web sheet + party/DM tools with 1,524 commits, 62 open issues; free; JS stack. ([GitHub](https://github.com/adventurerscodex/adventurerscodex)) Alive but niche; no mobile app, no 2024 rules mentioned. **Takeaway:** an open reference implementation of a 5e sheet if you want to read one.

### 1.19 Official paper sheets (2014 and 2024)

2014: three pages — combat/stats/equipment; personality/appearance/allies/backstory; spellcasting. 2024: condensed to **two pages**, with **skills listed under their governing ability**, an **attunement** box, "Heroic Inspiration" and "Species" wording, and (at release) non-fillable PDFs; reactions: "asymmetrical" ability placement and no spell-by-level organisation. ([EN World release thread](https://www.enworld.org/threads/2024-d-d-character-sheets-available-to-download.706499/)) Player opinion of both official sheets: "as awful as the 2014… poorly designed with respect to usability at the table," "tiny boxes," not enough room for HP/features; skill-under-ability grouping splits opinion ("breaking up a long list into a bunch of short lists is easier"). ([EN World opinion thread](https://www.enworld.org/threads/2024-d-d-character-sheet-what-do-you-think.704868/page-12)) Fan 2024 recreations expand to four pages: combat core; equipment/treasure; character development; spellcasting with slot checkboxes and weapon-mastery checkboxes. ([olddungeonmaster](https://olddungeonmaster.com/2025/01/19/dd-5-5-character-sheet-fillable-and-auto-calculating/)) Official downloads (2024 sheet, fillable, localised) live on DDB's resources page. ([DDB resources](https://www.dndbeyond.com/resources/1779-d-d-character-sheets)) **Takeaway:** the *reading order* players have internalised is: identity → abilities/saves/skills → AC/init/speed → HP block → attacks → features → equipment; spells on their own page.

### 1.20 MPMB's Character Record Sheet

Form-fillable PDF that "tries to house all parts of your character's administration in one handy PDF"; **Adobe Acrobat DC only** (uses Acrobat's JavaScript API — no mobile, no web viewers; flatten for tablets and lose interactivity); automates class features, spell sheets with slots/spell points, wild shape, multiclassing, AL logsheets; SRD content built in, everything else via community "import scripts"; pay-what-you-want or $1+/mo Patreon. ([FAQ](https://www.flapkan.com/faq), [flapkan.com](https://www.flapkan.com/)) Stable v13.2.3 (Dec 2024); v14.0.9-beta (June 2026) and a separate **v24.x beta for 5.5e (2024)** for patrons. ([releases](https://github.com/morepurplemorebetter/MPMBs-Character-Record-Sheet/releases)) **Takeaway:** the best "print-shaped" automation, but bound to a desktop runtime — a clear statement that the sheet format and the play device are separable.

### 1.21 Briefly: initiative/encounter tools players touch, and dice apps

- **Improved Initiative**: open-source (MIT) PWA initiative tracker with player view, HP/tags, JSON stat-block import, free, "Epic Initiative" sync via Patreon; a reviewer found the round counter "hidden within the settings" and HP editing able to open "an infinite stack of fields." ([GitHub](https://github.com/cynicaloptimist/improved-initiative), [Homebrew Creation review](https://homebrewcreation.com/reviews/best-dnd-encounter-builders-initiative-trackers/))
- **Kobold Fight Club**: original site broke in Aug 2021; **Kobold+ Fight Club** replaced it (no Google Sheets backend; custom content initially disabled), exports encounters to Improved Initiative. ([EN World](https://www.enworld.org/threads/update-on-kobold-fight-club.682231/), [review](https://homebrewcreation.com/reviews/best-dnd-encounter-builders-initiative-trackers/))
- **RPG Simple Dice** (Android, 4.6★/19k, no ads): d4–d100 plus custom sides, 1–100 dice, ±100 modifier, history; top complaints — no presets "like attack and damage," results auto-sorted high→low. ([Play](https://play.google.com/store/apps/details?id=com.ccp.rpgsimpledice&hl=en_US))
- **Dice Ex Machina** (iOS): folders of named rolls (`Attack roll: 1d20+4`) with toggleable modifiers and a **"Roll All"** per folder — "set up correctly, they can save a tremendous amount of time in fast paced combat"; drop-highest/lowest for adv/dis; crit highlight thresholds. ([instructions](http://www.between-worlds.com/dice-ex-machina-instructions))
- **Roll20 syntax**: see §1.2; the `2d20kh1` idiom and `cs>`/`cf<` markers are what users expect a roller to understand.

---

## 2. Feature matrix

Legend: ● full · ◐ partial · ○ none/unknown · † unofficial/community. "Import" = ingests other formats; "Export" = machine-readable out.

| Tool | Builder | Level-up | HP/THP/HD | Death saves | Conditions/Exhaustion | Slots & prepared | Concentration | Limited-use resets | Inventory/attune/coin | Rest buttons | Dice from sheet | Rules text in-app | Homebrew | Multi-char | Import | Export | Offline | Mobile | 2024 rules | Price |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| D&D Beyond | ● (+Quickbuilder) | ● | ● | ● | ● (syncs w/ Maps) | ● | ◐ | ● ("Limited Use") | ● (+party) | ● | ● (3D shared) | ● (owned books) | ● | 6 free | ○ | PDF; JSON† | ◐ (flaky) | ● native | ● | Free / $2.99 / $5.99 mo |
| Roll20 2024 sheet | ● (in progress) | ◐ | ● | ● | ● (applies mods) | ● | ○ | ● trackers | ● | ● modal | ● | ◐ compendium | ◐ | ● | DDB† | ○ | ○ | web only (app retired Feb 2026) | ● | Free; Plus/Pro |
| Foundry dnd5e | ● Advancement | ● | ● | ● | ● (+bloodied) | ● | ● | ● uses/recovery | ● containers | ● | ● | ● Ruletips/journals | ● | ● | DDB† (ddb-importer) | JSON | ● self-host | ○ | ● (modern/legacy) | licence + modules |
| Fantasy Grounds Unity | ● wizard/drag | ● drag | ● wounds | ● | ● effects | ● | ◐ | ● | ● | ◐ | ● | ● modules | ● | ● | XML† | XML | ● | ○ | ● (2024/Legacy) | one-time/sub |
| Fight Club 5e | ● from compendium | ● | ● | ● | ◐ | ● | ○ | ● counters | ● | ● | ● custom | ◐ (SRD 5.1 + XML) | ● XML | 1 free / ∞ $2.99 | XML | XML | ● | ● native | ◐ via fork XML | $2.99 |
| DiceCloud v2 | ● tree/libraries | ● | ● | ● | ◐ | ● | ○ | ● | ● | ◐ | ● | ◐ | ● formulas | ● | ○ | Avrae reads it | ○ | web | ◐ | free + patron |
| Shard Tabletop | ● mobile-friendly | ● | ● | ● | ● | ● | ◐ | ● automation | ● | ● | ● | ● SRD+bought | ◐ | ● | ○ | ○ | ○ | web | ● (free pack) | free; $19.99/yr+ |
| Demiplane D&D Nexus | ○ (none at launch) | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ● reader | ○ | – | – | – | ○ | web | ● | per-book |
| Alchemy | ◐ modal | ◐ | ● trackers | ◐ | ◐ | ● | ○ | ◐ | ● | ○ | ● | ◐ SRD | ◐ | 3 free | DDB (one-click) | JSON | ○ | ○ (1366×768 min) | ○ | $8/mo |
| DM's Vault | ● questionnaire | ● | ◐ | ◐ | ○ | ◐ manual | ○ | ◐ | ● | ○ | ○ | ◐ SRD | ● orcbrew | ● | ○ | PDF/orcbrew | ○ | web | ○ | free |
| Aurora Builder | ● | ● | ○ | ○ | ○ | ○ | ○ | ○ | ● | ○ | ○ | ● compendium | ● XML | ● | XML | PDF/cards | ● | Windows | ○ (dead 2020) | free |
| 5e Companion App | ● | ● | ● | ● | ◐ | ● | ○ | ◐ | ● | ◐ | ● | ● | ● | ● | ○ | ◐ share | ● | ● | ○ | free + $2.99 |
| Avrae | ○ (imports) | via !update | ● `!g hp/thp` | ● `!g ds` | ● `!init effect` | ● `!g ss` | ● | ● `!cc` | ◐ | ● `!rest` | ● | ◐ | aliases | ● | DDB/Dicecloud/GSheet | ○ | ○ | Discord | ● | free |
| Beyond20 | – | – | sync | – | – | – | – | – | – | – | ● | – | – | – | DDB DOM | rolls→VTT | ○ | ○ | ● | free |
| Hero Lab (Classic 5e) | ● | ● | ● | ● | ● | ● | ◐ | ● | ● | ◐ | ◐ | ◐ SRD | ● | ● | ○ | ◐ | ● | iPad | ○ | $29.99 |
| Pathbuilder 2e | ● best-in-class | ● pre-plan | ● slider | n/a | ● | ● | n/a | ● | ● | ● | ● optional | ● | ● | ● | ○ | PDF/JSON | ● | ● native | n/a | free + $6.99 |
| MPMB PDF | ● | ● | ● | ● | ◐ | ● | ○ | ◐ | ● | ◐ | ○ | ◐ SRD + scripts | ● scripts | 1/file | ○ | PDF | ● | ○ (Acrobat only) | ◐ beta | PWYW |
| Official 2024 paper | – | – | boxes | 6 boxes | – | checkboxes | – | – | ● + attunement | – | – | – | – | – | – | – | ● | – | ● | free |

Sources: as cited in §1.

---

## 3. What players actually need at the table, fast

### 3.1 Evidence

- **Rolls dwarf everything else.** DDB's 2023 telemetry: 1.9M players rolled dice **141 million** times in the app; **7 million** spells were cast; sheets were opened **80 million** times; 6M characters created; 13M searches (top terms "druid," "dragon"). ([2023 Unrolled](https://www.dndbeyond.com/posts/1648-2023-unrolled-a-look-back-at-a-year-of-adventure)) That is ~20 digital rolls per in-app spell cast and ~1.8 rolls per sheet open — and most tables still roll physical dice, so real roll counts are higher.
- **Combat sets the tempo.** Measured play: ~6–7 minutes per round, 4–6 rounds per encounter (Dec 2023–Jan 2024 data; one online table reported ~23 min/round). ([EN World timing thread](https://www.enworld.org/threads/length-of-combat-time-taken-per-round-collecting-data-from-my-games-updated-3-13-with-an-hour-30-minute-11-round-battle.701556/)) Every PC turn contains at least one attack-or-cast decision and usually a to-hit + damage pair.
- **Not knowing your own abilities is the top slowdown.** "By far the biggest drag factor in 5e's combat experience is the amount of player choice"; "There's a reason the most common phrase at our table is 'Read ALL the words!'"; tip: have the spell "description ready on their phone." ([Hipsters & Dragons](https://www.hipstersanddragons.com/fixing-slow-combat-5e/)) DDB forum consensus: "90% of the time it's player speed"; "players aren't paying attention/haven't read how their abilities work." ([DDB forum](https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/126790-why-does-combat-take-too-so-long-in-d-d))
- **What players ask the sheet for in combat**: "a big list of my actions and a big list of my spells," with resource spends (slot, sorcery points) adjacent. ([combat mode request](https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/170500-feature-request-combat-mode-for-web-ui)) The incumbent's own 2027 in-person app is scoped to "what your character can do… spells and Hit Points… conditions." ([DDB mid-year](https://www.dndbeyond.com/posts/2223-mid-year-update-d-d-beyonds-2026-development))
- **Revealed preference from Avrae**: the command surface players use during play is attack/action, cast, check, save, hp±, thp, slot±, counter±, death save, rest, effect. ([cheat sheet](https://hackmd.io/@pontifexexmachina/Hy9hkXBFt))
- **HP editing is the hottest, most fragile path**: DDB crashes "especially when modifying health"; Pathbuilder added an HP slider; DDB now paints "bloodied" at 50% and puts condition icons "inline with HP." ([Play Store](https://play.google.com/store/apps/details?id=com.fandom.playercompanion&hl=en_US&gl=US), [patch notes](https://pathbuilder2e.com/patchnotes.html), [changelog](https://www.dndbeyond.com/changelog))
- **Dice presets matter**: the top complaint about a 19k-review dice app is no "attack and damage" preset. ([RPG Simple Dice](https://play.google.com/store/apps/details?id=com.ccp.rpgsimpledice&hl=en_US))

### 3.2 Ranked list (per player, per 4-hour session; frequency estimates are inference from the tempo data above)

| Rank | Action | Typical frequency | Evidence | LP3 interaction budget |
|---|---|---|---|---|
| 1 | **Attack roll + damage** (one gesture) | 10–30 | 141M rolls; ~1 attack per turn × 4–6 rounds × 2–3 fights | 1 tap from Turn screen; show hit and damage together |
| 2 | **HP change** (damage taken / healing / temp HP) | 10–20 | crash reports on HP edit; bloodied indicators | Wheel = ±1, tap = ±5/±10 preset; never a keyboard |
| 3 | **Read a spell/feature text** | 5–15 | "Read ALL the words"; 13M searches | ≤2 taps to text; wheel scrolls; cross-links |
| 4 | **Skill check** | 5–15 | Avrae `!c`; exploration/social pillars | one list, one tap, adv/dis long-press |
| 5 | **Spell slot spend / cantrip cast** (casters) | 5–15 | 7M in-app casts; slot checkboxes in every tool | pips on the Turn screen; auto-decrement on cast |
| 6 | **Saving throw** | 3–10 | Avrae `!s`; save-based spells common | same list as skills |
| 7 | **Limited-use feature spend** (rage, ki, channel divinity…) | 3–10 | FC5 counters, DDB "Limited Use," Avrae `!cc` | pips; reset on rest |
| 8 | **Condition toggle / concentration** | 2–8 | Roll20 mods-on-toggle; DDB inline icons; Foundry statuses | checklist with one-line effect |
| 9 | **Short/long rest** | 1–3 | rest buttons in every tool; DDB rest bugs common | one button + confirm; resets counters/slots/HD |
| 10 | **Heroic inspiration** | 0–2 | 2024 sheet box; header toggle | single toggle in header |
| 11 | **Death saves** | 0–3 (rare, high stakes) | 6-checkbox pattern universal | auto-surface when HP = 0 |
| 12 | Currency/inventory edits | 2–6, mostly out of combat | party inventory demand on DDB | numeric only; no item creation |
| 13 | Level-up | 1 per 2–4 sessions | Advancement/level-up in all builders | defer to import/companion |

---

## 4. Prior art for constrained, monochrome and small screens

- **E-ink "tomes" (reMarkable 2 / Kindle Scribe).** An 89-page hyperlinked PDF: character details, 5 pages of spell lists, 10 of spell descriptions, equipment/treasure pages, and **56 pages of session notes**; "fully hyperlinked" so you jump between sections with taps. Tracking is by pen (write/erase), so mutable numbers (HP, slots) are handled as handwriting, not widgets — and the medium's sellers say nothing about what they cut because nothing is automated. ([reMarkable Character Tome](https://revivifygames.itch.io/remarkable-dd-character-tome)) Lesson: on a monochrome, low-refresh surface, **navigation links + generous whitespace** beat density; mutable state needs a cheap edit gesture.
- **Index cards and pocket cards.** A business-card sheet exists (2"×3.5"): "you will have to write small," "underline the ones you are proficient in," and the author concedes it "may not have all of the information" — it is a fallback for when you forget your sheet. ([business card sheet](https://olddungeonmaster.com/2016/02/12/dd5e-business-card-character-sheet/)) Index-card systems put "the game effects of each item right on the card," colour-code by kind (consumable/equipment/mundane), and treat inventory as a physical deck; downside: "constant recounting and worry about lost cards." ([EN World index cards](https://www.enworld.org/threads/managing-characters-with-index-cards.290721/)) Lesson: **one card = one action/item with its effect text inline** is the right granularity for a 3.9" screen.
- **Obsidian (mobile) sheets.** A 2024-rules sheet stores everything in YAML frontmatter (`health: {max, current, temp}`, `level1_1: true` for a used slot) and uses Meta Bind buttons/inputs to toggle slots, move spells between Known/Prepared, and auto-add a Concentration condition with damage-check reminders; it depends on a locally converted 5eTools corpus (`xphb`, `xdmg`). ([Obsidian sheet](https://github.com/mattclair/Obsidian-DnD-Character-Sheet)) Lesson: a **flat key-value state file + a static compendium** is enough; "prepared" is a set membership, slots are booleans.
- **CLI/TUI managers.** `ccvault` (2014 & 2024 rules, Tales of the Valiant): YAML characters ("human-readable… git-friendly"), `ccvault roll 2d6+5`, tracks HP, prepared spells, slots, levelling, inventory, in a Textual TUI. ([ccvault](https://github.com/climr-ai/ccvault)) `gobline`: a Bubble Tea terminal sheet, unfinished. ([gobline](https://github.com/dmcarman/gobline)) Lesson: text tools succeed when the *content* is pre-loaded and the user only types verbs and numbers — exactly the LP3 constraint.
- **Wearables / handhelds / calculators.** Pebble's RPG Dice Roller (2015) rolls "with the flick of your wrist" on a three-button watch; Playdate has a single "Dice Roller" listing; TI-83/84 dice programs are a long-standing genre. ([Pebble](https://apps.repebble.com/rpg-dice-roller_54b87939c61607d06a0000ea), [itch Playdate](https://itch.io/tools/tag-d20/tag-playdate), [ticalc](https://www.ticalc.org/pub/83plus/basic/programs/gametools/)) All of them cut everything except **die selection + modifier + result**, driven by up/down/select — i.e. a wheel and a button.
- **Light Phone community today.** Of 73 catalogued LP3 tools, there is no dice or RPG tool at all. ([awesome-light](https://awesome-light.garado.dev/))
- **Dice UX that generalises**: Dice Ex Machina's *pre-configured folders + Roll All* (speed by preparation) vs RPG Simple Dice's *no presets* (speed lost). ([DEM](http://www.between-worlds.com/dice-ex-machina-instructions), [RSD](https://play.google.com/store/apps/details?id=com.ccp.rpgsimpledice&hl=en_US))

---

## 5. Design implications for a 3.92" monochrome phone with a scroll wheel and painful text entry

Constraints in play (from the LP3 SDK notes in this project): three theme tokens, a 27×31 grid, `LightTextField` is display-only and real text entry is a *full-screen* editor with the LP3 keyboard, `LightFullscreenModal` is message+close only; tools must be open-source and non-commercial; the coming **File Manager** (WiFi drag-and-drop into per-tool directories, a "private inbox") is Light's sanctioned way to get data and tokens onto the phone; the Authenticator's **QR scanner** is already reused by community tools; HTTP is allowed (Weather). Hardware: 3.92" 1080×1240 AMOLED, touch + scroll wheel. ([Wikipedia LP3](https://en.wikipedia.org/wiki/Light_Phone_III))

### 5.1 Cut

- **The full builder.** Every tool that attempted a complete builder on a small surface either shipped data-entry pain (Fifth Edition Character Sheet, FC5's manual proficiencies) or stayed desktop (Pathbuilder web "only for desktop and larger tablets"). DDB's own answer for the constrained case is a *five-picker Quickbuilder*. Offer at most that: class → subclass (if L1–3) → species → background → assign standard array by wheel → name (one keyboard trip). No point-buy UI, no multiclass, no equipment picking beyond class defaults.
- **Homebrew editing, custom items, custom spells.** Free-text creation is the #1 complaint generator in indie apps and needs the keyboard. Accept homebrew only through import.
- **Inventory as a system**: weight/encumbrance, containers, party inventory, item creation. Keep a *read-only* equipment list with equip/attune toggles and a currency pad.
- **Campaign/party, maps, 3D dice, art, portraits, journals, backstory editing, notes editing.** (Imported notes can be *read*.)
- **Level-up on device** beyond "set level N and re-import" — Advancement is a tree of choices (Foundry's rebuild of it is a major release item); do it on the companion.

### 5.2 Keep (and what "keep" looks like)

- **Turn screen** (the missing "combat mode"): grouped by *Action / Bonus / Reaction / Other* (Shard's lens), each row a card — name, to-hit/DC, damage, one-line effect — with **one tap = roll to-hit and damage together** (Dice Ex Machina "Roll All"; RPG Simple Dice's missing "attack and damage"). Long-press = advantage/disadvantage (`2d20kh1`/`kl1`). Spells appear here *with* their slot pips so a cast decrements in place — fixing the DDB Actions/Spells split and the "three tabs to cast a quickened spell" complaint.
- **HP pad**: current/max/temp/hit dice on one screen; wheel = ±1, taps for ±5/±10, "damage"/"heal" verbs applied to temp first (Roll20/FG semantics); store *max* and *damage taken* separately (DDB `removedHitPoints`, FG "Wounds") so max can never be lost; show bloodied (≤50%) by weight, not colour; auto-open **death saves** (3+3 pips) at 0 HP.
- **Resources as pips**: one primitive `counter{name, value, max, reset: short|long|dawn|none}` covering spell slots (per level), pact slots, class features, item charges — FC5 `<counter reset L|S>`, Foundry `uses{value,max,recovery}`, Avrae `!cc` all agree. Tap = spend, wheel = adjust, rest = reset by type.
- **Conditions & concentration**: a checklist of the 15 conditions + exhaustion level (wheel) + a Concentrating-on-X line; each condition row has its one-sentence rule (Foundry "Ruletips," Roll20 modal descriptions). Monochrome distinction by weight/pips only.
- **Checks & saves**: one list, abilities → saves → skills grouped under abilities (the 2024 paper grouping that new players find readable), proficiency shown as a glyph; tap rolls with modifier; passive scores shown.
- **Rest**: two buttons with a confirm; short rest offers hit-dice spend via wheel.
- **Reader**: SRD 5.2.1 (CC-BY) spells/features/conditions/weapon-mastery bundled on device; every card links to its text; wheel scrolls long text. This directly addresses "Read ALL the words" and DDB's 13M searches.
- **Header**: name, class/level, AC · initiative · speed · proficiency · heroic-inspiration toggle.
- **Multi-character**: a list; switching is cheap because state is small.

### 5.3 Make one-tap

Attack (hit+damage), cast (with slot decrement), damage/heal by preset, spend a pip, toggle a condition, toggle inspiration, roll death save, roll initiative, start a rest. Everything else may take two taps; nothing should take a keyboard.

### 5.4 Import/companion path instead of on-device entry

1. **D&D Beyond snapshot** — user sets the character Public and the phone fetches `character-service.dndbeyond.com/character/v5/character/{id}?includeCustomItems=true` over WiFi. Input the ID by wheel (numeric, ~8 digits) or scan a QR of the sheet URL (reuse the Authenticator scanner). Parse `stats/bonusStats/overrideStats`, `classes[].level/hitDiceUsed`, `baseHitPoints/bonusHitPoints/overrideHitPoints/removedHitPoints/temporaryHitPoints`, `spellSlots/pactMagic`, `classSpells[].spells[].prepared`, `inventory[].equipped/isAttuned`, `currencies`, `conditions`, `deathSaves`, `inspiration`, and compute AC/skills from `modifiers.*`. Unofficial and unsupported — Beyond20's changelog shows the cost of live-binding — so treat it as a **one-shot import with local ownership afterwards**, and keep the parser isolated and versioned. ([API thread](https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/192944-api-questions), [scraper](https://github.com/MaximeBonnin/dnd-characters-dataset-24), [Beyond20 notes](https://beyond20.here-for-more.info/release_notes))
2. **File drop via the LightOS File Manager** — accept DDB JSON, Fight Club 5e XML (schema in §1.5), Foundry actor JSON (`system.*` paths in §1.3) and Pathbuilder-style JSON in the tool's directory; no cloud account, no browser on the phone required.
3. **Bundled content** — SRD 5.2.1 as the on-device compendium (CC-BY-4.0; classes, species, backgrounds, feats, spells, magic items, weapon mastery). Non-SRD subclass/spell *text* stays whatever the import carried (DDB JSON includes definitions inline), which keeps the tool clear of licensed content while still showing the player their own features. ([SRD post](https://www.dndbeyond.com/posts/1949-you-can-now-publish-your-own-creations-using-the))
4. **Export** — write the same normalised JSON back to the tool directory so a laptop can re-import; Avrae/Foundry-compatible fields are a bonus.

### 5.5 Data model sketch (normalised, edition-agnostic)

```
character: { id, name, edition: "2014"|"2024", level, classes[{name, subclass, level, hitDie, hitDiceUsed}],
  abilities: {str..cha: {score, save: prof|none}}, skills: {acr..: none|half|prof|expert}, prof,
  ac, initiative, speed, senses, size, inspiration,
  hp: {max, damage, temp, overrideMax}, deathSaves: {success, failure},
  conditions: [ids], exhaustion: 0..6, concentration: spellId|null,
  counters: [{id, name, value, max, reset: short|long|dawn|none, source}],   // includes spell slots
  actions: [{id, group: action|bonus|reaction|other, name, attack: {bonus, damage:[{formula,type}]}|null,
             save: {ability, dc}|null, uses: counterId|null, textRef}],
  spells: [{id, level, prepared, alwaysPrepared, ritual, concentration, textRef}],
  items: [{id, name, qty, equipped, attuned, textRef}], currency: {cp,sp,gp,ep,pp},
  notes: [{title, text}], source: {kind: ddb|fc5|foundry|manual, ref, importedAt} }
```
Rolls are `{formula, label, adv: none|adv|dis}` using Roll20-style notation so results are explainable.

### 5.6 Risks to name in the design doc

- The DDB endpoint can change without notice (v3 → v5 already happened; 2020 removals broke importers). ([removed endpoints](https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/71065-removed-undocumented-api-endpoints-regarding?page=8))
- Tool Library review favours tools useful to "a meaningful percentage of Light Phone users"; a D&D companion is niche — frame it as a *dice + reference + counter* tool with a 5e skin, and keep the generic primitives (counters, dice) usable without a character.
- DDB's own in-person app (2027) will set expectations for what "table companion" means; the LP3 version wins on calm, offline and speed, not breadth.

---

## Sources

### D&D Beyond
- Changelog — https://www.dndbeyond.com/changelog
- 2026 development roadmap — https://www.dndbeyond.com/posts/2132-d-d-beyonds-2026-development-roadmap
- Mid-year 2026 roadmap update (2027 in-person mobile app) — https://www.dndbeyond.com/posts/2223-mid-year-update-d-d-beyonds-2026-development
- 2025 wrap-up — https://www.dndbeyond.com/posts/2116-d-d-beyond-2025-wrap-up
- Behind the Screen: Quickbuilder — https://www.dndbeyond.com/posts/2135-behind-the-screen-the-new-quickbuilder-and-future
- Bell of Lost Souls on Quickbuilder — https://www.belloflostsouls.net/2026/03/dd-beyond-launches-new-character-quickbuilder-for-first-level-follies.html
- Builder Sections — https://dndbeyond-support.wizards.com/hc/en-us/articles/7747202748436-Builder-Sections
- Sheet Sections — https://dndbeyond-support.wizards.com/hc/en-us/articles/7747193946388-Sheet-Sections
- Character Header — https://dndbeyond-support.wizards.com/hc/en-us/articles/7747193980820-Character-Header
- Mobile app support article — https://dndbeyond-support.wizards.com/hc/en-us/articles/7747193137684-D-D-Beyond-Mobile-App
- Subscriptions pricing — https://dndbeyond-support.wizards.com/hc/en-us/articles/7747225116820-Subscriptions-Pricing
- Subscribe page (tier features) — https://www.dndbeyond.com/en/subscribe
- Google Play listing/reviews — https://play.google.com/store/apps/details?id=com.fandom.playercompanion&hl=en_US&gl=US
- App Store listing/reviews — https://apps.apple.com/us/app/d-d-beyond/id1501810129
- 2023 Unrolled statistics — https://www.dndbeyond.com/posts/1648-2023-unrolled-a-look-back-at-a-year-of-adventure
- API questions (staff: no public API; v5 URL) — https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/192944-api-questions
- Documentation for JSON file (2017) — https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/11540-documentation-for-json-file
- Removed undocumented endpoints (v3, public requirement, HP fields) — https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/71065-removed-undocumented-api-endpoints-regarding?page=8
- Character scraper using v5 endpoint — https://github.com/MaximeBonnin/dnd-characters-dataset-24
- ddb-importer (Foundry) — https://github.com/MrPrimate/ddb-importer
- Combat mode feature request — https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/170500-feature-request-combat-mode-for-web-ui
- Spells vs Actions tabs — https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/98877-spells-actions-and-beyond-oh-my
- Slow load times (2026) — https://www.dndbeyond.com/forums/d-d-beyond-general/bugs-support/239247-slow-load-time-for-character-sheets
- Offline failures — https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/d-d-beyond-mobile-app-feedback/115301-cant-view-any-characters-offline
- 2024 ruleset toggle complaints — https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/207343-toggle-2024-ruleset-on-character-builder
- Roadmap community thread — https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/236560-d-d-beyond-2026-development-roadmap
- Official character sheet downloads — https://www.dndbeyond.com/resources/1779-d-d-character-sheets
- SRD 5.2/5.2.1 CC-BY announcement — https://www.dndbeyond.com/posts/1949-you-can-now-publish-your-own-creations-using-the
- Why combat takes long (forum) — https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/126790-why-does-combat-take-too-so-long-in-d-d
- CBR import guide — https://www.cbr.com/dnd-beyond-alternatives-import-guide/

### Roll20
- 2024 character sheet help article — https://help.roll20.net/hc/en-us/articles/30748164251287-Dungeons-Dragons-2024-Character-Sheet
- 2024 sheet & builder beta blog — https://blog.roll20.net/posts/dd-2024-character-sheet-and-builder-now-in-beta-on-roll20/
- StartPlaying guide to the 2024 sheet — https://startplaying.games/blog/posts/how-to-use-roll20-one-dnd-2024-character-sheet
- Charactermancer wiki — https://wiki.roll20.net/Charactermancer
- Mobile app retirement — https://blog.roll20.net/posts/were-retiring-the-roll20-mobile-app-to-build-something-better-heres-why/
- Dice Reference — https://help.roll20.net/hc/en-us/articles/360037773133-Dice-Reference
- Price increase announcement — https://bloghub.roll20.net/posts/roll20-pricing-increase-announcement/
- Subscription wiki — https://wiki.roll20.net/Subscription

### Foundry VTT
- dnd5e package page — https://foundryvtt.com/packages/dnd5e
- dnd5e releases — https://github.com/foundryvtt/dnd5e/releases
- Roll Formulas wiki (data paths) — https://github.com/foundryvtt/dnd5e/wiki/Roll-Formulas
- DeepWiki data models — https://deepwiki.com/foundryvtt/dnd5e/2-data-models
- Actors (export/import) — https://foundryvtt.com/article/actors/
- Player's Handbook (2024) module — https://foundryvtt.com/packages/dnd-players-handbook

### Fantasy Grounds
- 5E character sheet — https://fantasygroundsunity.atlassian.net/wiki/spaces/FGCP/pages/996641917
- 2024 vs Legacy — https://fantasygroundsunity.atlassian.net/wiki/spaces/FGCP/pages/2697691149/5E+50th+Anniversary+2024+vs.+5E+Legacy+2014
- Steam page — https://store.steampowered.com/app/1196310/Fantasy_Grounds_Unity/

### Fight Club 5e / Game Master 5e
- FC5 App Store — https://apps.apple.com/us/app/fight-club-5th-edition/id901057473
- FC5 Google Play — https://play.google.com/store/apps/details?id=com.lionsden.fc5&hl=en_US
- GM5 App Store — https://apps.apple.com/us/app/game-master-5th-edition/id908176026
- FightClub5eXML README — https://raw.githubusercontent.com/kinkofer/FightClub5eXML/main/README.md
- FightClub5eXML Sources README — https://github.com/kinkofer/FightClub5eXML/blob/main/Sources/README.md
- compendium.xsd — https://raw.githubusercontent.com/kinkofer/FightClub5eXML/main/Utilities/compendium.xsd
- 5.5e fork — https://github.com/vidalvanbergen/FightClub5eXML
- EN World mobile apps thread — https://www.enworld.org/threads/useful-mobile-apps-for-5e.447580/

### Other builders/sheets
- DiceCloud v2 design — https://www.patreon.com/posts/version-2-and-26326856
- DiceCloud overview — https://harpscorp.com/dicecloud-digital-character-management/
- Shard offer/pricing/2024 — https://www.shardtabletop.com/offer
- Shard community thread — https://www.enworld.org/threads/black-flag-and-tales-of-the-valiant-on-shard-tabletop.704288/
- Demiplane D&D Nexus announcement — https://www.demiplane.com/blog/announcing-d-d-nexus---part-of-the-connected-d-d-experience-on-roll20
- Demiplane Q1 2025 review — https://www.demiplane.com/blog/demiplane-q1-2025-review
- D&D Nexus reactions — https://www.enworld.org/threads/d-d-nexus-at-demiplane-with-wotc-core-books.716176/
- Roll20–Demiplane integration beta — https://www.enworld.org/threads/roll20-demiplane-integration-beta-is-live.713151/
- Alchemy D&D page — https://alchemyrpg.squarespace.com/dungeonsanddragons
- Alchemy creating a character — https://help.alchemyrpg.com/en/articles/9821403-creating-a-character
- ddb2alchemy — https://github.com/alchemyrpg/ddb2alchemy
- DM's Vault known issues — https://www.dungeonmastersvault.com/help/known-issues-site-limitations/
- Orcpub source — https://github.com/Orcpub/orcpub
- StartPlaying character-maker roundup — https://startplaying.games/blog/posts/every-character-maker-dnd
- Aurora Builder — https://aurorabuilder.com/
- 5e Companion App (App Store) — https://apps.apple.com/us/app/5e-companion-app/id1507254451
- Fifth Edition Character Sheet (Play) — https://play.google.com/store/apps/details?id=com.wgkammerer.testgui.basiccharactersheet.app&hl=en_US
- DungeonSolvers app roundup — https://www.dungeonsolvers.com/dnd-character-sheet-apps/
- EN World "best character sheet app" — https://www.enworld.org/threads/best-character-sheet-app.612209/
- Hero Lab Online — https://www.wolflair.com/hlo/
- Hero Lab 5e (2016) — https://www.enworld.org/threads/herolabs-5e-update-is-here.663579/
- Pathbuilder 2e site — https://pathbuilder2e.com/
- Pathbuilder patch notes — https://pathbuilder2e.com/patchnotes.html
- Pathbuilder App Store — https://apps.apple.com/us/app/pathbuilder-2e/id6740821064
- Pathbuilder Google Play — https://play.google.com/store/apps/details?id=com.redrazors.pathbuilder2e&hl=en_US
- Pathbuilder feature overview — https://groupfinder.gg/library/pathbuilder-2e
- Adventurer's Codex — https://github.com/adventurerscodex/adventurerscodex
- MPMB FAQ — https://www.flapkan.com/faq
- MPMB site — https://www.flapkan.com/
- MPMB releases — https://github.com/morepurplemorebetter/MPMBs-Character-Record-Sheet/releases
- AlternativeTo DDB alternatives — https://alternativeto.net/software/dndbeyond/

### Avrae, Beyond20, Owlbear
- Avrae cheat sheet (HackMD) — https://hackmd.io/@pontifexexmachina/Hy9hkXBFt
- Avrae commands (DW guide) — https://dndworld.github.io/start-playing/avrae/
- Avrae pc_combat.rst — https://github.com/avrae/avrae/blob/master/docs/cheatsheets/pc_combat.rst
- Avrae get started — https://avrae.readthedocs.io/en/latest/cheatsheets/get_started.html
- Beyond20 repo — https://github.com/kakaroto/Beyond20
- Beyond20 release notes — https://beyond20.here-for-more.info/release_notes
- Owlbear extensions — https://extensions.owlbear.rodeo/
- Sheet from Beyond — https://extensions.owlbear.rodeo/sheet-from-beyond
- Owl20 — https://owl20.uberdragon.org/

### Official sheets
- 2024 sheets available (EN World) — https://www.enworld.org/threads/2024-d-d-character-sheets-available-to-download.706499/
- 2024 sheet opinions (EN World) — https://www.enworld.org/threads/2024-d-d-character-sheet-what-do-you-think.704868/page-12
- 5.5 fillable recreation (layout) — https://olddungeonmaster.com/2025/01/19/dd-5-5-character-sheet-fillable-and-auto-calculating/

### Initiative/encounter tools and dice apps
- Improved Initiative repo — https://github.com/cynicaloptimist/improved-initiative
- Encounter builders/initiative trackers review — https://homebrewcreation.com/reviews/best-dnd-encounter-builders-initiative-trackers/
- Kobold Fight Club update — https://www.enworld.org/threads/update-on-kobold-fight-club.682231/
- Kobold+ Fight Club — https://koboldplus.club/
- RPG Simple Dice — https://play.google.com/store/apps/details?id=com.ccp.rpgsimpledice&hl=en_US
- Dice Ex Machina instructions — http://www.between-worlds.com/dice-ex-machina-instructions

### Table tempo and behaviour
- Combat length data — https://www.enworld.org/threads/length-of-combat-time-taken-per-round-collecting-data-from-my-games-updated-3-13-with-an-hour-30-minute-11-round-battle.701556/
- Fixing slow combat — https://www.hipstersanddragons.com/fixing-slow-combat-5e/
- CritRoleStats d20 analysis — https://www.critrolestats.com/blog/d20-roll-analysis-with-graphs
- CritRoleStats Wildemount stats — https://www.critrolestats.com/stats-wm

### Constrained-screen prior art
- reMarkable D&D Character Tome — https://revivifygames.itch.io/remarkable-dd-character-tome
- Business-card character sheet — https://olddungeonmaster.com/2016/02/12/dd5e-business-card-character-sheet/
- Index cards for characters — https://www.enworld.org/threads/managing-characters-with-index-cards.290721/
- Obsidian D&D sheet — https://github.com/mattclair/Obsidian-DnD-Character-Sheet
- ccvault CLI — https://github.com/climr-ai/ccvault
- gobline TUI — https://github.com/dmcarman/gobline
- Pebble RPG Dice Roller — https://apps.repebble.com/rpg-dice-roller_54b87939c61607d06a0000ea
- Playdate d20 tools — https://itch.io/tools/tag-d20/tag-playdate
- TI-83/84 game tools — https://www.ticalc.org/pub/83plus/basic/programs/gametools/
- awesome-light (LP3 community tools) — https://awesome-light.garado.dev/
- Light Phone III (Wikipedia) — https://en.wikipedia.org/wiki/Light_Phone_III

### Content APIs
- Open5e API docs — https://open5e.com/api-docs
