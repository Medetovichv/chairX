# Import audit — 2026-11-02

Scope: PR #129, branch `feature/upstream-design-skills`, base `main` at `e1194166917c697a8af89d08b03440e9c2799be3`. Upstream default branches were cloned and pinned at the commits in [upstream-lock.json](upstream-lock.json). “Current” means those observed snapshots, not a promise that upstream will remain unchanged.

## Licenses and attribution

- UI UX Pro Max: upstream MIT text with `Copyright (c) 2024 Next Level Builder` is retained. Data provenance, Google font license metadata and Phosphor package/source metadata are included unchanged. These are recommendation/catalog data, not a redistribution of all font binaries or icon SVGs. Fonts/icons later downloaded for an application still require their own applicable licenses.
- Impeccable: upstream Apache-2.0 text with `Copyright 2025 Paul Bakaus` and NOTICE.md retained. No modifications to its distributed skill files.
- Impeccable iOS/Android guidance derives from ehmo/platform-design-skills under MIT. Its original license is included at `impeccable/third-party-licenses/platform-design-skills.txt`, retrieved from upstream revision `dc2be825d8b439caea78e9eaa8fb3ac23b0ff3e9`. The upstream notice attributes ehmo; its license text states `Copyright (c) 2026` without naming an author.
- Impeccable bundles `modern-screenshot.umd.js`. MIT license from qq15725/modern-screenshot is included at `impeccable/third-party-licenses/modern-screenshot.txt`, retrieved while upstream HEAD was `792d6db7411839c62940a6e930161f8e376e817f` (`Copyright (c) 2021-present wxm`). The exact library build version is not identified in Impeccable's bundled minified file; the added notice preserves the source project's terms, but does not establish an exact source-to-bundle mapping.
- Vercel explicitly declares MIT in SKILL.md and its repository README. The full upstream README is retained as `UPSTREAM-README.md` as evidence. **No LICENSE or separate copyright grant text exists in this upstream snapshot.** No copyright holder or license grant was fabricated. This is a remaining upstream attribution/documentation gap; keep it visible for a reviewer before merge. This audit is a source/notice review, not a legal certification.

## Script security and execution boundaries

UI UX's five runtime Python modules were inspected for imports, process/network calls and persistence. They use the standard library. Search reads local CSV/JSON; no shell execution, package installation, eval or network client was found in runtime modules. Design-system persistence creates directories and atomically writes Markdown under the chosen output directory, slugifies project/page names, skips existing output unless forced, and can overwrite when `--force` is supplied. Use a trusted output directory; slugification is not a defense against a malicious preexisting symlink tree.

Impeccable POSIX and Windows launchers were inspected. They can execute an explicit `IMPECCABLE_BIN`, a sibling engine, a home/PATH engine, or a cached/downloaded release engine. Download/cache verification uses SHA-256 from the shipped `engine-checksums.txt`; missing digest/hash support or a mismatch refuses a download. Explicit overrides bypass this verification; home/PATH candidates use an engine handshake rather than a hash, and locally supplied engines are trusted code. The launchers may create cache directories, restore executable bits and replace cached binaries. They do not sandbox the engine.

Downloaded macOS arm64 engine 0.1.18 matched `543ee2f72036ad185dc9991ac3ba86b74d91f988f3d497776a088b8b65c8a9e1`. Engine probe, help and launcher `context` succeeded against an empty scratch project. Context correctly reported missing PRODUCT.md/DESIGN.md and no active hook. No hook installation, live server, application scan, image API, telemetry workflow or global install was invoked. The binary remained outside the repository.

Browser helpers were inspected for their communication paths and syntax checked: live mode exchanges page/source/edit data with the engine's token-bearing localhost endpoints; the screenshot helper may fetch page resources. This is executable browser instrumentation, not passive guidance. The broader Rust engine has network-capable features (release/update checks, concept API, telemetry, font/image/browser workflows) and local file writes. Source inspection confirms telemetry opt-outs `DO_NOT_TRACK=1` and `IMPECCABLE_NO_TELEMETRY=1`; neither blocks all networking. Do not describe Impeccable as fully offline, download-free or comprehensively security-audited. No full Rust audit, Windows execution, Linux binary execution, browser end-to-end or image-service execution was performed.

