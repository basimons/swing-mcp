# 1. One agent, many UI toolkits

Date: 2026-09-13
Status: Accepted

## Context

Swing MCP drives Java Swing applications by attaching a Java agent to a running
JVM, reading the component tree, and acting on the Event Dispatch Thread. The
next toolkit we want to reach is JavaFX — the place Swing applications go when
they modernise, and frequently present in the *same* JVM as Swing through
`JFXPanel`.

Two things constrain the design.

**The agent runs inside someone else's application.** It is attached to a
process we do not control, alongside libraries we did not choose. It must load
and run unchanged in an application that has never heard of JavaFX — which is
almost all of them — and it must not impose a JavaFX version on an application
that already has one.

**A JavaFX scene graph is not a component tree.** Every `Control` is drawn by a
skin that is itself a sub-graph. A single `Button` is a `Button` containing a
`LabeledText`; a single `TextField` contains `Pane`, `Path` and `Text` nodes for
its caret and selection. Measured on our own demo window, a naive walk returns
roughly four times as many nodes as there are things a user can see. Handing
that to a model is both more expensive and less useful than the Swing
equivalent.

## Decision

**A `UiToolkit` interface in `swing-mcp-common` describes what the agent needs
from a toolkit**, and `ToolkitRegistry` in the agent routes each command to an
implementation. `SwingToolkit` is a thin adapter over the existing
`ComponentScanner`; `JavaFxToolkit` is new.

**The MCP tool surface does not change.** The server already speaks `CommandType`
over a socket and knows nothing about widgets, so a client never names a
toolkit — it works with whatever the target application happens to use.

**Each toolkit issues uids under its own prefix** — `comp-` for Swing (unchanged,
because released clients hold those uids) and `fx-` for JavaFX. The registry
routes a command carrying a uid back to the toolkit that issued it, which is
what makes a mixed Swing + JavaFX application work. Commands with no uid go to
the toolkit that currently has a focused window.

**JavaFX is an optional dependency, resolved reflectively.** It is compiled
against at `provided` scope and never shaded. `ToolkitRegistry` probes for
`javafx.application.Platform` before naming `JavaFxToolkit`, so in a Swing-only
application no `javafx.*` class is ever resolved. CI asserts the built agent jar
contains no `javafx/` entries.

**In the JavaFX walker, a `Control` is a leaf.** Its value is read through its own
API rather than by descending into its skin. The exceptions are the controls
that genuinely hold user content — `ScrollPane`, `TabPane`, `SplitPane`,
`TitledPane`, `Accordion`, `ToolBar`, `ButtonBar` — whose content is traversed
explicitly. `find_component` uses the *same* `contentOf()` definition as the
walker, so the two cannot drift apart.

**Threading and the modal-dialog protocol belong to the toolkit**, not the
command handler, because the rules differ between toolkits. `AbstractUiToolkit`
holds the shared policy: queries are marshalled with a hard timeout; actions are
fire-and-poll and return the open dialogs if they do not complete.

**JavaFX interaction uses each control's own API** (`fire()`, `setText`,
selection models) rather than synthesised native input. It is deterministic,
works when the window is not focused, and is immune to DPI scaling.

## Consequences

Good:

- Swing behaviour is unchanged; `SwingToolkit` adds no scanning logic of its own.
- A JavaFX snapshot is roughly a quarter the size of a naive walk and contains
  only nodes a user could point at.
- Mixed applications work without the client knowing anything about toolkits.
- Adding a third toolkit means implementing one interface, not touching dispatch.

Costs and things to watch:

- `UiToolkit` is a wide interface — one method per command. That is a deliberate
  trade for a complete Swing adapter with no behaviour change, but it means a new
  command touches the interface and every implementation. If the tool surface is
  consolidated later (see below), this interface should shrink with it.
- The action commands (`click`, `selectMenuItem`, `selectContextMenuItem`,
  `handleDialog`, `closeWindow`) return `Object`, not `String`, because a pending
  modal-dialog result is a map. An earlier draft returned `String` and silently
  stringified that map; the existing `ModalDialogCommandTest` caught it.
- `evaluate_java` and `drag` are Swing-only. They fail on JavaFX with a message
  that says so rather than a stack trace.
- Screenshots convert pixels directly rather than via `SwingFXUtils`, which lives
  in the `javafx.swing` module a pure JavaFX application has no reason to ship.

## Not decided here

The tool surface is still 39 tools. The ecosystem is moving towards small,
composable surfaces with tool search and code execution, because tool
definitions are charged to context on every request. This ADR deliberately keeps
the surface fixed so that adding a toolkit changes nothing for existing clients;
consolidating it is a separate, breaking decision that needs its own ADR and a
deprecation path.

## Addendum 2026-09-14 — tool annotations

Every tool now declares MCP tool annotations. The classification rule, so it stays
consistent as tools are added:

- `readOnlyHint = true` for tools that only observe: snapshots, finds, reads, lists,
  status, screenshot, `wait_for`. These are also `idempotentHint = true`.
- `destructiveHint = true` only for tools whose **own** effect is irreversible regardless
  of the target application: `stop_app`, `close_window`, `evaluate_java`. Generic
  interaction tools (`click`, `type_text`, `handle_dialog`, …) are *not* marked
  destructive, because their effect belongs to the application, not the tool — a click
  on "Delete all" is destructive and a click on "Cancel" is not, and the tool cannot
  tell. Clients should still treat non-read-only tools as needing care.
- `idempotentHint = true` for set-style writes whose repetition is harmless (`fill`,
  `select_*`, `focus`, `hover`, window geometry, `set_clipboard`); `false` for anything
  that fires an action.
- `openWorldHint = false` everywhere: the server drives one local application.

Cost: `tools/list` grew by ~53% (14.4 KB → 22.1 KB). About 4.5 KB is the annotations —
the feature — and about 2.1 KB is a `$schema` URL the annotation scanner emits on every
input schema, which carries no information and should be stripped when there is a clean
hook for it. `ToolAnnotationsTest` pins the sets above.
