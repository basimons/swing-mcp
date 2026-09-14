package solutions.crosstech.swingmcp.server.tools;

import solutions.crosstech.swingmcp.server.service.SnapshotService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for inspecting the target application's component tree.
 */
@Component
public class SnapshotTools {

    private final SnapshotService snapshotService;

    public SnapshotTools(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @McpTool(name = "take_snapshot",
        annotations = @McpTool.McpAnnotations(title = "Take UI snapshot", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = """
        Take a snapshot of the active window's component tree (Swing or JavaFX; both may be present). \
        Each component gets a stable UID used by interaction tools — "comp-42" for Swing, "fx-7" for JavaFX; \
        pass it back exactly as given. In JavaFX a Control is one node; its skin internals are omitted. \
        Always take a fresh snapshot after actions that change the UI.""")
    public String takeSnapshot(
            @McpToolParam(description = "Optional index of the window to snapshot (from list_windows)", required = false) Integer windowIndex,
            @McpToolParam(description = "Optional state filter to reduce snapshot size: ALL (default), VISIBLE_ONLY, ENABLED_ONLY, FOCUSABLE_ONLY", required = false) String filter) {
        return ToolJson.toJson(snapshotService.takeSnapshot(windowIndex, filter));
    }

    @McpTool(name = "get_component_details",
        annotations = @McpTool.McpAnnotations(title = "Get component details", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = """
        Get detailed information about a single component by UID, including \
        accessibility metadata, bounds, text, and selection state.""")
    public String getComponentDetails(
            @McpToolParam(description = "Component UID from a snapshot") String uid) {
        return ToolJson.toJson(snapshotService.componentDetails(uid));
    }

    @McpTool(name = "find_component",
        annotations = @McpTool.McpAnnotations(title = "Find component", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = """
        Find components by text, name, tooltip, or class without taking a full snapshot. \
        Returns matching components with their UIDs. Useful for very large component trees.""")
    public String findComponent(
            @McpToolParam(description = "Search string (case-insensitive substring match)") String query,
            @McpToolParam(description = "Field to search: TEXT, NAME, TOOLTIP, CLASS, or ANY (default)", required = false) String by) {
        return ToolJson.toJson(snapshotService.findComponent(query, by));
    }

    @McpTool(name = "get_table_data",
        annotations = @McpTool.McpAnnotations(title = "Read table", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = """
        Extract the model contents of a JTable as structured data: column names \
        and row values, optionally limited to a row range.""")
    public String getTableData(
            @McpToolParam(description = "JTable component UID") String uid,
            @McpToolParam(description = "Zero-based first row (inclusive)", required = false) Integer startRow,
            @McpToolParam(description = "Zero-based last row (inclusive)", required = false) Integer endRow) {
        return ToolJson.toJson(snapshotService.tableData(uid, startRow, endRow));
    }

    @McpTool(name = "get_list_items",
        annotations = @McpTool.McpAnnotations(title = "Read list items", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = """
        Extract the items of a JList or the visible rows of a JTree as structured \
        data, optionally limited to an index range.""")
    public String getListItems(
            @McpToolParam(description = "JList or JTree component UID") String uid,
            @McpToolParam(description = "Zero-based first item (inclusive)", required = false) Integer startIndex,
            @McpToolParam(description = "Zero-based last item (inclusive)", required = false) Integer endIndex) {
        return ToolJson.toJson(snapshotService.listItems(uid, startIndex, endIndex));
    }
}
