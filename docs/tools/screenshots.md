# Screenshot tools

## `take_screenshot`

Capture a PNG of the active window or a single component.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `uid` | string | no | Component UID; omit to capture the whole active window |
| `returnImage` | boolean | no | When `true`, include the base64-encoded PNG data in the response |
| `mode` | string | no | `PAINT` (default) renders the UI into the image; `SCREEN` copies the screen region |

**Returns:** the path of the saved PNG file, its size and `mode`, plus the
base64-encoded image data when `returnImage=true`.

**Notes:**
- Before capturing, the events already queued on the UI thread are processed,
  so the image shows the result of the last action instead of lagging one
  repaint behind.
- `PAINT` renders the component (for a window: its content, menu bar and
  lightweight popups) on the Event Dispatch Thread. Windows covering it,
  notifications, or a window being in the background do not affect the image,
  and components that are not on screen (e.g. in another tab) can be rendered.
  It works in headless environments too.
- `SCREEN` is the previous behaviour: it copies the screen pixels with
  `java.awt.Robot`, so it includes the native title bar, heavyweight popups and
  anything covering the window. Use it when native rendering matters.
- JavaFX always renders the node (`Node.snapshot`), whatever the `mode`.
- Images are saved under `swing.mcp.screenshot-dir`
  (default `${java.io.tmpdir}/swing-mcp-screenshots`).
- Use `returnImage=true` when the MCP client has no access to the server's
  local filesystem.
