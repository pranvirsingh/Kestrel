# Status

**Current:** v2.0.0, the first public release (being prepared).

## v2.0.0

- `main` starts with the initial import commit: the source zip contents,
  byte for byte, plus `.gitattributes` (no line-ending conversion) and
  `.gitignore`.
- `feat/kestrel-development` adds the README (with screenshots rendered by
  the game's own tests), CHANGELOG, LICENSE (MIT for the code; the bundled
  Rajdhani fonts are OFL 1.1, text in `docs/licenses/`), project docs, the
  Claude Code skills, agent and hooks, and CI.
- Release asset: the original APK (`Kestrel-1.apk`, version 2.0 inside),
  renamed `Kestrel-v2.0.0.apk`
  (SHA-256 `32574264f5e9e13168510fd5f1f1a4cef45bb8f0cbb2bc4871961e1a379d2484`),
  signed with the original key.

## Next

Nothing planned. Before the first release built from source, create the new
permanent signing key (see [workflow.md](workflow.md#signing-keys)).
