# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Nothing yet.

## [1.3.0] - 2026-09-14

### Added
- **JavaFX support.** The agent can now drive JavaFX applications as well as Swing
  ones, through exactly the same 39 tools — a client never names a toolkit. Applications
  that mix both in one JVM (a `JFXPanel` inside a Swing frame) work too: component
  uids are prefixed by the toolkit that issued them (`comp-` for Swing, `fx-` for
  JavaFX) and each command is routed to the toolkit that owns the uid it carries.
- **Tool annotations and titles** on all 39 tools (`readOnlyHint`, `destructiveHint`,
  `idempotentHint`, `openWorldHint`, `title`). Clients that honour the hints — Claude
  Desktop, VS Code, Cursor — can skip the per-call approval prompt for the 12 read-only
  tools and flag the three whose own effect is irreversible (`stop_app`,
  `close_window`, `evaluate_java`). Tools migrated from Spring AI's generic `@Tool`
  to `@McpTool`; `McpToolConfig` removed in favour of the starter's annotation scanner.
- `UiToolkit` interface in `swing-mcp-common` and `ToolkitRegistry` in the agent,
  so a further toolkit means implementing one interface rather than touching
  dispatch. See `docs/adr/0001-multi-toolkit-agent.md`.
- `swing-mcp-demo-fx`: a JavaFX demo application mirroring the Swing one, plus 20
  integration tests that drive it through the toolkit and 8 that drive it through the
  real server over stdio (`ServerRoundTripIT`), so a server↔agent contract mismatch
  fails the build.
- `ToolAnnotationsTest` pins the read-only and destructive sets; `JavaFxAbsentTest`
  proves a Swing-only JVM never loads a `javafx.*` class.
- CI asserts the agent jar contains no JavaFX runtime classes.

### Changed
- Threading and the modal-dialog fire-and-poll protocol moved from `CommandHandler`
  into the toolkit, because the rules differ per toolkit. Swing behaviour is
  unchanged: `SwingToolkit` is a thin adapter over the existing `ComponentScanner`
  and adds no scanning logic of its own.
- In a JavaFX snapshot a `Control` is reported as a single node; its skin sub-graph
  (`LabeledText`, caret and selection `Path` nodes) is omitted as a rendering detail.
  Controls that hold real content — `ScrollPane`, `TabPane`, `SplitPane`,
  `TitledPane`, `Accordion`, `ToolBar`, `ButtonBar` — are traversed explicitly.
- Tool descriptions served over `tools/list`, the MCPB manifest, README, docs and
  skills now say Swing *and* JavaFX, so clients and models know the capability exists.
  `take_snapshot` explains the `comp-`/`fx-` prefixes; `evaluate_java` and `drag`
  are marked Swing-only.
- `tools/list` grew from 14.4 KB to 22.1 KB (roughly 3.8k → 5.8k tokens per request):
  4.5 KB is the annotations themselves, 2.1 KB is a `$schema` line the annotation
  scanner adds to every input schema. The latter is pure overhead and is a candidate
  for stripping in a follow-up.
- `additionalProperties: false` is no longer emitted on input schemas (scanner
  behaviour); property names, types, descriptions and `required` arrays are unchanged.

### Notes
- JavaFX is an optional dependency: compiled against at `provided` scope and
  resolved reflectively, so no `javafx.*` class is loaded in a Swing-only
  application and the agent jar never bundles a JavaFX runtime.
- `evaluate_java` and `drag` remain Swing-only; on JavaFX they fail with a message
  saying so rather than a stack trace.
- Structured output (`outputSchema`) is not part of this release; every tool still
  returns a JSON string in a text block.

## [1.2.3] - 2026-09-08

### Fixed
- The MCP `serverInfo.version` reported to clients was hardcoded to `1.1.0`; it now
  follows the Maven project version (`application.yml` is filtered at build time).

### Changed
- `docs/installation.md`: unversioned jar paths, the unzip-the-`.mcpb` route for
  clients that need the jars, `mcpServers` config shape for IntelliJ IDEA (JetBrains
  AI Assistant), Claude Desktop extension install first, link to the install video.
- New per-client guides in `docs/install/` (Claude Desktop, Claude Code, VS Code +
  GitHub Copilot, Cursor, Devin Desktop/Windsurf, IntelliJ IDEA, OpenAI Codex CLI,
  Gemini CLI), each paired with a one-minute video on crosstech.solutions/swing-mcp.
## [1.2.2] - 2026-09-04

### Changed
- Registry/extension listing: benefit-first description, `websiteUrl` and icon in `server.json`; MCPB manifest now ships an icon, three screenshots, a markdown long description, license and richer keywords (`mcpb/assets/`, copied into the bundle by the publish workflow).

## [1.2.1] - 2026-09-01

