package solutions.crosstech.swingmcp.server.tools;

import solutions.crosstech.swingmcp.server.service.InteractionService;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for interacting with components of the target application.
 */
@Component
public class InteractionTools {

    private final InteractionService interactionService;

    public InteractionTools(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @McpTool(name = "click",
        annotations = @McpTool.McpAnnotations(title = "Click", readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false),
        description = """
        Click a component by UID. A plain left click on a button is a direct click; anything else \
        is delivered as mouse events at x/y (default: center) to the component under that point, \
        which works while the window is covered or in the background. Use x/y to hit content drawn \
        inside one component (chart points, canvas items) and modifiers for Ctrl/Shift-clicks.""")
    public String click(
            @McpToolParam(description = "Component UID from a snapshot") String uid,
            @McpToolParam(description = "Mouse button: LEFT, RIGHT, or MIDDLE (default LEFT)", required = false) String button,
            @McpToolParam(description = "Click type: SINGLE or DOUBLE (default SINGLE)", required = false) String clickType,
            @McpToolParam(description = "X offset from the component's left edge (default: center)", required = false) Integer x,
            @McpToolParam(description = "Y offset from the component's top edge (default: center)", required = false) Integer y,
            @McpToolParam(description = "Modifier keys held during the click, e.g. CTRL, SHIFT, CTRL+SHIFT, META (Cmd), SHORTCUT (Ctrl, Cmd on macOS)", required = false) String modifiers,
            @McpToolParam(description = "Swing: move the real OS mouse instead (window must be in front and uncovered)", required = false) Boolean robot) {
        return ToolJson.toJson(interactionService.click(uid, button, clickType, x, y, modifiers, robot));
    }

    @McpTool(name = "hover",
        annotations = @McpTool.McpAnnotations(title = "Hover component", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Move the mouse over a component by UID, at x/y (default: center), to trigger hover effects and tooltips.")
    public String hover(
            @McpToolParam(description = "Component UID from a snapshot") String uid,
            @McpToolParam(description = "X offset from the component's left edge (default: center)", required = false) Integer x,
            @McpToolParam(description = "Y offset from the component's top edge (default: center)", required = false) Integer y,
            @McpToolParam(description = "Swing: move the real OS mouse instead of sending events", required = false) Boolean robot) {
        return ToolJson.toJson(interactionService.hover(uid, x, y, robot));
    }

    @McpTool(name = "focus",
        annotations = @McpTool.McpAnnotations(title = "Focus component", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Give keyboard focus to a component by UID so subsequent press_key/type_text calls target it.")
    public String focus(
            @McpToolParam(description = "Component UID from a snapshot") String uid) {
        return ToolJson.toJson(interactionService.focus(uid));
    }

    @McpTool(name = "type_text",
        annotations = @McpTool.McpAnnotations(title = "Type text", readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false),
        description = """
        Type text character-by-character into the focused component using key events \
        (unlike fill, which sets the value directly). Triggers per-keystroke listeners. \
        The events go to the application itself, so it works while the app is in the background.""")
    public String typeText(
            @McpToolParam(description = "Text to type") String text,
            @McpToolParam(description = "Optional component UID to focus before typing", required = false) String uid,
            @McpToolParam(description = "Swing: send OS-level keystrokes via Robot; refused unless the window can be brought to the front", required = false) Boolean robot) {
        return ToolJson.toJson(interactionService.typeText(text, uid, robot));
    }

    @McpTool(name = "select_context_menu_item",
        annotations = @McpTool.McpAnnotations(title = "Select context-menu item", readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false),
        description = """
        Open the context menu of a component and click an item by path, \
        e.g. "Copy" or "Refactor > Rename".""")
    public String selectContextMenuItem(
            @McpToolParam(description = "Component UID from a snapshot") String uid,
            @McpToolParam(description = "Menu item path separated by ' > '") String path) {
        return ToolJson.toJson(interactionService.selectContextMenuItem(uid, path));
    }

    @McpTool(name = "fill",
        annotations = @McpTool.McpAnnotations(title = "Fill text", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Set the text of a text field/area, spinner, or editable combo box by UID.")
    public String fill(
            @McpToolParam(description = "Component UID from a snapshot") String uid,
            @McpToolParam(description = "Text or value to enter") String text) {
        return ToolJson.toJson(interactionService.fill(uid, text));
    }

    @McpTool(name = "select_option",
        annotations = @McpTool.McpAnnotations(title = "Select option", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Select an option in a JList, JComboBox, or JTabbedPane by index or visible text.")
    public String selectOption(
            @McpToolParam(description = "Component UID from a snapshot") String uid,
            @McpToolParam(description = "Zero-based option index", required = false) Integer index,
            @McpToolParam(description = "Visible text of the option to select", required = false) String text) {
        return ToolJson.toJson(interactionService.selectOption(uid, index, text));
    }

    @McpTool(name = "select_tree_node",
        annotations = @McpTool.McpAnnotations(title = "Select tree node", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Select a JTree node by path, e.g. \"Root > Folder > Leaf\".")
    public String selectTreeNode(
            @McpToolParam(description = "JTree component UID") String uid,
            @McpToolParam(description = "Node path separated by ' > '") String path) {
        return ToolJson.toJson(interactionService.selectTreeNode(uid, path));
    }

    @McpTool(name = "select_table_cell",
        annotations = @McpTool.McpAnnotations(title = "Select table cell", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = """
        Select a JTable/TableView cell by zero-based row and column, like a click. \
        For multi-selection use toggle (Ctrl/Cmd-click) or extend (Shift-click), \
        or pass rows to set several selected rows at once.""")
    public String selectTableCell(
            @McpToolParam(description = "JTable (Swing) or TableView (JavaFX) component UID") String uid,
            @McpToolParam(description = "Zero-based row index; omit when passing rows", required = false) Integer row,
            @McpToolParam(description = "Zero-based column index (default 0)", required = false) Integer col,
            @McpToolParam(description = "Add or remove this row from the selection (Ctrl/Cmd-click)", required = false) Boolean toggle,
            @McpToolParam(description = "Select the range from the anchor row to this row (Shift-click)", required = false) Boolean extend,
            @McpToolParam(description = "Zero-based rows that become the whole selection; [] clears it", required = false) List<Integer> rows) {
        return ToolJson.toJson(interactionService.selectTableCell(uid, row, col, toggle, extend, rows));
    }

    @McpTool(name = "select_menu_item",
        annotations = @McpTool.McpAnnotations(title = "Select menu item", readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false),
        description = "Click a menu item by path in the active window, e.g. \"File > Save\".")
    public String selectMenuItem(
            @McpToolParam(description = "Menu path separated by ' > '") String path) {
        return ToolJson.toJson(interactionService.selectMenuItem(path));
    }

    @McpTool(name = "press_key",
        annotations = @McpTool.McpAnnotations(title = "Press key", readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false),
        description = """
        Press a key or key chord in the target application, e.g. "ENTER", "CTRL+S", "F5". \
        Sent to the component with keyboard focus in the application (or uid), even while the app \
        is in the background; the result names the component that received it.""")
    public String pressKey(
            @McpToolParam(description = "Key or chord such as ENTER, TAB, ESCAPE, F5, HOME, PAGE_DOWN, CTRL+S, SHORTCUT+A") String keys,
            @McpToolParam(description = "Optional component UID to focus and send the keys to", required = false) String uid,
            @McpToolParam(description = "Swing: send OS-level keystrokes via Robot; refused unless the window can be brought to the front", required = false) Boolean robot) {
        return ToolJson.toJson(interactionService.pressKey(keys, uid, robot));
    }

    @McpTool(name = "drag",
        annotations = @McpTool.McpAnnotations(title = "Drag", readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false),
        description = """
        Drag with the left button from a point in one component to a point in another, or within one \
        component (slider, divider, chart zoom). Points default to the centers. Delivered as mouse \
        events; native drag-and-drop between components needs robot=true. Swing sessions only.""")
    public String drag(
            @McpToolParam(description = "Source component UID") String fromUid,
            @McpToolParam(description = "Target component UID (default: fromUid)", required = false) String toUid,
            @McpToolParam(description = "X offset in the source component (default: center)", required = false) Integer fromX,
            @McpToolParam(description = "Y offset in the source component (default: center)", required = false) Integer fromY,
            @McpToolParam(description = "X offset in the target component (default: center)", required = false) Integer toX,
            @McpToolParam(description = "Y offset in the target component (default: center)", required = false) Integer toY,
            @McpToolParam(description = "Move the real OS mouse, as native drag-and-drop requires (window must be in front)", required = false) Boolean robot) {
        return ToolJson.toJson(interactionService.drag(fromUid, toUid, fromX, fromY, toX, toY, robot));
    }

    @McpTool(name = "scroll",
        annotations = @McpTool.McpAnnotations(title = "Scroll", readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false),
        description = """
        Scroll a component in a direction by a number of units. Moves the enclosing \
        JScrollPane/ScrollPane; components without one get mouse-wheel notches instead.""")
    public String scroll(
            @McpToolParam(description = "Component UID, usually inside a scroll pane") String uid,
            @McpToolParam(description = "Direction: UP, DOWN, LEFT, or RIGHT (default DOWN)", required = false) String direction,
            @McpToolParam(description = "Number of scroll units (default 3): scroll-bar unit increments (Swing), tenths of the range (JavaFX ScrollPane), or wheel notches without a scroll pane", required = false) Integer amount) {
        return ToolJson.toJson(interactionService.scroll(uid, direction, amount));
    }

    @McpTool(name = "mouse_wheel",
        annotations = @McpTool.McpAnnotations(title = "Mouse wheel", readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false),
        description = """
        Turn the mouse wheel over a component, at x/y (default: center) and with optional \
        modifier keys. One wheel event per notch is delivered to the component under that \
        point, which works while the window is covered or in the background. Use for \
        components that handle the wheel themselves (zoom, pan, custom scrollers), e.g. \
        CTRL+wheel to zoom a graph or chart.""")
    public String mouseWheel(
            @McpToolParam(description = "Component UID from a snapshot") String uid,
            @McpToolParam(description = "Wheel notches, 1-100 either way; positive scrolls down/towards the user, negative up") int rotation,
            @McpToolParam(description = "X offset from the component's left edge (default: center)", required = false) Integer x,
            @McpToolParam(description = "Y offset from the component's top edge (default: center)", required = false) Integer y,
            @McpToolParam(description = "Modifier keys held during the wheel, e.g. CTRL, SHIFT, CTRL+SHIFT, META (Cmd), SHORTCUT (Ctrl, Cmd on macOS)", required = false) String modifiers,
            @McpToolParam(description = "Swing: turn the real OS mouse wheel instead (window must be in front and uncovered)", required = false) Boolean robot) {
        return ToolJson.toJson(interactionService.mouseWheel(uid, rotation, x, y, modifiers, robot));
    }
}
