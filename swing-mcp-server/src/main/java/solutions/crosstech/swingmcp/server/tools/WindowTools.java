package solutions.crosstech.swingmcp.server.tools;

import solutions.crosstech.swingmcp.server.service.WindowService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for window management on the target application.
 */
@Component
public class WindowTools {

    private final WindowService windowService;

    public WindowTools(WindowService windowService) {
        this.windowService = windowService;
    }

    @McpTool(name = "list_windows",
        annotations = @McpTool.McpAnnotations(title = "List windows", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "List all visible windows in the target JVM with their index, title, and bounds.")
    public String listWindows() {
        return ToolJson.toJson(windowService.listWindows());
    }

    @McpTool(name = "select_window",
        annotations = @McpTool.McpAnnotations(title = "Select window", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Select the active window by index (from list_windows) and bring it to front.")
    public String selectWindow(
            @McpToolParam(description = "Window index from list_windows") int index) {
        return ToolJson.toJson(windowService.selectWindow(index));
    }

    @McpTool(name = "resize_window",
        annotations = @McpTool.McpAnnotations(title = "Resize window", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Resize the active window to the given width and height in pixels.")
    public String resizeWindow(
            @McpToolParam(description = "New width in pixels", required = false) Integer width,
            @McpToolParam(description = "New height in pixels", required = false) Integer height) {
        return ToolJson.toJson(windowService.resizeWindow(width, height));
    }

    @McpTool(name = "move_window",
        annotations = @McpTool.McpAnnotations(title = "Move window", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Move the active window to the given screen position in pixels.")
    public String moveWindow(
            @McpToolParam(description = "New x position in pixels", required = false) Integer x,
            @McpToolParam(description = "New y position in pixels", required = false) Integer y) {
        return ToolJson.toJson(windowService.moveWindow(x, y));
    }

    @McpTool(name = "maximize_window",
        annotations = @McpTool.McpAnnotations(title = "Maximize window", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Maximize the active frame window.")
    public String maximizeWindow() {
        return ToolJson.toJson(windowService.setWindowState("MAXIMIZED"));
    }

    @McpTool(name = "minimize_window",
        annotations = @McpTool.McpAnnotations(title = "Minimize window", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Minimize (iconify) the active frame window.")
    public String minimizeWindow() {
        return ToolJson.toJson(windowService.setWindowState("MINIMIZED"));
    }

    @McpTool(name = "restore_window",
        annotations = @McpTool.McpAnnotations(title = "Restore window", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Restore the active frame window to its normal state.")
    public String restoreWindow() {
        return ToolJson.toJson(windowService.setWindowState("NORMAL"));
    }

    @McpTool(name = "close_window",
        annotations = @McpTool.McpAnnotations(title = "Close window", readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false),
        description = "Close the active window by dispatching a window-closing event.")
    public String closeWindow() {
        return ToolJson.toJson(windowService.closeWindow());
    }
}
