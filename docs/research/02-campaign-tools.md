# Campaign documentation, session journaling, and worldbuilding tools — a survey to inform a player-side journal for the Light Phone 3

*Research report 02 · D&D 5e companion for LP3 · compiled 29 Aug 2026*

## 0. Scope, method, and the one-sentence conclusion

This report surveys the tools tabletop players and DMs actually use to remember their campaigns — local-first knowledge bases (Obsidian), hosted worldbuilding wikis (Kanka, World Anvil, LegendKeeper, Scabard, Chronica), session-first loggers (Campaign Logger, the late Realm Works), general-purpose stacks (Notion, Google Docs/Sheets, paper), VTT-embedded journals (Foundry, Roll20, D&D Beyond), the 2025–26 wave of AI recap tools, fantasy-calendar tooling, and the video-game quest-log conventions those tools borrow from. About sixty pages were fetched; every non-obvious claim carries a URL, and where a page could not be verified that is said explicitly.

The target is not any of these tools. It is a **player's** journal on a 3.92" monochrome-by-discipline phone with a physical scroll wheel, no browser, and a full-screen keyboard that makes long typing painful. The screen is roughly 411×472 dp, "short and nearly square," and the SDK's only real text-entry surface is `LightTextInputEditor`, a full-screen mode with the LP3 keyboard that a read-only `LightTextField` opens on tap (project doc `claude/lp3-learning-path.md`). That single fact reorganises the whole survey: everything below is read through the lens of "how few characters does this require, and how much of it can be a tap or a wheel-turn instead."

**The one-sentence conclusion:** every serious tool converges on the same five nouns — *who, where, what-quest, what-loot, when (session)* — joined by a single cheap link primitive (an `@`-mention or `[[wikilink]]`), and the player-side literature says players need far less than DMs; so the LP3 journal should be a **session-stamped log of short lines that attach to a small roster of named things**, with authoring of anything longer than a line pushed to an export.

---

## 1. Tool-by-tool survey

### 1.1 Obsidian and the TTRPG plugin ecosystem (local-first Markdown)

