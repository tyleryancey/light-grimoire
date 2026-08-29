# Tool Library Submission — Grimoire

- **Name:** Grimoire
- **Tool ID:** dev.tyler.grimoire
- **Description:** TODO: one paragraph, in Tyler's own words (draft to react to: "A player
  companion for 5E-compatible tabletop games. Track hit points, spell slots, feature uses,
  conditions and coin; roll what your turn needs with one tap; look up the open rules
  (System Reference Document 5.1) offline; keep a short session journal with the scroll
  wheel. No account, no network, no permissions.")
- **Repository:** https://github.com/tyleryancey/light-grimoire
- **Commit:** the SHA of the release tag — `git rev-list -n 1 v0.1.0`
- **Version:** 0.1.0 (versionCode 1)
- **Permissions:** none
- **Build command:** `./gradlew :tool:assembleRelease`
- **Content licence:** bundled rules text is SRD 5.1 under CC-BY-4.0 (attribution in the
  About screen and README); application code MIT.
- **Testing notes:** M0 (28 Aug 2026) on a physical Light Phone III (TLP301, LightOS 572-release-lp3):
  hardware key codes recorded in `docs/sdk-facts-delta.md` §Hardware results; first-launch compendium
  import 3.1–3.3 s (branch `spike/import-timing`); debug APK builds through the plugin scan with the
  2.63 MB compendium bundled; CI `check` + `submission-check` green on PR #1. Emulator QA and release
  QA: TODO in M6.
