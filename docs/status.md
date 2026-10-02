# Status

**Current:** v1.0.0, the first public release (being prepared).

## v1.0.0

- `main` starts with the initial import commit: the source zip contents,
  byte for byte, plus `.gitattributes` (no line-ending conversion) and
  `.gitignore`.
- `feat/blotto-development` adds the README (with screenshots rendered by
  the game's own `Harness` test), CHANGELOG, LICENSE (MIT for the code; the
  bundled fonts are Ultra under Apache 2.0 and Cinzel under OFL 1.1, texts
  in `docs/licenses/`), project docs, the Claude Code skills, agent and
  hooks, CI, and the branch protection settings.
- Blotto has no `t.sh`. CI compiles and runs the tests itself with the
  repo's own `kc.sh` (see `.github/workflows/ci.yml`).
- Release asset: the original APK, renamed `Blotto-v1.0.0.apk`
  (SHA-256 `ae8b2556c33cb8533655fa96d8da013eda735eed75a0b24e8cefd5090c0c6f46`),
  signed with the original key.

## Next

Nothing planned. Before the first release built from source, create the new
permanent signing key (see [workflow.md](workflow.md#signing-keys)).