### Fixed
- `publish` workflow compile break in `AgentServer.handleClient` (#13). This is the
  version actually published to the MCP Registry and attached to the GitHub release;
  the `V1.2.0` tag was never published. Functionally identical to 1.2.0 below.

## [1.2.0] - 2026-09-01

### Security
- **Agent socket now requires a per-session auth token.** The agent generates a
  random token at startup and hands it to the paired MCP server via the (now
  owner-only) port response file; every command must carry it or is rejected.
  Closes the hole where any local process could connect to the loopback socket
  and drive — or code-exec — the automated application.
- **`evaluate_java` is enforced in the agent, not only the server.** The
  `swing.mcp.evaluate.enabled` flag (default `false`) is passed to the agent and
  checked there, so arbitrary Java evaluation can no longer be triggered by
  talking to the socket directly. The `evaluate_java` JShell instance is now
  always closed (previously it leaked a forked JVM per call) and prefers
  in-process (`local`) execution.
- The token handshake file is the server's owner-only temp file (0600 /
  user-scoped ACL) written *in place* by the agent — never deleted and
  recreated with default permissions — and is removed as soon as it is read.
  The server requires both port and token before connecting, so a partial
  write can never yield an unauthenticated session.
- A connection that presents a bad token is closed on the first attempt, and
  the token is redacted from all agent log output.

### Fixed
- Applications launched via `launch_app` are now closed on server shutdown
  (`@PreDestroy`) instead of being orphaned on every stdio-MCP restart.
- `stop_app` forcibly terminates a process that ignores graceful shutdown
  (`destroyForcibly` fallback), so frozen/modal apps are actually stopped.
- Launch commands are tokenized with quote awareness, so paths and arguments
  containing spaces (e.g. `"/Users/My App/app.jar"`) no longer break.
- `select_option` by text now fails with the list of available options when
  nothing matches, instead of silently reporting success.
- `windowIndex` is now honored by `take_snapshot`, `close_window`, `resize_window`
  and friends (it was documented but silently ignored — `close_window(windowIndex=1)`
  could close the main frame). Out-of-range indices fail clearly.
- Clearer error when the target application exits mid-command (the session is
  reported dead with a hint to relaunch, rather than a generic transport error).
- Temporary port/output files are marked `deleteOnExit`; the port-file read
  tolerates partial writes instead of throwing a raw `NumberFormatException`.

### Added
- `take_snapshot` accepts `maxNodes` (default 2000) and `maxDepth` limits and
  returns `"truncated": true` when the tree was cut short — preventing a single
  snapshot of a large UI from blowing the AI client's context window.
- Project moved to the CrossTech organization: repository transferred to
  `github.com/crosstech-solutions-bv/swing-mcp` (old URLs redirect), Maven
  `groupId` changed from `io.github.tinusj` to `solutions.crosstech`, Java
  packages renamed `io.github.tinusj.swingmcp.*` → `solutions.crosstech.swingmcp.*`
  (breaking for code importing these classes), and the MCP registry name is now
  `io.github.crosstech-solutions-bv/swing-mcp`.
- Demo GIF in the README showing snapshots, clicks, form filling, tables, trees, menus, and dialogs.

### Removed
- Stray `test/current` directory (#10).

## [1.1.0] - 2026-07-06

### Added
- MCP Registry packaging: `server.json`, MCPB bundle, and a `publish-mcp` GitHub Actions workflow (#5).
- Comprehensive MCP Registry publishing guide under `docs/registry-publishing.md` (#6).

### Fixed
- Case-sensitive server name `io.github.crosstech-solutions-bv/swing-mcp` in registry metadata (#6).
- `server.json` name casing, description, and identifier URL (#7).

## [1.0.0] - 2026-07-05

Initial release.

### Added
- `swing-mcp-server`: Spring Boot MCP server (stdio transport) exposing Swing automation tools.
- `swing-mcp-agent`: Java agent loaded into the target Swing JVM (via `-javaagent` or dynamic attach by PID), running a loopback-only JSON line-protocol socket server that executes commands on the Swing EDT.
- `swing-mcp-common`: shared command/DTO types between server and agent.
- `swing-mcp-demo`: demo Swing application used for integration testing.
- Full tool set: inspection (`take_snapshot`, `find_component`, `get_component_details`, tables/lists/trees), interaction (`click`, `fill`, `select_option`, menus, drag, keyboard), dialogs, window management, clipboard, wait conditions, inline screenshots, and multi-session support (#3).
- Agent skills for AI coding agents: `swing-mcp`, `swing-ui-testing`, `troubleshooting` (#4).
- Documentation: per-category tool docs, single-page tool reference, per-client installation guide (IntelliJ IDEA, VS Code, Claude Desktop, Claude Code, Cursor, Windsurf) (#1, #2).
- CI with GUI integration tests under `xvfb`; restricted workflow permissions.

### Changed
- Logging migrated to `logback-spring.xml`; agent command handling updated for modal dialogs.

[Unreleased]: https://github.com/crosstech-solutions-bv/swing-mcp/compare/V1.3.0...HEAD
[1.3.0]: https://github.com/crosstech-solutions-bv/swing-mcp/compare/V1.2.3...V1.3.0
[1.2.3]: https://github.com/crosstech-solutions-bv/swing-mcp/compare/V1.2.2...V1.2.3
[1.2.2]: https://github.com/crosstech-solutions-bv/swing-mcp/compare/V1.2.1...V1.2.2
[1.2.1]: https://github.com/crosstech-solutions-bv/swing-mcp/compare/V1.2.0...V1.2.1
[1.2.0]: https://github.com/crosstech-solutions-bv/swing-mcp/compare/V1.1.0...V1.2.0
[1.1.0]: https://github.com/crosstech-solutions-bv/swing-mcp/compare/V1.0.0...V1.1.0
[1.0.0]: https://github.com/crosstech-solutions-bv/swing-mcp/releases/tag/V1.0.0
