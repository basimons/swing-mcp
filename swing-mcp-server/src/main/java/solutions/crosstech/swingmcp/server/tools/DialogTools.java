package solutions.crosstech.swingmcp.server.tools;

import solutions.crosstech.swingmcp.server.service.DialogService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for handling modal dialogs of the target application.
 */
@Component
public class DialogTools {

    private final DialogService dialogService;

    public DialogTools(DialogService dialogService) {
        this.dialogService = dialogService;
    }

    @McpTool(name = "list_dialogs",
        annotations = @McpTool.McpAnnotations(title = "List open dialogs", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = """
        List currently open dialogs with their window index, type (option, fileChooser, custom), \
        title, message, modality, and available buttons.""")
    public String listDialogs() {
        return ToolJson.toJson(dialogService.listDialogs());
    }

    @McpTool(name = "handle_dialog",
        annotations = @McpTool.McpAnnotations(title = "Handle dialog", readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false),
        description = """
        Respond to an open dialog: click a named button (e.g. "OK", "Cancel", "Yes") \
        or set a file path in a JFileChooser. Targets the first open dialog unless \
        a window index is given.""")
    public String handleDialog(
            @McpToolParam(description = "Text of the dialog button to click", required = false) String button,
            @McpToolParam(description = "File path to select in a JFileChooser", required = false) String filePath,
            @McpToolParam(description = "Optional window index of the dialog (from list_dialogs)", required = false) Integer windowIndex) {
        return ToolJson.toJson(dialogService.handleDialog(button, filePath, windowIndex));
    }
}
