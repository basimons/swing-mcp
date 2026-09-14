package solutions.crosstech.swingmcp.server.tools;

import solutions.crosstech.swingmcp.server.service.EvaluateService;
import solutions.crosstech.swingmcp.server.service.WaitService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP utility tools: waiting for UI conditions and (optionally) evaluating
 * Java snippets in the target JVM.
 */
@Component
public class UtilityTools {

    private final WaitService waitService;
    private final EvaluateService evaluateService;

    public UtilityTools(WaitService waitService, EvaluateService evaluateService) {
        this.waitService = waitService;
        this.evaluateService = evaluateService;
    }

    @McpTool(name = "wait_for",
        annotations = @McpTool.McpAnnotations(title = "Wait for UI condition", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        description = """
        Wait until a UI condition is met or a timeout elapses. Condition types: \
        WINDOW_TITLE, COMPONENT_TEXT, COMPONENT_VISIBLE, COMPONENT_ENABLED, \
        COMPONENT_EXISTS (a component matching a text/name query appears), \
        COMPONENT_GONE (no component matches the query), \
        WINDOW_COUNT (the number of visible windows equals the expected value), \
        EDT_IDLE (the event dispatch queue has drained).""")
    public String waitFor(
            @McpToolParam(description = "Condition type: WINDOW_TITLE, COMPONENT_TEXT, COMPONENT_VISIBLE, COMPONENT_ENABLED, COMPONENT_EXISTS, COMPONENT_GONE, WINDOW_COUNT, or EDT_IDLE") String conditionType,
            @McpToolParam(description = "Component UID (required for COMPONENT_TEXT/VISIBLE/ENABLED conditions)", required = false) String uid,
            @McpToolParam(description = "Expected value: window title, component text, search query, or window count", required = false) String expectedValue,
            @McpToolParam(description = "Timeout in milliseconds (default 5000)", required = false) Long timeoutMs) {
        return ToolJson.toJson(waitService.waitFor(conditionType, uid, expectedValue, timeoutMs));
    }

    @McpTool(name = "evaluate_java",
        annotations = @McpTool.McpAnnotations(title = "Evaluate Java (Swing only)", readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false),
        description = """
        Evaluate a Java snippet inside the target JVM via JShell. Swing sessions only. \
        Disabled unless swing.mcp.evaluate.enabled=true on the server.""")
    public String evaluateJava(
            @McpToolParam(description = "Java code to evaluate") String code) {
        return ToolJson.toJson(evaluateService.evaluate(code));
    }
}
