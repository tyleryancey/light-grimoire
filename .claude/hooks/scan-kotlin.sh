#!/usr/bin/env bash
# PostToolUse (Edit|Write|MultiEdit) on *.kt under tool/src/: a fast mirror of the Light SDK
# plugin scan (LightSdkPlugin.kt BLOCKED_IMPORTS / BLOCKED_CODE_PATTERNS) plus house rules.
# The real scan runs at Gradle configure time on every file, tests included; catching it
# here saves a build. Exit 2 reports violations back to Claude as something to fix now.
set -uo pipefail
INPUT=$(cat)
FILE=$(printf '%s' "$INPUT" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("tool_input",{}).get("file_path") or "")' 2>/dev/null || true)
[ -n "$FILE" ] || exit 0
case "$FILE" in *.kt) ;; *) exit 0 ;; esac
case "$FILE" in */tool/src/*|tool/src/*) ;; *) exit 0 ;; esac
[ -f "$FILE" ] || exit 0

python3 - "$FILE" <<'PY'
import re, sys
path = sys.argv[1]
src = open(path, encoding="utf-8").read()
findings = []

BLOCKED_IMPORTS = [
    "android.app.", "android.content.Context", "android.content.Intent", "android.content.ComponentName",
    "android.content.BroadcastReceiver", "android.content.ContentProvider", "android.content.ServiceConnection",
    "androidx.compose.ui.platform.LocalContext", "androidx.compose.ui.platform.LocalView",
    "androidx.compose.ui.platform.LocalLifecycleOwner", "androidx.lifecycle.compose.LocalLifecycleOwner",
    "androidx.activity.", "androidx.appcompat.", "java.lang.reflect.", "java.lang.invoke.", "kotlin.reflect.",
]
BLOCKED_PATTERNS = [
    (r"\b(LocalContext|LocalView|LocalActivity|LocalLifecycleOwner)\b", "banned composition local"),
    # Cast patterns are copied verbatim from LightSdkPlugin.kt:101-102 (whole-segment match).
    (r"\bas\??\s+(?:\w+\.)*\w*Activity\b", "cast to Activity"),
    (r"\bas\??\s+(?:\w+\.)*(?:Context|ContextWrapper|ContextThemeWrapper|Application|Service|ContentProvider|BroadcastReceiver)\b", "cast to framework type"),
    (r"\b(startActivity|startService|bindService|registerReceiver|getSystemService|getBaseContext|attachBaseContext)\s*\(", "banned framework call"),
    (r"\b(createPackageContext|createConfigurationContext|createDeviceProtectedStorageContext|createContextForSplit|createAttributionContext|createWindowContext|createDisplayContext)\s*\(", "banned create*Context call"),
    (r"\bcontentResolver\b", "contentResolver"),
    (r"\.javaClass\b", "reflection (.javaClass)"),
    (r"\.java\s*\.\s*\w", "reflection (.java.<member>)"),
    (r"\bClass\.forName\s*\(", "reflection (Class.forName)"),
    (r"\.(getDeclaredMethod|getMethod|getDeclaredField|getField)\s*\(", "reflection"),
    (r"\bMethodHandles\b", "reflection (MethodHandles)"),
]

for lineno, line in enumerate(src.splitlines(), 1):
    for stmt in line.split(";"):
        s = stmt.strip()
        if not s or s.startswith("//") or s.startswith("*") or s.startswith("/*"):
            continue  # only a statement that BEGINS with a comment marker is exempt (strings and trailing comments are not)
        if s.startswith("import "):
            imp = s[len("import "):].strip()
            for b in BLOCKED_IMPORTS:
                if imp.startswith(b):
                    findings.append((lineno, f"blocked import {imp!r} (prefix {b})"))
            continue
        for pat, why in BLOCKED_PATTERNS:
            if re.search(pat, s):
                findings.append((lineno, f"{why}: {s[:80]}"))

# House rules -------------------------------------------------------------------------
is_rules = "/rules/" in path.replace("\\", "/")
is_ui = "/ui/" in path.replace("\\", "/")
is_test = "/src/test/" in path.replace("\\", "/")
for lineno, line in enumerate(src.splitlines(), 1):
    s = line.strip()
    if s.startswith("//"):
        continue
    if is_rules and re.match(r"import\s+(android|androidx|kotlinx\.coroutines|java\.io|java\.nio)\b", s):
        findings.append((lineno, "rules/ must stay pure (no Android/Compose/IO imports) — it is the fixture-replayed oracle"))
    if is_rules and re.search(r"\b(java\.util\.Random|kotlin\.random|Random\(|Math\.random)", s):
        findings.append((lineno, "rules/ dice must use Mulberry32 (ADR-0006), not a platform RNG"))
    if is_ui and re.search(r"\bColor\s*\(\s*0x|\bColor\.(Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray|Black|White|Transparent|Unspecified)\b|\bColor\s*\(\s*\d", s):
        findings.append((lineno, "colour literal in ui/ — read colours only from LightThemeTokens.colors (monochrome is the tool's job)"))
    if is_ui and re.search(r"\b(Switch|Checkbox|RadioButton|TabRow|AlertDialog|Slider|Scaffold|TopAppBar|NavigationBar)\s*\(", s):
        findings.append((lineno, "Material component — build from sdk:ui primitives (LightText + lightClickable + glyphs) instead"))
    if is_test and re.search(r"\bimport\s+org\.junit\b", s):
        findings.append((lineno, "tests use kotlin.test (message is the LAST argument), not JUnit4 imports"))
    if re.search(r"\bLazyColumn\s*\(", s) and not re.search(r"LightLazyScrollView", src):
        findings.append((lineno, "bare LazyColumn — use LightLazyScrollView (uniform rows) or LightScrollView"))

if findings:
    print(f"scan-kotlin: {len(findings)} issue(s) in {path}", file=sys.stderr)
    for ln, msg in findings[:25]:
        print(f"  line {ln}: {msg}", file=sys.stderr)
    print("These fail the light-sdk plugin scan at Gradle configure time (or break a house rule). Fix before building.", file=sys.stderr)
    sys.exit(2)
PY
