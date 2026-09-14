package solutions.crosstech.swingmcp.server.tools;

import solutions.crosstech.swingmcp.server.service.ClipboardService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for reading and writing the target JVM's system clipboard.
 */
@Component
public class ClipboardTools {

    private final ClipboardService clipboardService;

    public ClipboardTools(ClipboardService clipboardService) {
        this.clipboardService = clipboardService;
    }

    @McpTool(name = "get_clipboard",
        annotations = @McpTool.McpAnnotations(title = "Read clipboard", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Read the system clipboard of the target JVM as text.")
    public String getClipboard() {
        return ToolJson.toJson(clipboardService.getClipboard());
    }

    @McpTool(name = "set_clipboard",
        annotations = @McpTool.McpAnnotations(title = "Set clipboard", readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = "Write text to the system clipboard of the target JVM.")
    public String setClipboard(
            @McpToolParam(description = "Text to place on the clipboard") String text) {
        return ToolJson.toJson(clipboardService.setClipboard(text));
    }
}
