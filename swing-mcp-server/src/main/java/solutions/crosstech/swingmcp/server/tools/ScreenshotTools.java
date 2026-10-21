package solutions.crosstech.swingmcp.server.tools;

import solutions.crosstech.swingmcp.server.service.ScreenshotService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for taking screenshots of the target application.
 */
@Component
public class ScreenshotTools {

    private final ScreenshotService screenshotService;

    public ScreenshotTools(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
    }

    @McpTool(name = "take_screenshot",
        annotations = @McpTool.McpAnnotations(title = "Take screenshot", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = """
        Take a PNG screenshot of the active window, or of a single component if a UID is given. \
        By default the UI is rendered into the image, so overlapping windows never show and \
        background windows work; mode=SCREEN copies the screen region instead. \
        The image is saved to the configured screenshot directory and its path is returned. \
        Set returnImage=true to also receive the base64-encoded PNG data inline.""")
    public String takeScreenshot(
            @McpToolParam(description = "Optional component UID; omit to capture the whole active window", required = false) String uid,
            @McpToolParam(description = "When true, include the base64-encoded PNG data in the response", required = false) Boolean returnImage,
            @McpToolParam(description = "PAINT (default): render the UI, without title bar; SCREEN: copy the screen pixels", required = false) String mode) {
        return ToolJson.toJson(screenshotService.screenshot(uid, returnImage, mode));
    }
}
