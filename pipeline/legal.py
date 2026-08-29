"""Write the attribution and license files that must travel with the bundled data.

The wording of the WotC attribution sentence is prescribed by the SRD 5.1 legal page and
must not be altered. CC-BY-4.0 §3(a)(1)(B) additionally requires a modification notice,
which the first paragraph provides. Do NOT add any other Wizards attribution or
trademark line — WotC asks that none be included beyond the sentence below."""
from __future__ import annotations

from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
LEGAL_DIR = REPO_ROOT / "tool" / "src" / "main" / "assets" / "legal"
LICENSES_DIR = REPO_ROOT / "LICENSES"

SRD_51_SENTENCE = (
    "This work includes material taken from the System Reference Document 5.1 (\"SRD 5.1\") by Wizards of the "
    "Coast LLC and available at https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is "
    "licensed under the Creative Commons Attribution 4.0 International License available at "
    "https://creativecommons.org/licenses/by/4.0/legalcode."
)

ATTRIBUTION_MD = f"""# Attribution

Grimoire is 5E compatible. It bundles rules text from the System Reference Document 5.1
published by Wizards of the Coast under the Creative Commons Attribution 4.0 International
License (CC-BY-4.0). The text has been reorganized into a searchable database for display
on a small screen: entries were split into fields and paragraphs, and cross-references were
converted to keys. No rules wording was changed.

{SRD_51_SENTENCE}

The structured JSON this tool was built from comes from the open-source project
5e-bits/5e-database (MIT-licensed code; the rules text it carries is the SRD 5.1 under
CC-BY-4.0). Its record structure was used; its text is the SRD's.

The full text of CC-BY-4.0 is included as LICENSE-CC-BY-4.0.txt. The bundled data is
provided "as is"; see Section 5 of CC-BY-4.0 for the disclaimer of warranties and
limitation of liability.

Application source code is licensed under the MIT License (see LICENSE in the repository).
This tool is a derivative of lightphone/light-sdk, also MIT-licensed.
"""


def write_legal(cc_by_text_path: Path) -> None:
    LEGAL_DIR.mkdir(parents=True, exist_ok=True)
    LICENSES_DIR.mkdir(parents=True, exist_ok=True)
    (LEGAL_DIR / "ATTRIBUTION.md").write_text(ATTRIBUTION_MD, encoding="utf-8")
    cc = cc_by_text_path.read_text(encoding="utf-8")
    (LEGAL_DIR / "LICENSE-CC-BY-4.0.txt").write_text(cc, encoding="utf-8")
    (LICENSES_DIR / "CC-BY-4.0.txt").write_text(cc, encoding="utf-8")