**Entity model.** Obsidian has no entity model; the community imposes one with folders + YAML properties + templates. The canonical DM template repos use a `type:` property to discriminate note kinds. SONDLecT's templates ship seven kinds — NPC, adventure/campaign, item, session-notes, location, player-character, organization — each with "dataview annotated fields, allowing for the creation of a usable database" ([github.com/SONDLecT/obsidian-dm-templates](https://github.com/SONDLecT/obsidian-dm-templates)). The raw NPC template's frontmatter is just `type: "npc"`, `tags: [dnd, npc]`, `date_added`, with body inline-fields for summary, campaign, aliases, alignment, organization, location, race, gender, class, and a "COGAS" block (Color, Occupation, Goals, Attitude, Stake); it closes with a Dataview query — `TABLE summary AS "Session Summary" FROM #session-notes AND [[<%tp.file.title%>]]` — that lists every session the NPC appeared in ([raw npc.md](https://raw.githubusercontent.com/SONDLecT/obsidian-dm-templates/main/npc.md)). The session-notes template's frontmatter is `type: session-notes`, `tags`, `date_created`, `dnd_session_date`, and a `summary` array, with body sections for players/campaign/location/music, then prep sections (strong opening, scenes, secrets and clues, locations, NPCs ×3, monsters, rewards) ([raw session-notes.md](https://raw.githubusercontent.com/SONDLecT/obsidian-dm-templates/main/session-notes.md)). That Dataview pattern — *NPC page automatically lists the sessions that mention it* — is the single most-copied idea in the Obsidian TTRPG world and the one worth stealing.

dclasair's `ttrpg-campaign-vault` is explicitly split into a private **DM Info** tree (campaign outline, adventures, sessions, secrets, rules) and a shareable **Player Info** tree (world, characters incl. NPCs, monsters, magic items, maps, session notes, rules). It recommends at most three properties per note, uses `[[NPC Name]]` links, and by 2026 uses **Bases** ("the term Obsidian uses for Databases") for its NPC table; player folders are shared via Syncthing, Obsidian Sync, or a cloud drive ([github.com/dclasair/ttrpg-campaign-vault](https://github.com/dclasair/ttrpg-campaign-vault)). cacharbe's session-notes plugin generates a note from a Handlebars template with `type: session, campaign, sessionNum, location, date`, plus `fc-date`/`fc-category` for the calendar plugin and `long_rest`/`short_rest` flags ([github.com/cacharbe/obsidian-ttrpg-session-notes](https://github.com/cacharbe/obsidian-ttrpg-session-notes)).

**Plugins.** The Obsidian Hub's TTRPG list is short: Dice Roller, Fantasy Statblocks, Initiative Tracker, Fantasy Calendar, Leaflet (interactive maps in notes), RPG Manager, Quick Monsters 5e ([obsidian-hub Plugins for TTRPG](https://raw.githubusercontent.com/obsidian-community/obsidian-hub/main/02%20-%20Community%20Expansions/02.01%20Plugins%20by%20Category/Plugins%20for%20TTRPG.md)). The first four plus Leaflet are Javalent's "Fantasy System" family ([plugins.javalent.com](https://plugins.javalent.com)); PhD20's beginner guide recommends exactly Dataview, Initiative Tracker, Fantasy Statblocks, Leaflet, and Calendarium ([phd20.com](https://phd20.com/blog/getting-started-with-obsidian-dnd/)). Note that statblocks, initiative and maps are all **DM-side**; none of them matter to a player journal.

**Linking.** `[[wikilinks]]`, backlinks, and "unlinked mentions" that catch names you forgot to link ([phd20.com](https://phd20.com/blog/getting-started-with-obsidian-dnd/)). The GM Assistant guide's rule of thumb — "If something might matter again, give it a note and link to it" — and its warnings (over-engineering the vault before play, writing *unlinked* session summaries) are the best short statement of the Obsidian workflow ([gmassistant.app](https://gmassistant.app/blog/using-obsidian-for-ttrpg-notes-part-1)). Josh Plunkett's Patreon posts on Dataview and "why Obsidian" exist but are behind a paywall and were not read ([patreon.com/posts/70180876](https://www.patreon.com/posts/70180876)).

**2025–26 change: Bases.** Obsidian 1.9 (May 2025) introduced Bases — native, no-code, properties-only table views stored as `.base` files — which the community is now treating as the Dataview replacement for simple lists; it "only queries data stored in properties" and is "incredibly fast" ([obsidian.rocks](https://obsidian.rocks/dataview-vs-datacore-vs-obsidian-bases/); [alternativeto news, May 2025](https://alternativeto.net/news/2025/5/obsidian-1-9-0-introduces-bases-plugin-for-database-style-note-management)). The practical consequence for anyone exporting to Obsidian in 2026: **put the structured fields in YAML properties, not inline `key:: value` fields**, or Bases can't see them.

**Export / offline / mobile / price.** Plain Markdown on disk; fully offline; free for personal use; mobile apps exist but every TTRPG guide surveyed assumes a laptop. The LP3 has no Obsidian and the Light community's request for "Obsidian vault sync" has "zero replies" (project doc `claude/lp3-tool-register.md`).

### 1.2 Kanka (hosted, the most explicit entity model)

Kanka is the clearest specification of what a campaign "is." Entity types are **fixed** — "adding your own types isn't possible" — and every entity shares "a name, type, an entry field to describe the entity, an image, and tags" ([docs.kanka.io entities](https://docs.kanka.io/en/latest/entities/overview.html)). The features page lists roughly 20 modules: characters, locations, families (with family trees on premium), items, organisations, quests, journals ("Plan your session or write a session recap in the eyes of a character"), calendars, timelines, maps, events, abilities, plus cross-cutting relations, attributes/"properties" ("Track HP, Level, Strength"), per-entity inventories, `@`-mention linking in the editor, and role-based access ("control exactly what they can see") ([kanka.io/features](https://kanka.io/features)).

Field-level detail from the API:

- **Character:** `name, title, entry, age, sex, pronouns, type, races[], families[], locations, traits (personality + appearance), status_id, is_private, is_personality_visible, tags[], image` ([api-docs characters](https://app.kanka.io/api-docs/1.0/characters)).
- **Journal:** `name, entry, type (e.g. "Session"), date, author_id, parent_id, is_private, tags[], image, calendar_id, calendar_year/month/day, calendar_event_length` ([api-docs journals](https://app.kanka.io/api-docs/1.0/journals)). A journal is literally "a session recap with a date and an author" — the closest thing in any tool to the player's session log.
- **Quest:** nested via `parent quest`, a `date`, a `completed` flag, and an **Elements** subpage where characters, locations, events, items and notes are attached each with a free-text **Role** that "groups and sorts elements" ([docs.kanka.io quests](https://docs.kanka.io/en/latest/entities/quests.html)).
- **Calendar:** months, weekdays, years, seasons, moons; entities attach to dates through *reminders*; an "advancing date option … advances the day by one every night"; the docs admit calendars are "the most complicated and complex feature of Kanka"; no Harptos preset is documented ([docs.kanka.io calendars](https://docs.kanka.io/en/latest/entities/calendars.html)).

**Visibility.** Five levels: `all`, `admin`, `admin-self`, `self` (only `created_by`), `members` ([api-docs visibilities](https://app.kanka.io/api-docs/1.0/misc/visibilities)), plus per-entity permission endpoints. This is the reference design for "player can write private notes on a DM-owned object."

**Pricing (2026).** Kobold free with unlimited entries; Owlbear $4.99/mo (1 premium campaign), Wyvern $9.99, Elemental $24.99; standard campaigns are capped at 10 members ([kanka.io/pricing](https://kanka.io/pricing)). **2025–26 changes:** a steady monthly cadence — whiteboards (3.5, Nov 2025), campaign-wide "connections web" (3.7, Dec 2025), a new text editor and "terminology simplification for new users" (3.9, Mar 2026), a category-list rework (3.10), merged search (3.12), "Maps v4" (3.14, Jul 2026), and 3.15 on 26 Aug 2026 ([blog.kanka.io/tag/release](https://blog.kanka.io/tag/release/)). Nothing in that list touches journals or quests: the entity model is stable. No offline mode; browser-only.

### 1.3 World Anvil (hosted, article-centric, richest *player* features)

World Anvil's unit is the *article* with typed templates, and its differentiator is the **player workflow**. Players create "Heroes" (characters) inside a campaign; the GM can edit the sheet but "cannot access player journal entries." Players get four private-ish surfaces: **Journals** (session summaries or in-character diary, with backdatable custom dates), a **Notebook** ("100% private," unstyled text), **Memories** (private annotations pinned to specific world articles), and a **Scrapbook** visible to the GM but not other players ([Player Workflow, WA Codex](https://www.worldanvil.com/w/WorldAnvilCodex/a/player-workflow)). The marketing page adds "capture session notes & clues for later" as a player feature ([worldanvil.com/player](https://www.worldanvil.com/player)).

**Secrets.** A secret has a title, BBCode content, and optional *subscriber groups*; by default "secrets are only visible to the person who created them"; they are inserted into articles with BBCode tags and appear as a tab visible to anyone who can view the article, so players can "reference this information in future sessions" once revealed ([Secrets, WA Codex](https://www.worldanvil.com/w/WorldAnvilCodex/a/secrets)). **Memories-on-articles** is the pattern to note: the player annotates the DM's object rather than duplicating it.

**Pricing.** Free tier: 2 worlds, 5 articles, 2 maps, 2 timelines, 100 MB; Master $4.50/mo, Grandmaster $8.25/mo (custom templates, API), Sage $25/mo; lifetime options ([worldanvil.com/pricing](https://www.worldanvil.com/pricing)). Browser-only; no offline.

### 1.4 LegendKeeper (hosted, wiki + atlas + secrets)

Pages with `@`-mentions, automatic link detection, `[[New Page]]` creation, hover previews, page templates and properties, an atlas with pins bound to pages, whiteboards, and per-user share permissions; "Secrets" are hidden blocks inside otherwise-shared pages ([phd20.com LegendKeeper guide](https://phd20.com/blog/getting-started-with-legendkeeper-dnd/)). Editing works offline and syncs on reconnect; "LK is not technically mobile-ready yet"; export is full HTML or JSON ([legendkeeper.com/faq](https://www.legendkeeper.com/faq/)). Pricing: one Pro tier, $9/mo or $90/yr; a free Basic tier can view, export and collaborate; "an unlimited number of guests can participate in your projects for free. Only the project owner needs an active subscription" ([legendkeeper.com/pricing](https://www.legendkeeper.com/pricing/)). **2025–26:** moons for calendars (Nov 2025), meter blocks (Dec 2025), a major new map tool with multiplayer cursors (0.18, Jan 2026), a full UI rebuild and URL migration (0.19, Jun 2026), infobox/properties blocks (0.19.1, Aug 2026); still labelled Beta and timelines are still "pre-" ([legendkeeper.com/changelog](https://www.legendkeeper.com/changelog/)).

### 1.5 Scabard, Chronica, Campfire, Fantasia Archive (the long tail)

- **Scabard** — wiki-style pages for characters/NPCs, places, organizations, events, family trees; a "Proper Noun Detector" auto-links names (reviewer: "a dangerous rabbit hole"); pages hide from players via checkbox; no player commenting; free to $19.95/mo with "many key features … pay-walled" ([Worlds by Wally review, Jun 2025](https://worldsbywally.com/2025/06/01/scabard-product-review/); [scabard.com](https://www.scabard.com/world-building/learn-more)).
- **Chronica** — characters (NPCs, PCs, mounts, companions), "Kinships" (factions/families/guilds), nested places, encounters, **Adventure Notes** marked "public or private," a hierarchical **Quest Log** with quests "marked secret … until discovered," and **Developments** (plot points that render as a timeline on related profiles); custom calendars; free tier plus Knight/Monarch/Deity ([chronica.ventures/features](https://chronica.ventures/features)).
- **Campfire** — 18 modules (characters, relationships, timeline, encyclopedia, maps, calendar, systems…), browser + desktop + mobile, aimed at novelists rather than tables; the page gives no pricing ([campfirewriting.com/write](https://www.campfirewriting.com/write)).
- **Fantasia Archive** — "100% free," offline, desktop-only; latest release v0.1.15; effectively a hobby project ([fantasiaarchive.com](https://fantasiaarchive.com/)).

### 1.6 Campaign Logger (Johnn Four; session-first, tag-driven)

Campaign Logger is the only mainstream tool whose *primary object is the log entry*, not the wiki page. Entries are free-form Markdown, autosaved "every character as you type," with `@`-mention tagging of "any NPC, location, item, plot, faction, idea, or other detail" — "the automagic tagging lets you link to pages and create pages instantly without slowing down," with autosuggest so "you type each name in just once" ([roleplayingtips.com/campaign-logger](https://www.roleplayingtips.com/campaign-logger/); [campaign-logger.com](https://campaign-logger.com/)). The FAQ confirms multi-word tags with distinct sigils — `@"Character Tag"` and `#"Location Tag"` ([Campaign Logger FAQ](https://www.roleplayingtips.com/campaign-logger-faq/)); a Roleplaying Tips article says `%` is used for dates/timeline in the same syntax ([roleplayingtips.com/tag/campaign-logger](https://www.roleplayingtips.com/tag/campaign-logger/)). A public "vNext cheat sheet" lists the full sigil set but sits behind a forum login and could not be verified here ([campaign-community.com](https://campaign-community.com/index.php?resources/campaign-logger-vnext-cheat-sheet.254/)). Export: text, JSON, PDF. Browser-based with "no offline functionality"; "writing works best on computers and tablets." Players share the DM's login ("they'll have access to all your notes"); a player mode is only "planned" ([FAQ](https://www.roleplayingtips.com/campaign-logger-faq/)). Pricing: free (1 campaign, 25 logs), $5/mo or $4/mo annual ([campaign-logger.com](https://campaign-logger.com/)).

The design lesson is precise: **a sigil-prefixed name is a complete entity record**. Typing `@Merrick` in a log line both links and *creates* Merrick. There is no form.

### 1.7 Realm Works (dead — what people miss)

Lone Wolf ceased development in 2019 ([tenkarstavern.com](https://www.tenkarstavern.com/2019/09/hero-labs-lone-wolf-downsizes-ceases.html)). The 5MWD review explains why it is still mourned: topics in categories (cities, factions, NPCs, events) with **snippets** that could be revealed *individually* to players — an assassination "can remain hidden until discovered, then details unfold progressively" — plus automatic cross-linking and a dedicated Player View window. The same review lists the reasons it died: $50 with no demo, a $9.99 Player Edition, Windows-only, crash-prone, and links only regenerated on edit ([5mwd.com](https://www.5mwd.com/archives/3370)). Every "secrets" feature in LegendKeeper, World Anvil, Chronica and Monk's Enhanced Journal is a descendant of Realm Works' snippet-level reveal. For a *player* journal, the mirror-image idea is what matters: the player's record of an NPC is a **growing stack of dated snippets**, not a single evolving paragraph.

### 1.8 Notion templates and Google Docs/Sheets

Notion's D&D templates all share one shape: a handful of relational databases. Sly Flourish's Lazy DM template uses a Character database (player skills, passive scores, languages) and one **Campaign database** in which NPCs, villains, items and locations are rows discriminated by a `type` tag, with `@`-mention linking and a session template generated from the eight Lazy-DM steps; he flags that Notion "has no support for running offline" and that Markdown/CSV export "loses filtered views and database features" ([slyflourish.com](https://slyflourish.com/lazy_dnd_with_notion.html)). The 2026 Minva ranking lists Lorekeeper ($17.99, DM-side: Adventures, Locations, NPCs & Monsters, Treasures, Character Profiles, Worldbuilding, Random Tables), **Character Compendium** ($9.99, *player*-side: Character Sheet, Backstory, Inventory/Equipment, Spells, **Session Notes**), Ultimate TTRPG Planner and Tome of Organization ([minvarpg.com](https://minvarpg.com/blogs/ttrpg/best-notion-templates-dnd)); the free Broken Barrel "D&D 5E Campaign Database" (4.95/5, 36 ratings) links "characters, locations, items, and even initiative rolls" ([notion.com](https://www.notion.com/templates/dnd5e-campaign-database)). Note that the only *player* template in that list has exactly five tables and one of them is session notes.

For loot, groups overwhelmingly use a shared spreadsheet or a paper ledger. The EN World thread on party loot tracking shows the consistent columns: item, description, unique item number, "who's currently carrying," session/source, value in gp, and status (party treasury vs individual), with a designated "party treasurer" or the DM keeping a "mirror track" because "few if any of them being any good at records management" ([enworld.org loot thread](https://www.enworld.org/threads/help-w-party-loot-tracking.668536/)). Google Docs' offline mode and headings are cited by players precisely because they survive a dead Wi-Fi at the table ([dndbeyond forum thread](https://www.dndbeyond.com/forums/dungeons-dragons-discussion/tips-tactics/15431-how-to-take-notes-efficiently-as-a-player)).

### 1.9 Paper: index cards, notebooks, and the official Player's Campaign Journal (Nov 2025)

Sly Flourish's ten index-card uses include three that are *handed to players*: quest cards ("the quest, the goal, and any notes"), magic-item cards, and secrets/clues cards "when their characters discover the clue"; cards "reinforce the style of improvisational play" and make information "feel more real" ([slyflourish.com index cards](https://slyflourish.com/ten_ways_to_use_index_cards.html)). Scriv the Bard recommends "separate item cards for goods that require additional details" so the main notes stay clean ([scrivthebard.com](https://scrivthebard.com/2019/04/10/dd-note-taking-tips/)). A two-year notebook retrospective concluded the book was "a memory artifact rather than a rulebook" and that the next one should be "a lot smaller" ([rileyadammmson.itch.io](https://rileyadammmson.itch.io/rilesblog/devlog/1112410/retiring-a-gaming-notebook-2023-2025)).

The most on-point 2025 artefact is **The Player's Campaign Journal (Dungeons & Dragons)**, Clarkson Potter, 18 Nov 2025 — an official, player-facing paper journal with "prompts, indexes, and templates" for tracking party members, NPCs, factions, creatures and memorable moments, plus Bastion material for the 2024 rules ([mitpressbookstore.mit.edu](https://mitpressbookstore.mit.edu/book/9798217034321)). That Wizards' licensee chose *party members, NPCs, factions, creatures, moments* as the player's index set is a useful external validation of the entity list in §3.

### 1.10 VTT-embedded journals: Foundry, Monk's Enhanced Journal, Roll20, D&D Beyond

**Foundry VTT** journal entries are multi-page (text, image, video, PDF) with four ownership levels — None, Limited, Observer, Owner — a "Show Players" push, and `@UUID[…]` content links; the core docs say nothing about players' private notes ([foundryvtt.com/article/journal](https://foundryvtt.com/article/journal/); [users](https://foundryvtt.com/article/users/)). **Monk's Enhanced Journal** adds the typed entries everyone wanted: Person and Place (image, properties, description, *GM-private notes*), Organization, Encounter, **Quest** (objectives, rewards, colour-coded status; players see only in-progress objectives), Checklist, Shop, Loot (currency splitting), Slideshow ([README](https://github.com/ironmonk108/monks-enhanced-journal/blob/main/README.md)); a v13 community fork exists ([therealguy90/monks-enhanced-journal-v13](https://github.com/therealguy90/monks-enhanced-journal-v13)).

**Roll20** handouts have name, image, "Description & Notes" (player-visible) and "GM Notes" (GM-only), with two separate access lists — "In Player's Journals" (view) and "Can Be Edited & Controlled By" (edit) — and only GMs can create them ([wiki.roll20.net/Handout](https://wiki.roll20.net/Handout)). A player request for private notes was closed by Roll20 for lack of votes; the workarounds are the character bio tab or a GM-created editable handout ([roll20 forum](https://app.roll20.net/forum/post/2550355/player-notes-o-the-journal)).

**D&D Beyond** gives the player a Notes area on the character ("Organizations, Allies, Enemies, Backstory, etc.") ([dndbeyond-support](https://dndbeyond-support.wizards.com/hc/en-us/articles/7747202748436-Builder-Sections)) and a campaign-level public/private notes field that DMs report "just got too unwieldy to manage" after a few sessions, pushing groups to Google Drive, OneNote or Discord ([dndbeyond forum](https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/116008-campaign-notes)). The pattern across all three VTTs is the same: **the platform of record has no first-class player journal, so players carry one elsewhere** — which is exactly the niche the LP3 tool would occupy.

### 1.11 AI session-recap tools (2025–26)

- **Archivist** ingests Discord live audio, uploaded audio (single or multi-track), play-by-post chat, transcripts, raw notes and journals, and within ~10 minutes produces summaries, **entities** (characters, locations, factions, items), highlights and a searchable timeline, plus an "Ask Archivist" bot ([myarchivist.ai/how-it-works](https://www.myarchivist.ai/how-it-works)). An April 2025 review reports STT trouble with crosstalk, accents and homebrew words, "$10 for 1 campaign, with 4 sessions per month," free player access, and a verdict of "not a plug-and-play tool" ([thedailydungeonmaster.com](https://www.thedailydungeonmaster.com/4-14-2025/product-review-archivist-ai)).
- **Scribe: Automated TTRPG Recaps** (iOS/Android) records in-app, produces transcripts, editable narrative recaps, AI art and an auto-updated NPC/location/event database; credits $3.99–$59.99; last update v1.14 on 22 Mar 2025 ([App Store](https://apps.apple.com/us/app/scribe-automated-ttrpg-recaps/id6736348981)).
- **SessionKeeper** — in-app/Discord/upload capture, self-organising wiki, "character memories," badges, 13 languages ([sessionkeeper.ai](https://www.sessionkeeper.ai/)). **Tavern Scribe** — free workspace with a "Player truth" ledger of facts the party knows vs private canon, AI recaps as paid "silver packs" ([tavernscribe.com](https://www.tavernscribe.com/)). **DM Scribe** — same category ([dmscribe.com](https://dmscribe.com/)).

Two lessons. First, the entity set these tools *extract* from raw audio is again characters/locations/factions/items/timeline — machine confirmation of the human consensus. Second, they all need a network, a server, and a group that consents to recording; on the LP3, the honest version is "record a voice memo on-device, hand it to one of these later," not on-device transcription (see §5).

### 1.12 Calendars: Harptos, Fantasy Calendar, Calendarium, donjon

The Forgotten Realms' Calendar of Harptos has twelve 30-day months of three tendays each (Hammer … Nightal), five intercalary festival days (six in leap years), 365 days, and dates written "15 Hammer" or "the 15th of Deepwinter" ([forgottenrealms.fandom.com](https://forgottenrealms.fandom.com/wiki/Calendar_of_Harptos)). Tooling:

- **Fantasy Calendar** (fantasy-calendar.com): custom months/weekdays/leap days, moons, weather, events to the minute, eras, Discord integration, player invitations; single paid tier $2.49/mo or $24.99/yr; "open source hobby project maintained by two developers"; public Harptos presets exist ([app.fantasy-calendar.com](https://app.fantasy-calendar.com/); [pricing](https://app.fantasy-calendar.com/pricing); [Harptos preset](https://app.fantasy-calendar.com/calendars/d4cdf619d1a7a06a27221866c99c1020)).
- **Obsidian:** the original Fantasy Calendar plugin (archived 15 Dec 2023) established the frontmatter contract — `fc-calendar`, `fc-date` (string `YYYY-MM-DD` or `{year, month, day}` object, with omitted parts making the event recur), `fc-end`, `fc-category`, `fc-display-name` — plus presets including Harptos, a settable "current day," and "Auto-increment Day" ([github.com/fantasycalendar/obsidian-fantasy-calendar](https://github.com/fantasycalendar/obsidian-fantasy-calendar)). Its successor **Calendarium** (Javalent; 449 commits, 65 open issues) keeps that model ([github.com/javalent/calendarium](https://github.com/javalent/calendarium)); its docs site could not be fetched, so the exact current key names are not re-verified here.
- **donjon**'s generator takes a JSON with `year_len, n_months, months[], month_len{}, week_len, weekdays[], year, first_day, n_moons, moons[], lunar_cyc{}, lunar_shf{}, events, notes{}` — a 17-"month" Harptos definition treats each festival as a one-day month ([gist](https://gist.github.com/palikhov/b96d39fa0d1a16e624021ce1835eed73)). That trick — festivals as one-day months — is the cheapest correct representation of Harptos and is what the LP3 tool should use if it models the in-world date at all.
- **Kanka** calendars attach entities via reminders and can auto-advance nightly ([docs.kanka.io](https://docs.kanka.io/en/latest/entities/calendars.html)); **LegendKeeper** added moons in Nov 2025 ([changelog](https://www.legendkeeper.com/changelog/)).

### 1.13 Video-game quest-log conventions that TTRPG tools borrow

The Witcher's journal is the archetype: tabs for Quests, Characters, Locations, Bestiary, Glossary; quests filtered by primary/secondary and by act, each with status *active / completed / failed* and phases that "update dynamically"; **character entries accumulate** ("only significant ones generate entries that accumulate as players learn more") ([witcher.fandom.com](https://witcher.fandom.com/wiki/The_Witcher_journal)). Oblivion's journal had tabs for active quests, all known quests and completed quests; Skyrim's journal "exists as a means of quest tracking" only ([elderscrolls.fandom.com](https://elderscrolls.fandom.com/wiki/Journal)). Morrowind's engine model is a quest ID with monotonically rising stage indices — "the index is only set if it is higher than before," duplicates suppressed, silent stages allowed ([uesp.net](https://en.uesp.net/wiki/Morrowind_Mod:Journal)). The most-downloaded 2025 Skyrim journal mod shows what players feel vanilla lacks: separation into Main/Side/Faction/Completed/Failed, a "!" for new, region icons, and an "option to hide completed and failed quests" ([nexusmods.com](https://www.nexusmods.com/skyrimspecialedition/mods/141295)).

The borrowed conventions that survive into Kanka/Chronica/Monk's Journal are: a quest is **a title + a status enum + an ordered list of objectives + a giver + a place**; entries about a person are **appended, never rewritten**; and *completed/failed* things are hidden by default but never deleted.

---

## 2. Comparison matrix

| Tool | Core entities | Link primitive | Player-private notes | Session log object | Calendar | Export | Offline | Mobile | Price (2026) |
|---|---|---|---|---|---|---|---|---|---|
| Obsidian + plugins | user-defined via `type:` | `[[link]]`, backlinks, unlinked mentions | n/a (own vault) | note per session + Dataview/Bases | Calendarium (`fc-date`) | Markdown files | yes | app, awkward | free |
| Kanka | ~20 fixed types | `@mention`, relations | `self`/`admin-self` visibility | **Journal** (type, date, author, calendar date) | yes, complex | API/JSON | no | browser | free–$24.99 |
| World Anvil | typed articles | mentions | Notebook, Memories, Journals, Scrapbook | player Journal (backdatable) | timelines | limited | no | browser | free–$25 |
| LegendKeeper | pages + properties | `@`, `[[ ]]`, auto-detect | secrets (GM-side) | page | moons (2025), timelines pending | HTML/JSON | edit-offline sync | "not mobile-ready" | $9/mo, guests free |
| Scabard | chars, places, orgs, events | proper-noun detector | none for players | page | — | ? | no | ? | free–$19.95 |
| Chronica | chars, kinships, places, quests, developments | links | Adventure Notes public/private | Adventure Notes | custom | ? | no | ? | free + tiers |
| Campaign Logger | log entries + auto-created tags | `@name`, `#place`, `%date` | none (shared login) | **the log entry** | world dates | text/JSON/PDF | no | read-only OK | free–$5 |
| Realm Works † | topics/snippets | auto-links | Player Edition | — | — | — | Windows | no | dead 2019 |
| Notion templates | 4–8 databases | `@` relations | own workspace | Session Notes table | — | MD+CSV (lossy) | no | app, online | free + $8–18 templates |
| Foundry + Monk's | Person/Place/Org/Quest/Loot/Shop | `@UUID` | GM-private only | — | — | — | self-host | no | $50 one-time |
| Roll20 | handouts | none | bio tab workaround | — | — | — | no | web | free+ |
| D&D Beyond | char Notes fields | none | Allies/Enemies/Orgs/Backstory | campaign notes (unwieldy) | — | — | partial | app | free+ |
| Archivist / Scribe / SessionKeeper | auto-extracted chars/locs/factions/items | auto | viewer access | AI recap + timeline | — | ? | no | Scribe: iOS/Android | ~$10/mo or credits |

† discontinued.

---

## 3. What players actually write down (evidence)

The player-side literature is thinner than the DM-side but unusually consistent.

**Categories.** The long-running D&D Beyond thread "How to take notes efficiently as a player" converges on: NPC names *with where you met them*, towns and notable features, active quests and their givers, loot and party finances, plot clues, and — repeatedly — "odd or seemingly trivial moments (DMs often callback to these)" and rumours ([dndbeyond forum](https://www.dndbeyond.com/forums/dungeons-dragons-discussion/tips-tactics/15431-how-to-take-notes-efficiently-as-a-player)). Scriv the Bard reduces it to three questions — "Where did you go? Who did you meet? What did you learn/find?" — with an NPC line looking like "Merrick, Salmon's Spyglass, Honeycakes" (name, place, memorable detail) and the warning that "everything else is just flavour … might just end up cluttering your notes" ([scrivthebard.com](https://scrivthebard.com/2019/04/10/dd-note-taking-tips/)). Minva's player-specific list is "character relationships, party goals, important NPCs, treasure and rewards, backstory moments, relevant clues," organised around the single question "What do I care about next session?" ([minvarpg.com](https://minvarpg.com/blogs/ttrpg/how-to-take-dnd-session-notes-you-ll-actually-use)).

**Formatting in the moment.** Players use sigils and casing rather than structure: "? for new quests, ! for information learned," "LOOT in all caps," sections "Towns | People | Quests | General Info | Miscellaneous," and "leave space between entries for later additions"; the practical rule is to spell names phonetically so "you can reasonably repeat the name" without stopping play ([dndbeyond forum](https://www.dndbeyond.com/forums/dungeons-dragons-discussion/tips-tactics/15431-how-to-take-notes-efficiently-as-a-player)). The Friendly Bard writes "phrases I jot down while I'm playing" in a paper notebook and types a clean recap within 1–2 days, keeps a separate campaign timeline and an in-character journal, and shares with the table for correction ([friendlybard.com](https://friendlybard.com/2020/09/how-i-take-session-notes/)).

**Two-pass is universal.** Critscribbler's analysis of 337 Reddit comments (DM-side, but the shape carries) ranked "write notes after session" (93 mentions) and "minimal in-session notes" (61) far above "pause to write notes" (9); "delegate to players" scored 62 and "use AI note-taker" 17 ([critscribbler.com](https://critscribbler.com/blog/how-to-take-notes-during-a-dnd-session-as-a-dm)). Johnn Four's own framing is pre-session / in-session / post-session, with the in-session list being combat results, items acquired, NPC interactions and player comments ([roleplayingtips.com](https://www.roleplayingtips.com/running-games/a-guide-to-session-notes/)).

**Sharing.** The EN World "Notetaker role" thread shows notes flowing to Discord recaps, a shared Google Drive folder where "Google's OCR is good enough on handwriting that we basically have full-search capability," and in-character blogs; notably "no participant raised concerns that note-taking reduces engagement" ([enworld.org](https://www.enworld.org/threads/the-notetaker-role.704680/)).

**The dissent.** Nate Whittington's "The Fallacy of Taking Notes" argues "If everything is worth recording, then nothing is," that "your players probably don't need to take notes; *you* need to start running stuff worth remembering," and that sparse notes are feedback to the GM ([grinningrat.substack.com](https://grinningrat.substack.com/p/notes)). One D&D Beyond poster echoes the fear that tables end up "writing what the DM says and not really getting immersed" and suggests recording only "what your *character* would find significant" ([dndbeyond forum](https://www.dndbeyond.com/forums/dungeons-dragons-discussion/tips-tactics/15431-how-to-take-notes-efficiently-as-a-player)). This dissent is actually *aligned* with the Light Phone ethos: a good player journal is small on purpose.

---

## 4. Synthesis I — the minimum viable entity model for a player's journal

A DM's wiki needs twenty types because a DM invents the world. A player *discovers* it, one line at a time, and the tools that serve players (World Anvil's player surfaces, Notion's Character Compendium, Chronica's Adventure Notes, the 2025 Player's Campaign Journal, and the AI extractors) agree on a much smaller set.

**Six entities, one of which is the log itself.**

1. **Session** — the spine. `number`, `real date`, optional `in-world date` (free text or a Harptos-style day index), `title` (auto: "Session 12"), and an ordered list of *entries*. Every tool with a session object stamps it with the real date and a number (cacharbe's plugin, Kanka Journal, Minva's template); the in-world date is optional everywhere and should be optional here.
2. **Entry** (log line) — `timestamp`, `text` (short), `kind` sigil (met / went / learned / got / quest / rumour / note), and zero-or-more links to the entities below. This is Campaign Logger's log entry and the D&D Beyond thread's "? / ! / LOOT" line, formalised.
3. **Person** (NPC or party member) — `name`, `where met` (link to Place), `one-line detail`, `disposition` (friend / neutral / hostile / unknown — Minva's "trustworthiness"), `alive?` (Kanka `is_dead`), and a derived list of entries that mention them (the Obsidian `TABLE … FROM #session-notes AND [[NPC]]` pattern, computed, never typed). Party members are the same record with a `party` flag: the Player's Campaign Journal, Chronica and Monk's all treat PCs as characters.
4. **Place** — `name`, `parent place` (Chronica nests "worlds to individual buildings"; Kanka locations nest), `one-line detail`, derived mentions.
5. **Quest / Hook** — `title`, `status` (open / done / failed / dropped — the Witcher/Skyrim enum, with "hide completed" the default), `giver` (link Person), `where` (link Place), and free-text `objectives` as plain lines. Kanka's `completed` flag and Monk's objectives are the two ends of the complexity range; a player needs the flag and a few lines, not roles or rewards tables.
6. **Item / Ledger line** — `name`, `holder` (link Person or "party"), `source` (link Session), `value`, `status` (held / sold / used / given). These are exactly the EN World spreadsheet columns. The party fund is a single running `gp` total attached to the campaign, not a separate entity.

**What is deliberately absent.** Organisations/factions (fold into Person with a "group" flag or into a tag — the Player's Campaign Journal tracks factions but most player threads don't), races, families, abilities, events, maps, timelines, calendars-as-objects, statblocks, initiative, secrets/permissions (there is no DM on the device), templates, and attributes/properties. Rumours and clues are **entry kinds**, not entities; downtime is an entry kind on a session with no number. Any of these can be added later without migration if entries carry a free tag list.

**Which linking is worth the cost.** Exactly one primitive: an entry links to one or more named things by *selection from the roster*, never by typing a link. Backlinks are free (derive them), tags are cheap (a fixed enum of entry kinds plus optional free tags), relations-with-roles (Kanka's Elements page, LegendKeeper properties) are not worth it. Unlinked-mention detection (Obsidian, Scabard's noun detector) is worth it in the *export*, not on the phone.

---

## 5. Synthesis II — fast capture during a live session

The bar set by good tools is "met NPC X in place Y about quest Z" in under ten seconds, and every tool that clears it does so by making **names the only thing you type, once**:

- **Campaign Logger:** `@Merrick` creates and links Merrick; autosuggest thereafter means "you type each name in just once and then you point and click" ([campaign-logger.com](https://campaign-logger.com/)).
- **Obsidian / LegendKeeper:** `[[` or `@` opens a fuzzy picker over existing names ([phd20.com](https://phd20.com/blog/getting-started-with-legendkeeper-dnd/)).
- **Paper:** a sigil (`?`, `!`, `LOOT`) at the line start and a phonetic name ([dndbeyond forum](https://www.dndbeyond.com/forums/dungeons-dragons-discussion/tips-tactics/15431-how-to-take-notes-efficiently-as-a-player)).
- **AI tools:** zero typing; entities extracted from audio ([myarchivist.ai](https://www.myarchivist.ai/how-it-works)).

On the LP3, text entry is a full-screen `LightTextInputEditor` with the LP3 keyboard, which the project docs call "awkward full-screen text entry" and treat as a UX cost, not just an aesthetic one (`claude/lp3-port-candidates.md`). So the capture flow should invert the usual order — **pick the structure first with the wheel, type the name last, and only if it is new**:

1. Tap **+** on the current session → a vertical list of entry kinds (Met · Went · Quest · Got · Learned · Rumour · Note), one wheel-turn each, one tap to choose. This is the sigil, made a chip.
2. The kind decides which roster opens next: *Met* → People (most-recent-first, "+ new" at top); *Went* → Places; *Quest* → Quests; *Got* → Items + holder. Wheel to the name, tap. Choosing "+ new" is the only path into the keyboard, and it asks for a name only (`singleLine = true`, `initialCaps = true`).
3. Optional second link (the *about* clause): after picking a Person, offer "at …" and "re …" as two chips that open the Place and Quest rosters. Skipping is a single tap.
4. Optional free text last, capped visually to one line — long enough for "Salmon's Spyglass, honeycakes," short enough to discourage prose.

Each entry is timestamped on creation, so the session log is a timeline without a date field. **Voice** is the one escape hatch worth building: `android.permission.RECORD_AUDIO` is on the SDK's permission allow-list (`plugin/src/main/kotlin/com/thelightphone/plugin/LightToolMetadata.kt` in the synced SDK source), the LP3 keyboard exposes a Voice special key, and "voice-to-text / dictation" is an open community request with no shipped answer (`claude/lp3-tool-register.md`). A 20-second voice memo attached to an entry — played back on the phone, transcribed *off* the phone by Archivist/SessionKeeper-class tools or by the user — is honest and cheap; on-device transcription is not.

Two things that *do not* work with terrible text entry and should be dropped: freeform Markdown with inline `@` sigils (requires typing the sigil and the name accurately), and any form with more than one text field. Two things that do: **phonetic names are fine** (players already spell NPCs phonetically), and **rename later** (the export can normalise "Merik" to "Merrick" on a laptop; Obsidian's unlinked-mentions and Scabard's alias feature exist for exactly this).

---

## 6. Synthesis III — design implications for a monochrome phone with a scroll wheel

**Keep on-device.**
- The session log as the home screen: current session at top, entries newest-last so the wheel scrolls "forward in time," one line each, sigil glyph + name + optional place. The panel is ~411×472 dp, so plan on 8–10 lines per screen.
- Four flat rosters — People, Places, Quests, Loot — each a single `LightLazyScrollView` list, most-recently-touched first, with quests defaulting to *open only* (the Skyrim-mod finding: users want completed/failed hidden but retained).
- Per-thing detail = its derived mention list: tapping "Merrick" shows every entry that links him, in session order. This is Obsidian's Dataview table and the Witcher's "entries accumulate" rule, and it costs nothing to author.
- Three states per quest and per item, toggled by tap, no dialog.
- Party gold as a single number with +/− entries in the loot roster.
- Everything in Room/DataStore with debounced saves, exactly as the ledger tool does (`claude/lp3-learning-path.md`).

**Drop.**
- Any wiki page with a body: no long descriptions of NPCs or places on-device.
- Maps, statblocks, initiative, calendars-as-widgets, timelines-as-graphics, relations with roles, permissions, templates, tags beyond the fixed kind enum, images (the SDK has no media-image permission and the ethos argues against it).
- The in-world calendar as a *model*. Store an optional free-text in-world date per session ("3 Mirtul 1492 DR"); if a Harptos day-counter is ever wanted, use donjon's "festival = one-day month" trick, not Kanka's reminder system.
- Search-as-typed. A roster sorted by recency plus a first-letter jump on the wheel is enough for a party's ~40 NPCs; typing a query costs more than scrolling.

**Make a companion-export instead of on-device editing.**
- **Markdown export** as the primary "long-form" path, shaped for Obsidian *in its 2026 form*: one file per Person/Place/Quest with YAML `type:`, `aliases:`, `status:`, `first_seen: Session N` **as properties** (so Bases can see them), one `Session NN.md` per session with a bullet per entry and `[[wikilinks]]` for every linked name, and `fc-date`-style keys if an in-world date was recorded, so Calendarium can place it. This matches SONDLecT's and dclasair's structures closely enough to drop into either vault.
- **JSON export** of the raw model for anyone importing into Kanka (its API has `journals`, `characters`, `quests` endpoints with obvious field mappings — `name`, `entry`, `date`, `is_private`) or Campaign Logger (which imports JSON and even documents a search-and-replace workflow for names).
- Post-session cleanup — prose recaps, renaming "Merik" to "Merrick," merging duplicates, in-character journal writing — belongs on the laptop after export, because the evidence says players already do a two-pass anyway (§3) and because the Light Phone's "calm/finite" ethos is served by a journal that *ends* when the session ends.
- Delivery mechanism is the open question this report cannot settle: the SDK has no filesystem or share-sheet, so export is likely a QR-chunked or server-relayed handoff; that belongs to the platform research, not this survey.

**Monochrome and wheel specifics.** States are weight and glyph, never hue: sigils (`?` quest, `!` learned, `◆` loot, `@` met, `→` went) do double duty as the Skyrim "!"-for-new marker; completed quests render struck-through and sink to the bottom; the current session's header is the only bold text on the home screen. Give the wheel a job on every screen (scroll the list; on a quest, scroll objectives; on a roster, first-letter jumps) so the touch target count per screen stays under six, which is also what keeps the thing "finite."

---

## 7. Open questions and things not verified

- Campaign Logger's full sigil set beyond `@`, `#`, `%` (cheat sheet behind forum login).
- Calendarium's current frontmatter keys (docs site fetches failed; the archived predecessor's `fc-*` keys are documented and Calendarium is its continuation).
- Monk's Enhanced Journal field-level quest schema under Foundry v13 (README describes types, not fields).
- Josh Plunkett's exact vault (paywalled); the SONDLecT/dclasair repos are used as the public proxies.
- Whether LightOS exposes the scroll wheel as key events to tools (`LightKeyHandler` exists in the SDK; hardware mapping was not tested here).

---

## Sources

**Obsidian ecosystem**
- Obsidian Hub, Plugins for TTRPG — https://raw.githubusercontent.com/obsidian-community/obsidian-hub/main/02%20-%20Community%20Expansions/02.01%20Plugins%20by%20Category/Plugins%20for%20TTRPG.md
- Javalent plugin docs — https://plugins.javalent.com
- PhD20, Getting Started with Obsidian for D&D — https://phd20.com/blog/getting-started-with-obsidian-dnd/
- GM Assistant, Using Obsidian for TTRPG Notes pt 1 — https://gmassistant.app/blog/using-obsidian-for-ttrpg-notes-part-1
- SONDLecT obsidian-dm-templates — https://github.com/SONDLecT/obsidian-dm-templates ; raw npc.md — https://raw.githubusercontent.com/SONDLecT/obsidian-dm-templates/main/npc.md ; raw session-notes.md — https://raw.githubusercontent.com/SONDLecT/obsidian-dm-templates/main/session-notes.md
- dclasair ttrpg-campaign-vault — https://github.com/dclasair/ttrpg-campaign-vault
- cacharbe obsidian-ttrpg-session-notes — https://github.com/cacharbe/obsidian-ttrpg-session-notes
- Obsidian Rocks, Dataview vs Datacore vs Bases — https://obsidian.rocks/dataview-vs-datacore-vs-obsidian-bases/
- AlternativeTo, Obsidian 1.9 introduces Bases (May 2025) — https://alternativeto.net/news/2025/5/obsidian-1-9-0-introduces-bases-plugin-for-database-style-note-management
- Joshua Plunkett, Getting Started With Dataview (paywalled) — https://www.patreon.com/posts/70180876

**Kanka**
- Features — https://kanka.io/features ; Pricing — https://kanka.io/pricing
- Docs: entities overview — https://docs.kanka.io/en/latest/entities/overview.html ; quests — https://docs.kanka.io/en/latest/entities/quests.html ; calendars — https://docs.kanka.io/en/latest/entities/calendars.html
- API: characters — https://app.kanka.io/api-docs/1.0/characters ; journals — https://app.kanka.io/api-docs/1.0/journals ; visibilities — https://app.kanka.io/api-docs/1.0/misc/visibilities
- Release blog — https://blog.kanka.io/tag/release/

**World Anvil**
- Player Workflow (Codex) — https://www.worldanvil.com/w/WorldAnvilCodex/a/player-workflow
- Secrets (Codex) — https://www.worldanvil.com/w/WorldAnvilCodex/a/secrets
- Player page — https://www.worldanvil.com/player ; Pricing — https://www.worldanvil.com/pricing

**LegendKeeper**
- FAQ — https://www.legendkeeper.com/faq/ ; Pricing — https://www.legendkeeper.com/pricing/ ; Changelog — https://www.legendkeeper.com/changelog/
- PhD20, Getting Started with LegendKeeper — https://phd20.com/blog/getting-started-with-legendkeeper-dnd/

**Scabard, Chronica, Campfire, Fantasia Archive**
- Scabard learn-more — https://www.scabard.com/world-building/learn-more ; Worlds by Wally review (Jun 2025) — https://worldsbywally.com/2025/06/01/scabard-product-review/
- Chronica features — https://chronica.ventures/features
- Campfire — https://www.campfirewriting.com/write
- Fantasia Archive — https://fantasiaarchive.com/

**Campaign Logger / Realm Works**
- Campaign Logger — https://campaign-logger.com/ ; Roleplaying Tips product page — https://www.roleplayingtips.com/campaign-logger/ ; FAQ — https://www.roleplayingtips.com/campaign-logger-faq/ ; tag archive — https://www.roleplayingtips.com/tag/campaign-logger/ ; vNext cheat sheet (login) — https://campaign-community.com/index.php?resources/campaign-logger-vnext-cheat-sheet.254/
- Roleplaying Tips, A Guide to Session Notes — https://www.roleplayingtips.com/running-games/a-guide-to-session-notes/
- Tenkar's Tavern, Lone Wolf ceases Realm Works development — https://www.tenkarstavern.com/2019/09/hero-labs-lone-wolf-downsizes-ceases.html
- 5 Minute Workday, Review: Realm Works — https://www.5mwd.com/archives/3370

**Notion / Docs / paper**
- Sly Flourish, Lazy D&D with Notion — https://slyflourish.com/lazy_dnd_with_notion.html
- Minva, Best Notion Templates for DnD (2026) — https://minvarpg.com/blogs/ttrpg/best-notion-templates-dnd
- Notion Marketplace, D&D 5E Campaign Database — https://www.notion.com/templates/dnd5e-campaign-database
- EN World, Help w/ Party Loot Tracking — https://www.enworld.org/threads/help-w-party-loot-tracking.668536/
- Sly Flourish, Ten Ways to Use Index Cards — https://slyflourish.com/ten_ways_to_use_index_cards.html
- The Player's Campaign Journal (Clarkson Potter, 18 Nov 2025) — https://mitpressbookstore.mit.edu/book/9798217034321
- Riles, Retiring a Gaming Notebook 2023–2025 — https://rileyadammmson.itch.io/rilesblog/devlog/1112410/retiring-a-gaming-notebook-2023-2025
- Daniel Kwan, Campaign Notebook (Notion) — https://danielhkwan.itch.io/campaign-notebook

**VTT / D&D Beyond**
- Foundry, Journal Entries — https://foundryvtt.com/article/journal/ ; Users and Permissions — https://foundryvtt.com/article/users/
- Monk's Enhanced Journal README — https://github.com/ironmonk108/monks-enhanced-journal/blob/main/README.md ; v13 fork — https://github.com/therealguy90/monks-enhanced-journal-v13 ; GM Wintermute overview — https://www.gmwintermute.com/foundryvtt/monks-enhanced-journal/
- Roll20 Wiki, Handout — https://wiki.roll20.net/Handout ; forum: Player Notes — https://app.roll20.net/forum/post/2550355/player-notes-o-the-journal
- D&D Beyond support, Builder Sections — https://dndbeyond-support.wizards.com/hc/en-us/articles/7747202748436-Builder-Sections ; forum: Campaign Notes — https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/116008-campaign-notes

**AI recap tools (2025–26)**
- Archivist, How It Works — https://www.myarchivist.ai/how-it-works ; Daily Dungeon Master review (Apr 2025) — https://www.thedailydungeonmaster.com/4-14-2025/product-review-archivist-ai
- Scribe: Automated TTRPG Recaps (App Store) — https://apps.apple.com/us/app/scribe-automated-ttrpg-recaps/id6736348981
- SessionKeeper — https://www.sessionkeeper.ai/ ; Tavern Scribe — https://www.tavernscribe.com/ ; DM Scribe — https://dmscribe.com/

**Calendars**
- Forgotten Realms Wiki, Calendar of Harptos — https://forgottenrealms.fandom.com/wiki/Calendar_of_Harptos
- Fantasy Calendar — https://app.fantasy-calendar.com/ ; pricing — https://app.fantasy-calendar.com/pricing ; Harptos preset — https://app.fantasy-calendar.com/calendars/d4cdf619d1a7a06a27221866c99c1020
- Obsidian Fantasy Calendar (archived Dec 2023) — https://github.com/fantasycalendar/obsidian-fantasy-calendar ; Calendarium — https://github.com/javalent/calendarium
- donjon Harptos definition (gist) — https://gist.github.com/palikhov/b96d39fa0d1a16e624021ce1835eed73

**Video-game quest logs**
- Witcher Wiki, The Witcher journal — https://witcher.fandom.com/wiki/The_Witcher_journal
- Elder Scrolls Wiki, Journal — https://elderscrolls.fandom.com/wiki/Journal
- UESP, Morrowind Mod: Journal — https://en.uesp.net/wiki/Morrowind_Mod:Journal
- Nexus Mods, Quest Journal Overhaul (Skyrim SE) — https://www.nexusmods.com/skyrimspecialedition/mods/141295

**Player note-taking evidence**
- D&D Beyond forum, How to take notes efficiently as a player — https://www.dndbeyond.com/forums/dungeons-dragons-discussion/tips-tactics/15431-how-to-take-notes-efficiently-as-a-player
- Scriv the Bard, D&D Note-Taking Tips — https://scrivthebard.com/2019/04/10/dd-note-taking-tips/
- The Friendly Bard, How I Take Session Notes — https://friendlybard.com/2020/09/how-i-take-session-notes/
- Minva, How to Take D&D Session Notes You'll Actually Use — https://minvarpg.com/blogs/ttrpg/how-to-take-dnd-session-notes-you-ll-actually-use
- Critscribbler, How to Take Notes During a DnD Session as a DM (with data) — https://critscribbler.com/blog/how-to-take-notes-during-a-dnd-session-as-a-dm
- EN World, The Notetaker role — https://www.enworld.org/threads/the-notetaker-role.704680/
- Grinning Rat, The Fallacy of Taking Notes — https://grinningrat.substack.com/p/notes

**Project-internal constraints (claude.ai Project "light phone")**
- `claude/lp3-learning-path.md` (panel size, `LightTextInputEditor`, three theme tokens, debounced saves)
- `claude/lp3-port-candidates.md` (permission allow-list, "awkward full-screen text entry")
- `claude/lp3-tool-register.md` (dictation and Obsidian-sync as unmet community requests)
- Synced SDK source: `sdk/ui/.../LightTextInputEditor.kt`, `LightScrollView.kt`, `LightKeyHandler.kt`; `plugin/.../LightToolMetadata.kt` (`ALLOWED_PERMISSIONS` incl. `RECORD_AUDIO`)
