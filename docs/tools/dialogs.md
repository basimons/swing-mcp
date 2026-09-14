# Dialog tools

Tools for handling modal dialogs — `JOptionPane`, file choosers, and custom
`JDialog`s in Swing; `Dialog`/`Alert` and modal `Stage`s in JavaFX — which
frequently block the flow of Java desktop applications. Both toolkits use the
same fire-and-poll protocol: the triggering action returns
`modalDialogOpen=true`, then `list_dialogs` / `handle_dialog` takes it from
there.

## `list_dialogs`

List currently open dialogs.

No parameters.

**Returns:** for each dialog: window `index`, `title`, `class`, `modal` flag,
`type` (`option` for `JOptionPane`-based dialogs, `fileChooser` for
`JFileChooser` dialogs, `custom` otherwise), the `message` (for option
dialogs), and the texts of its `buttons`.

**Notes:**
- On JavaFX, `message` and `buttons` are read from the dialog's
  `DialogPane`: its content text, header text, and button types.

## `handle_dialog`

Respond to an open dialog by clicking a named button or selecting a file in a
`JFileChooser`. Targets the first open dialog unless a window index is given.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `button` | string | no | Text of the dialog button to click, e.g. `OK`, `Cancel`, `Yes` |
| `filePath` | string | no | File path to select in a `JFileChooser` (implies approval) |
| `windowIndex` | number | no | Window index of the dialog (from `list_dialogs`) |

**Notes:**
- Provide either `button` or `filePath`.
- For a `JFileChooser`, `button=Cancel` cancels the selection.
- Dialogs can also be handled generically via `take_snapshot` + `click` on the
  dialog window.
