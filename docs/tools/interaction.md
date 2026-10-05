# Interaction tools

Tools for interacting with components in the active window. All UID parameters
come from `take_snapshot` (see [inspection.md](inspection.md)). Actions are
executed on the owning toolkit's UI thread: the Event Dispatch Thread for
Swing, the JavaFX Application Thread for JavaFX.

## `click`

Click a component. `AbstractButton`s (buttons, checkboxes, radio buttons,
toggle buttons) are clicked directly via `doClick()`; other components via
mouse emulation.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `uid` | string | yes | Component UID |
| `button` | string | no | `LEFT` (default), `RIGHT`, `MIDDLE` |
| `clickType` | string | no | `SINGLE` (default) or `DOUBLE` |

## `hover`

Move the mouse over a component to trigger hover effects and tooltips
(mouse-emulation move without a click).

| Parameter | Type | Required | Description |
|---|---|---|---|
| `uid` | string | yes | Component UID |

## `focus`

Give keyboard focus to a component so that subsequent `press_key` or
`type_text` calls target it deterministically.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `uid` | string | yes | Component UID |

## `type_text`

Type text character-by-character into the focused component using key events
(as opposed to `fill`, which sets the value directly). Use this for UIs with
per-keystroke listeners, input masks, or autocompletion.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `text` | string | yes | Text to type |
| `uid` | string | no | Component UID to focus before typing |

## `fill`

Set the text/value of a text component (`JTextField`, `JTextArea`, …),
`JSpinner`, `JSlider`, or editable `JComboBox` (Swing) — or a
`TextInputControl` (`TextField`, `PasswordField`, `TextArea`), `Spinner`, or
editable `ComboBox` (JavaFX).

| Parameter | Type | Required | Description |
|---|---|---|---|
| `uid` | string | yes | Component UID |
| `text` | string | yes | Text or value to enter |

## `select_option`

Select an option in a `JList`, `JComboBox`, or `JTabbedPane` (Swing) — or a
`ComboBox`, `ChoiceBox`, `ListView`, or `TabPane` (JavaFX) — by index or
visible text.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `uid` | string | yes | Component UID |
| `index` | number | no | Zero-based option index |
| `text` | string | no | Visible text of the option |

**Notes:** provide either `index` or `text`.

## `select_tree_node`

Select a `JTree` (Swing) or `TreeView` (JavaFX) node by path.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `uid` | string | yes | `JTree` (Swing) or `TreeView` (JavaFX) component UID |
| `path` | string | yes | Node path separated by ` > `, e.g. `Root > Folder > Leaf` |

## `select_table_cell`

Select a `JTable` (Swing) or `TableView` (JavaFX) cell.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `uid` | string | yes | `JTable` (Swing) or `TableView` (JavaFX) component UID |
| `row` | number | yes | Zero-based row index |
| `col` | number | yes | Zero-based column index |

## `select_menu_item`

Click a menu item in the active window's menu bar (`JMenuBar` for Swing,
`MenuBar` for JavaFX).

| Parameter | Type | Required | Description |
|---|---|---|---|
| `path` | string | yes | Menu path separated by ` > `, e.g. `File > Save` |

## `select_context_menu_item`

Open the context menu of a component and click an item by path
(`JPopupMenu` for Swing, `ContextMenu` for JavaFX). The component's
registered popup menu is used when available; otherwise a right-click popup
trigger is dispatched.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `uid` | string | yes | Component UID |
| `path` | string | yes | Menu item path separated by ` > `, e.g. `Copy` or `Refactor > Rename` |

## `press_key`

Press a key or key chord using AWT `Robot`.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `keys` | string | yes | Key or chord such as `ENTER`, `TAB`, `CTRL+S` |

## `drag`

Drag from one component to another using mouse emulation.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `fromUid` | string | yes | Source component UID |
| `toUid` | string | yes | Target component UID |

**Notes:**
- Swing only; on a JavaFX uid this fails with an error saying so.

## `scroll`

Scroll a component inside a `JScrollPane` (Swing) or `ScrollPane` (JavaFX).

| Parameter | Type | Required | Description |
|---|---|---|---|
| `uid` | string | yes | Component UID, usually inside a scroll pane |
| `direction` | string | no | `UP`, `DOWN` (default), `LEFT`, `RIGHT` |
| `amount` | number | no | Scroll units (default 3) |

**Notes:**
- A component with no enclosing scroll pane gets `amount` mouse-wheel notches
  at its centre instead (Shift+wheel for `LEFT`/`RIGHT`). This covers custom
  scrollers and JavaFX `ListView`, `TableView` and `TreeView`.

## `mouse_wheel`

Rotate the mouse wheel over a component, at an optional point and with
optional modifier keys held. Use it for components that react to the wheel
themselves, such as zooming or panning a graph, chart or map, where `scroll`
has no scrollbar to move.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `uid` | string | yes | Component UID |
| `rotation` | number | yes | Wheel notches, 1–100 either way; positive scrolls down, negative up |
| `x` | number | no | X within the component in pixels (default: centre) |
| `y` | number | no | Y within the component in pixels (default: centre) |
| `modifiers` | string | no | Keys held during the wheel: `CTRL`, `SHIFT`, `ALT`, `META`, combined as `CTRL+SHIFT` |

**Notes:**
- One event per notch (at most 100) goes to the deepest component under the
  point and travels up to the nearest wheel handler, as with a physical wheel.
  The events are synthesized, so no `Robot` or pointer movement is involved.
- Example: `mouse_wheel(uid, rotation=-2, modifiers="CTRL")` turns the wheel
  two notches up with Ctrl held, which many viewers use to zoom in.
