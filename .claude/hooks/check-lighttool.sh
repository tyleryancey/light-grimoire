#!/usr/bin/env bash
# PostToolUse (Edit|Write) on tool/lighttool.toml: validate against LightToolMetadata.kt's
# rules (verified 29 Aug 2026) and the house rule that commits carry serverPackage=com.lightos.
set -uo pipefail
INPUT=$(cat)
FILE=$(printf '%s' "$INPUT" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("tool_input",{}).get("file_path") or "")' 2>/dev/null || true)
case "$FILE" in */tool/lighttool.toml|tool/lighttool.toml) ;; *) exit 0 ;; esac
[ -f "$FILE" ] || exit 0

python3 - "$FILE" <<'PY'
import json, re, sys, tomllib
path = sys.argv[1]
raw = open(path, "rb").read()
if len(raw) > 32 * 1024:
    print("lighttool.toml exceeds the 32 KiB limit", file=sys.stderr); sys.exit(2)
try:
    doc = tomllib.loads(raw.decode("utf-8"))
except Exception as e:  # noqa: BLE001
    print(f"lighttool.toml is not valid TOML: {e}", file=sys.stderr); sys.exit(2)
tool = doc.get("tool")
if not isinstance(tool, dict):
    print("lighttool.toml needs a [tool] table", file=sys.stderr); sys.exit(2)

errs, notes = [], []
ID = str(tool.get("id", ""))
if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", ID):
    errs.append(f"id {ID!r} must match ^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$ (lowercase dotted, >=2 segments, no hyphens)")
label = str(tool.get("label", ""))
if not re.fullmatch(r"[^\x00-\x1f<>]{1,50}", label):
    errs.append("label must be 1-50 printable chars with no < > or control chars")
for bad in ("D&D", "Dungeons & Dragons", "DnD"):
    if bad.lower() in label.lower():
        errs.append(f"label contains {bad!r}: WotC trademark - the SRD legal page permits only '5E compatible' (docs/LICENSING.md)")
vn = str(tool.get("versionName", ""))
if not re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", vn):
    errs.append(f"versionName {vn!r} must be strict major.minor.patch with no leading zeros or suffixes")
vc = tool.get("versionCode")
if not isinstance(vc, int) or isinstance(vc, bool) or not (1 <= vc <= 2_100_000_000):
    errs.append(f"versionCode {vc!r} must be an integer in 1..2100000000")
sp = str(tool.get("serverPackage", ""))
if sp == "com.thelightphone.sdk.emulator":
    notes.append("serverPackage is the emulator value - fine for local AVD work; restore com.lightos before committing (git checkout -- tool/lighttool.toml)")
elif sp != "com.lightos":
    errs.append(f"serverPackage {sp!r} must be com.lightos (or com.thelightphone.sdk.emulator locally)")
ALLOWED = {"android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.WAKE_LOCK",
           "android.permission.VIBRATE", "android.permission.POST_NOTIFICATIONS", "android.permission.CAMERA",
           "android.permission.RECORD_AUDIO", "android.permission.READ_MEDIA_AUDIO", "android.permission.ACCESS_FINE_LOCATION",
           "android.permission.ACCESS_COARSE_LOCATION", "android.permission.NFC"}
perms = tool.get("permissions", [])
if not isinstance(perms, list):
    errs.append("permissions must be a list")
else:
    for p in perms:
        if p not in ALLOWED:
            errs.append(f"permission {p!r} is not in ALLOWED_PERMISSIONS - it will not build")
    if len(perms) != len(set(perms)):
        errs.append("duplicate permissions")
    if perms:
        notes.append(f"permissions={perms} - ADR-0004 targets [] for v1; update docs/VETTING-DEFENSE.md and 00-ASSESSMENT.md if intentional")
caps = tool.get("capabilities", [])
if not isinstance(caps, list) or any(c != "detached-audio" for c in caps):
    errs.append("capabilities may only contain \"detached-audio\"")
ori = tool.get("orientation")
if ori is not None and ori != "portrait":
    errs.append("orientation may only be \"portrait\"")
for extra in set(tool) - {"id", "label", "versionCode", "versionName", "permissions", "capabilities", "orientation", "serverPackage"}:
    notes.append(f"unknown key {extra!r} - LightToolMetadata ignores it; probably a typo")
if errs:
    print("lighttool.toml problems:", file=sys.stderr)
    for e in errs:
        print("  - " + e, file=sys.stderr)
    sys.exit(2)
if notes:
    print(json.dumps({"hookSpecificOutput": {"hookEventName": "PostToolUse", "additionalContext": "lighttool.toml notes: " + " | ".join(notes)}}))
PY
