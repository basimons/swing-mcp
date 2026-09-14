package solutions.crosstech.swingmcp.server.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the MCP tool annotations.
 *
 * <p>{@code readOnlyHint} is what lets a client skip its per-call approval
 * prompt for a tool, so a read tool that loses the hint becomes annoying and
 * a write tool that gains it becomes dangerous. {@code destructiveHint} marks
 * the tools whose <em>own</em> effect is irreversible. Neither is visible in
 * ordinary tests, so the classification is pinned here.</p>
 */
class ToolAnnotationsTest {

    private static final List<Class<?>> TOOL_CLASSES = List.of(
        ApplicationTools.class, SnapshotTools.class, WindowTools.class, InteractionTools.class,
        DialogTools.class, ClipboardTools.class, ScreenshotTools.class, UtilityTools.class);

    /** Tools that only observe. Every one of these must carry readOnlyHint. */
    private static final Set<String> READ_ONLY = Set.of(
        "take_snapshot", "find_component", "get_component_details", "get_table_data",
        "get_list_items", "list_windows", "list_dialogs", "list_sessions", "app_status",
        "get_clipboard", "take_screenshot", "wait_for");

    /** Tools whose own effect is irreversible, independent of the target application. */
    private static final Set<String> DESTRUCTIVE = Set.of("stop_app", "close_window", "evaluate_java");

    private static Set<String> namesWhere(java.util.function.Predicate<McpTool.McpAnnotations> p) {
        Set<String> out = new TreeSet<>();
        for (Class<?> c : TOOL_CLASSES) {
            for (Method m : c.getDeclaredMethods()) {
                McpTool t = m.getAnnotation(McpTool.class);
                if (t != null && p.test(t.annotations())) {
                    out.add(t.name());
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("every tool is an @McpTool with a title")
    void everyToolHasATitle() {
        int count = 0;
        for (Class<?> c : TOOL_CLASSES) {
            for (Method m : c.getDeclaredMethods()) {
                McpTool t = m.getAnnotation(McpTool.class);
                if (t == null) {
                    continue;
                }
                count++;
                assertFalse(t.annotations().title().isBlank(), t.name() + " has no title");
                assertFalse(t.annotations().openWorldHint(),
                    t.name() + ": this server drives a local application; openWorldHint must be false");
            }
        }
        assertEquals(39, count, "tool count changed — update the docs and this test deliberately");
    }

    @Test
    @DisplayName("exactly the observing tools are marked read-only")
    void readOnlyClassification() {
        assertEquals(READ_ONLY, namesWhere(McpTool.McpAnnotations::readOnlyHint));
    }

    @Test
    @DisplayName("exactly the irreversible tools are marked destructive")
    void destructiveClassification() {
        assertEquals(DESTRUCTIVE, namesWhere(McpTool.McpAnnotations::destructiveHint));
    }

    @Test
    @DisplayName("no tool is both read-only and destructive")
    void hintsAreCoherent() {
        Set<String> both = namesWhere(a -> a.readOnlyHint() && a.destructiveHint());
        assertTrue(both.isEmpty(), "contradictory hints on " + both);
        Set<String> readOnlyNotIdempotent = namesWhere(a -> a.readOnlyHint() && !a.idempotentHint());
        assertTrue(readOnlyNotIdempotent.isEmpty(), "a read must be idempotent: " + readOnlyNotIdempotent);
    }
}
