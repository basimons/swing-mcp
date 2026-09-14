# 2. Structured tool output — typed results for the data tools only

Date: 2026-09-14
Status: Proposed (spike measured; not yet in the tree)

## Context

Every Swing MCP tool returns a `String`. The data-bearing ones (`take_snapshot`,
`get_table_data`, `app_status`, …) build that string with `ToolJson.toJson(...)`, so a
client receives JSON, but as opaque text inside a `content` block and with no schema
that says what is in it. The MCP spec has had a better answer since 2025-06-18:
a tool may declare an `outputSchema`, and its result then carries a
`structuredContent` object that clients can validate and consume without parsing
prose. Claude Desktop, VS Code and Cursor all read it; the registry and the
"servers that return data, not walls of text" guidance both expect it. The 1.3.0
review listed it as the next alignment item after tool annotations.

Spring AI 2.0.0's `@McpTool` supports it directly: `generateOutputSchema = true` on a
method with a non-`String` return type makes the starter derive a JSON Schema from the
return type, advertise it in `tools/list`, validate every result against it, and emit
`structuredContent` alongside the text block.

This ADR records what a spike against the 1.3.0 tree measured, and what we will and
will not do as a result.

## What the spike found

Three tools were converted — `app_status` (a small flat record), `get_table_data`
(a record with two lists) and `take_snapshot` (the recursive `SnapshotNode` /
`ComponentDescriptor` records already in `swing-mcp-common`) — and driven through the
real server over stdio against the Swing demo, protocol 2025-11-25.

1. **It works end to end, with one trap.** The generator marks every record component
   `required`, while our DTOs use `@JsonInclude(NON_NULL)` and drop null fields. The SDK
   validates results against the schema before sending them, so the very first
   `take_snapshot` came back as `isError: true` with a wall of
   "required property 'children' not found" messages. `@JsonProperty(required = false)`
   on every nullable component (`name`, `text`, `selectionState`, `children` on
   `ComponentDescriptor`; `windowTitle`, `truncated` on `SnapshotNode`) fixes it; the
   generator honours that annotation, Swagger's `@Schema`, and Spring's `Nullness`
   (JSpecify `@Nullable`). Any DTO that can omit a field must say so or the tool breaks.
2. **The result is sent twice.** With an output schema the SDK emits *both* the text
   block and `structuredContent`, byte for byte the same JSON: `take_snapshot` on the
   demo window is 12,785 B of text plus 12,785 B of structured content. That is the
   spec's backward-compatibility rule, not a Spring quirk. The wire payload of every
   converted tool doubles; the model-visible payload does not, because clients feed the
   text block to the model and keep `structuredContent` for validation and tooling.
3. **`tools/list` grows per converted tool.** Output schemas cost 761 B (`app_status`),
   488 B (`get_table_data`) and 1,130 B (`take_snapshot`, with its `$defs` recursion) —
   2,379 B for three, taking the list from 22.1 KB to 24.5 KB. At ~600 B per tool, typing
   all 39 would add roughly 20 KB and push `tools/list` to about 42 KB (≈11k tokens on
   every session start). The `$schema` line the generator writes into every input *and*
   output schema is 2.4 KB of that list today and would be 4.6 KB after a full migration.
4. **Error paths are unchanged.** A tool that throws still returns `isError: true` with
   the message as text; validation only runs on successful results. `app_status` with no
   session (`{"connected":false,"sessionCount":0}`) validates once the optional fields
   are declared.
5. **Null cells are a latent failure.** `get_table_data` rows are `List<List<String>>`;
   the generated schema says every item is a `string`, so a null cell (a Swing table
   model returning `null`) would fail validation at runtime. Cells must be rendered to
   `""` before they leave the agent, or the schema must allow `null`.
6. Existing server tests (29) pass with the three conversions in place.

## Decision

**Adopt structured output for the tools whose result is data, and only those.**

- Convert the read tools that return records or lists a client would want to process:
  `take_snapshot`, `find_component`, `get_component_details`, `get_table_data`,
  `get_list_items`, `list_windows`, `list_dialogs`, `list_sessions`, `app_status`,
  `launch_app`/`attach_to_app` (session info), `get_clipboard`, `take_screenshot`
  (path + dimensions). That is 13 tools, about 8 KB of output schema, and every one of
  them is where a validated shape actually helps.
- Leave the action tools (`click`, `fill`, `press_key`, the window moves, `handle_dialog`,
  `wait_for`, …) as plain text. Their result is a sentence ("Clicked comp-16",
  "Filled spinner with 42") or a small pending-dialog map; a schema buys nothing there
  and would cost ~15 KB of `tools/list`.
- Typed results live in `swing-mcp-common` next to the existing DTOs so the agent
  produces them and the server passes them through, instead of the server re-shaping a
  `Map` per tool. Every nullable component carries `@JsonProperty(required = false)`; a
  unit test walks each result type and asserts the generated schema validates a
  minimal instance (all optional fields absent) — the trap in finding 1 must not recur.
- Table cells are rendered to strings on the agent side (already the case) and never
  null; `get_list_items` items likewise.
- Strip the `$schema` line from generated schemas in the same change (an
  `McpServerFeatures` post-processor on the tool specifications), since it is pure
  overhead and doubles once output schemas arrive.

## Consequences

- Clients that honour `structuredContent` get validated, typed results from the tools
  that matter; models see exactly what they see today, because the text block is
  unchanged.
- `tools/list` goes from 22.1 KB to roughly 28 KB (schemas in, `$schema` lines out)
  rather than 42 KB. Per-call wire size doubles for the 13 data tools; for `take_snapshot`
  on a large window that is the one place to watch, and the existing `filter` and
  `maxNodes` limits are the lever.
- The wire contract of the 13 tools becomes explicit and enforced. A change to a DTO
  that forgets to update the schema (or nullability) fails at runtime on the first
  call, which is why the schema-validates-minimal-instance test is part of the change,
  not optional.
- This is additive for clients: text stays, `structuredContent` is new. It ships as a
  minor version (1.4.0), with the CHANGELOG naming the 13 tools.

## Alternatives considered

- **Type all 39 tools.** Uniform, but ~20 KB more `tools/list` on every session for
  action tools whose result is a sentence. Rejected on cost.
- **Structured output without the text block.** Halves per-call payload, but the spec
  requires the text block for clients that predate `structuredContent`, and the SDK
  does not offer the option. Rejected.
- **Keep JSON-in-text.** Zero cost, but it is exactly the pattern the ecosystem is
  moving away from, and the 1.3.0 review flagged it. Rejected.

## Spike artefacts

The measurements above come from a throwaway branch of the 1.3.0 tree (annotations
applied) with three converted tools; the numbers are reproducible with the server's
own stdio transport and the Swing demo under Xvfb. Nothing from the spike is in this
repository; the change lands as its own PR following this ADR.
