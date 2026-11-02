# Community design skills for ChairX

Runtime instructions and resources from three upstream projects, pinned on 2026-11-02. These are upstream skills with two documented portability/link patches, not ChairX-authored replacement guidance.

| Skill | Pinned upstream commit | Included resources | License evidence |
|---|---|---|---|
| [UI UX Pro Max](ui-ux-pro-max/SKILL.md) | [713eef51](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/commit/713eef51b04efc98d6fa565323b495f7c9c89fd4) | Complete runtime `scripts/`, `data/`, `references/` | [MIT, Next Level Builder](ui-ux-pro-max/LICENSE) |
| [Impeccable](impeccable/SKILL.md) | [9c3808ba](https://github.com/pbakaus/impeccable/commit/9c3808ba441337fce03b3e34aa06efc434503162) | Complete upstream `.agents/skills/impeccable` distribution: references, launchers, browser assets, data and agent metadata | [Apache-2.0, Paul Bakaus](impeccable/LICENSE), [upstream NOTICE](impeccable/NOTICE.md), [third-party details](AUDIT.md#licenses-and-attribution) |
| [Vercel React Best Practices](vercel-react-best-practices/SKILL.md) | [1a2c79d4](https://github.com/vercel-labs/agent-skills/commit/1a2c79d4e6c4a2283dc2c614b6538cbe1ed5ca62) | All 70 rules, section/template files, compiled `AGENTS.md`, metadata and upstream README | MIT declared in SKILL.md and [upstream repository README](vercel-react-best-practices/UPSTREAM-README.md); **upstream has no standalone license text** |

[upstream-lock.json](upstream-lock.json) records original file SHA-256 hashes, local patch hashes and additional attribution files. [AUDIT.md](AUDIT.md) records checks and limitations. Run `python3 .agents/skills/verify.py` from the repository root for offline inventory, hash, Python syntax and local Markdown/TOML link checks. The validator does not execute imported code or access the network.

## Ordinary ChatGPT with GitHub

A GitHub connection provides access to repository content; merely committing these folders does not establish skill registration, automatic activation, slash commands, a shell, Python or a browser. Explicitly ask the assistant to fetch/read the skill and relevant supporting files from this branch (or its commit). If a file cannot be fetched, say so rather than infer its contents.

Example request:

> In Medetovichv/chairX, branch feature/upstream-design-skills, read .agents/skills/README.md, then the Impeccable SKILL.md and reference/critique.md. Review the existing sales screen using the source files you can retrieve. Preserve ChairX business rules and permissions. State which sources you read and which visual or executable checks you could not perform. Do not merge.

Use Impeccable for review/refinement, UI UX Pro Max for design research, and Vercel for applicable React performance guidance. ChairX uses React/Vite and TanStack Query: Next.js server/RSC rules are conditional examples, and SWR guidance does not require replacing the existing data library. Skill text does not authorize unrelated changes, deployments, messages, hooks, paid APIs or merges.

Without an execution environment, read Impeccable references and existing project context directly, inspect relevant UI UX CSV data, and read Vercel rule Markdown. Clearly label this as document-based guidance; do not claim that search, context, detector, browser or tests ran. Impeccable's own launcher-unavailable fallback permits direct context reading. Browser iteration, native device checks, image generation and subagent roles require corresponding tools and authorization in the actual host.

Official documentation describes skills as instructions plus optional resources and repo-specific discovery in supported agents; it does not establish automatic installation through an ordinary GitHub connection: [Build skills](https://learn.chatgpt.com/docs/build-skills), [Customization](https://learn.chatgpt.com/docs/customization/overview).

## Coding agent with a checkout and execution tools

Supporting agents can discover `.agents/skills`; check the host's available skills rather than assume activation. Read the selected `SKILL.md` first. No global installation is needed for the bundled resources.

UI UX Pro Max: Python 3, standard library only. From the repository root:

```sh
export UI_UX_SKILL_DIR="$(pwd)/.agents/skills/ui-ux-pro-max"
python3 "$UI_UX_SKILL_DIR/scripts/search.py" "keyboard focus modal" --domain ux
python3 "$UI_UX_SKILL_DIR/scripts/search.py" "internal analytics dashboard" --design-system
python3 "$UI_UX_SKILL_DIR/scripts/validate_data.py"
```

Default search is read-only. `--persist` writes under the chosen `--output-dir`; read existing output before using `--force`. Do not use private customer or financial data as research input. The local path variable replaces the upstream Claude-plugin-only path; set it to an absolute path if running elsewhere.

Impeccable: use `.agents/skills/impeccable/scripts/impeccable` (Windows: `impeccable.cmd`), keeping cwd at the project. The bundled launcher pins engine **0.1.18** and verifies downloaded binaries against the bundled checksum manifest. The engine is **not committed**: first use may download it to a user cache. A preinstalled binary can be selected through `IMPECCABLE_BIN`; that override is trusted and not checksum-verified by the launcher. Review [the audit](AUDIT.md#script-security-and-execution-boundaries) before enabling network, live browser workflows, hooks or image APIs. `DO_NOT_TRACK=1` disables supported telemetry; it does not disable every network feature. No hooks, global agents or app scripts are installed by this PR.

Vercel: read individual `rules/*.md` or the compiled guide; no executable setup is needed. The imported upstream skill README describes upstream authoring/build tooling which is not part of this runtime distribution; its `pnpm build` instructions are not ChairX commands.

## Scope and provenance

Only `.agents/skills/**` changes. Application code, lockfiles, CI, Docker build contexts and production data are unchanged. This does not prove every future use of the skills is harmless: scripts run only when explicitly invoked by a host with suitable permissions.

The UI UX runtime import excludes upstream `scripts/tests/**`, which contains repository-maintenance tests reaching outside the installed skill. Upstream CLI installers, galleries, unrelated skills, build tools, Rust engine source and release binaries are not runtime assets and are not vendored. Impeccable browser JavaScript and font metadata are included as shipped. No required runtime asset directory was omitted.

Local changes are limited to UI UX Python/path portability and three relative links inside Vercel's compiled guide. Impeccable uses upstream's existing Codex-oriented distribution instead of the initial Pi-oriented entrypoint. All other runtime files match their pinned upstream bytes.