Vercel's runtime payload is Markdown/JSON with example code; it has no executable helper. Examples are suggestions to assess against ChairX's actual stack, not commands to run or dependencies to add automatically.

## Integrity and links

- Offline verifier: 3 skills, 192 pinned files (including added attribution evidence), 2 patched files, 111 local Markdown/TOML links; passed. It checks exact file inventory/hashes and Python syntax. Original upstream hashes and patched hashes are both retained.
- Three broken upstream links in Vercel AGENTS.md now target `rules/async-defer-await.md`, `rules/async-cheap-condition-before-await.md` and `rules/server-hoist-static-io.md`.
- UI UX SKILL.md now invokes Python 3 through an absolute `UI_UX_SKILL_DIR`, replacing the Claude-only plugin path. No Python logic/data changed.
- External Markdown URL scan: 53 unique URL-like strings, 46 HTTP 200, 1 HTTP 403 (npm package page), and 6 examples/localhost addresses. The 403 is inconclusive, not evidence that the package page is missing. Example-domain 404s and inactive localhost endpoints were preserved as examples. Results are retained in [external-link-results.json](external-link-results.json). URL probes establish HTTP availability only; they do not validate external content or every URL inside CSV/JSON catalogs.
- No symlinks or compiled engine binaries were added.

## Validation results

| Check | Result |
|---|---|
| `python3 .agents/skills/verify.py` | Passed |
| Skill Creator `quick_validate.py`, all 3 skills | Passed using temporary PyYAML 6.0.3; system Python initially lacked YAML |
| UI UX `scripts/validate_data.py` | Passed: 12 domains, 22 stacks, reasoning contracts |
| UI UX upstream `test_core.py` | 39 passed against byte-identical imported Python/data before excluding maintenance tests |
| UI UX upstream `test_data_contracts.py` | 24 passed in pinned upstream checkout; same unmodified runtime/data hashes |
| UI UX focused UX search and design-system JSON | Both parsed; nonempty UX result and populated design-system output |
| Impeccable launcher `sh -n`, all 5 browser JS `node --check` | Passed |
| Impeccable pinned engine probe/help/context | Passed on macOS arm64 in scratch fixture |
| Frontend `npm ci --ignore-scripts` | Passed from lockfile; no dependency/lockfile changes |
| Frontend lint, typecheck, tests, build | Passed: 29 test files, 464 tests; Vite warns about JS chunk >500 kB |
| Backend `./mvnw --batch-mode --no-transfer-progress clean verify` | BUILD SUCCESS: 726 tests, 0 failures/errors, 1 skipped |

Backend ran with Java 25.0.4.1, Maven 3.9.16, Docker 29.7.2 and disposable Testcontainers PostgreSQL. The skipped test is `BackupUpgradeRehearsalTest.operatorProvidedBackup`, which requires an operator-provided backup. No production database or demo environment was started. Frontend ran with Node 24.20.0 and npm 11.19.0. The backend emitted existing deprecated-API notices.

## Application isolation and workflow limits

The complete PR changes only `.agents/skills/**`; application/backend/frontend trees, dependencies, workflows and Compose are identical to the base. Docker builds use `./frontend` and `./backend` contexts, so the skill payload is outside those images. No app import, install hook or automatic command execution was introduced. Current frontend/backend CI workflows filter for their own application paths, so this skills-only PR should not be presented as having automatically run those jobs; the results above are local verification.

Ordinary ChatGPT + GitHub use is document retrieval and application of explicitly read guidance. It does not establish a runtime or slash-command registration. Supported coding hosts may discover `.agents/skills` and run helpers if available. See the [usage instructions](README.md). A real ordinary-chat UI session was not independently tested; capability statements are scoped to repository retrieval versus execution requirements.

PR remains draft/open; no merge was performed. Remaining limitations are the Vercel license-text gap, exact modern-screenshot build provenance, unverified npm-page HTTP access and unexecuted platform/browser/API paths described above.
