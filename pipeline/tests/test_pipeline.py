"""Pipeline tests: adapter output validates, emitted assets are reproducible and within
Light's builder limits, legal files are present and unmodified in wording."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

import pytest

from pipeline import fetch as fetch_mod
from pipeline.emit import MAX_FILE_BYTES
from pipeline.legal import SRD_51_SENTENCE
from pipeline.normalize import fivebits_2014, open5e_v1_sections
from pipeline.validate import validate

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "tool" / "src" / "main" / "assets" / "compendium"
LEGAL = ROOT / "tool" / "src" / "main" / "assets" / "legal"


@pytest.fixture(scope="module")
def cache_dir():
    lock = fetch_mod.load_lock()
    src = lock["sources"]["fivebits-2014"]
    d = fetch_mod.cache_dir_for("fivebits-2014", src)
    if not d.exists():
        pytest.skip("run `python3 -m pipeline fetch` first (network)")
    return d


def test_normalize_validates(cache_dir):
    lock = fetch_mod.load_lock()
    comp = fivebits_2014.normalize(cache_dir)
    o5 = fetch_mod.cache_dir_for("open5e-v1-wotc-srd", lock["sources"]["open5e-v1-wotc-srd"])
    if not (o5 / "Section.json").exists():
        pytest.skip("run `python3 -m pipeline fetch` first (network)")
    extra_rules, extra_sections = open5e_v1_sections.supplement(o5)
    comp["rules"] = sorted(comp["rules"] + extra_rules, key=lambda r: r["key"])
    comp["rule_sections"] = sorted(comp["rule_sections"] + extra_sections, key=lambda r: r["key"])
    validate(comp, lock["expectedCounts"])


@pytest.mark.skipif(not ASSETS.exists(), reason="run `python3 -m pipeline build` first")
def test_emitted_assets_match_index_and_limits():
    index = json.loads((ASSETS / "index.json").read_text(encoding="utf-8"))
    for name, meta in index["files"].items():
        data = (ASSETS / name).read_bytes()
        assert len(data) == meta["bytes"] <= MAX_FILE_BYTES
        assert hashlib.sha256(data).hexdigest() == meta["sha256"]
        assert len(json.loads(data)) == meta["count"]
    # Builder allow-list: only .json in the compendium folder.
    assert all(p.suffix == ".json" for p in ASSETS.iterdir())


@pytest.mark.skipif(not ASSETS.exists(), reason="run `python3 -m pipeline build` first")
def test_no_forbidden_sources_leaked():
    blob = b"".join(p.read_bytes() for p in ASSETS.glob("*.json")).lower()
    for needle in (b"5e.tools", b"5etools", b"dndbeyond.com/monsters", b"player's handbook p."):
        assert needle not in blob, needle


@pytest.mark.skipif(not LEGAL.exists(), reason="run `python3 -m pipeline build` first")
def test_attribution_sentence_verbatim():
    text = (LEGAL / "ATTRIBUTION.md").read_text(encoding="utf-8")
    assert SRD_51_SENTENCE in text
    # WotC asks for no other Wizards attribution: no trademark lines.
    assert "trademark" not in text.lower()
    assert "Dungeons & Dragons" not in text
    assert (LEGAL / "LICENSE-CC-BY-4.0.txt").read_text(encoding="utf-8").startswith("Creative Commons Attribution 4.0 International")


def test_fixture_characters_match_schema():
    from jsonschema import Draft202012Validator

    schema = json.loads((ROOT / "pipeline" / "schema" / "character.schema.json").read_text(encoding="utf-8"))
    v = Draft202012Validator(schema)
    for p in sorted((ROOT / "fixtures" / "characters").glob("*.json")):
        errs = [e.message for e in v.iter_errors(json.loads(p.read_text(encoding="utf-8")))]
        assert not errs, (p.name, errs[:3])
    jschema = json.loads((ROOT / "pipeline" / "schema" / "journal.schema.json").read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(jschema)
    jv = Draft202012Validator(jschema)
    for p in sorted((ROOT / "fixtures" / "journal").glob("*.json")):
        errs = [e.message for e in jv.iter_errors(json.loads(p.read_text(encoding="utf-8")))]
        assert not errs, (p.name, errs[:3])


def test_plugin_copies_in_sync():
    """plugins/fifth-edition mirrors .claude skills/agents byte-for-byte (see plugins/README.md)."""
    pairs = [
        ("plugins/fifth-edition/skills/fifth-edition-rules/SKILL.md", ".claude/skills/fifth-edition-rules/SKILL.md"),
        ("plugins/fifth-edition/skills/fifth-edition-rules/references/edge-cases.md", ".claude/skills/fifth-edition-rules/references/edge-cases.md"),
        ("plugins/fifth-edition/skills/dice-notation/SKILL.md", ".claude/skills/dice-notation/SKILL.md"),
        ("plugins/fifth-edition/agents/rules-lawyer.md", ".claude/agents/rules-lawyer.md"),
    ]
    for a, b in pairs:
        assert (ROOT / a).read_text(encoding="utf-8") == (ROOT / b).read_text(encoding="utf-8"), f"{a} != {b}"


def test_readme_mirrors_vetting_and_attribution():
    """README.md must carry docs/VETTING-DEFENSE.md's defense and the bundled attribution verbatim."""
    import re

    vet = (ROOT / "docs" / "VETTING-DEFENSE.md").read_text(encoding="utf-8")
    section = re.search(r"\n(Grimoire is an offline.*?)\n\nReviewer questions", vet, re.S).group(1).strip()
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    assert section in readme, "README vetting section drifted from docs/VETTING-DEFENSE.md"
    attribution = (LEGAL / "ATTRIBUTION.md").read_text(encoding="utf-8").split("\n", 2)[2].strip()
    assert attribution in readme, "README attribution drifted from assets/legal/ATTRIBUTION.md"
    toml = (ROOT / "tool" / "lighttool.toml").read_text(encoding="utf-8")
    claude_md = (ROOT / "CLAUDE.md").read_text(encoding="utf-8")
    assert f"```toml\n{toml}```" in claude_md, "CLAUDE.md toml block drifted from tool/lighttool.toml"
