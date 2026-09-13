package solutions.crosstech.swingmcp.agent.toolkit;

import org.junit.jupiter.api.Test;
import solutions.crosstech.swingmcp.common.dto.SnapshotNode;
import solutions.crosstech.swingmcp.common.spi.UiToolkit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routing rules of {@link ToolkitRegistry}, exercised with stub toolkits so the
 * assertions do not depend on a live UI.
 */
class ToolkitRegistryTest {

    /** Minimal stub: only identity, availability and window list are meaningful. */
    private static final class StubToolkit implements UiToolkit {
        private final String id;
        private final String prefix;
        private final boolean available;
        private final boolean focused;

        StubToolkit(String id, String prefix, boolean available, boolean focused) {
            this.id = id;
            this.prefix = prefix;
            this.available = available;
            this.focused = focused;
        }

        @Override public String id() { return id; }
        @Override public String uidPrefix() { return prefix; }
        @Override public boolean isAvailable() { return available; }
        @Override public boolean isUiThread() { return false; }
        @Override public <T> T invokeOnUiThread(Callable<T> w, long t) throws Exception { return w.call(); }
        @Override public void postToUiThread(Runnable w) { w.run(); }

        @Override
        public List<Map<String, Object>> listWindows() {
            return List.of(Map.of("index", 0, "title", id + " window", "focused", focused));
        }

        private UnsupportedOperationException nope() {
            return new UnsupportedOperationException("stub");
        }

        @Override public SnapshotNode takeSnapshot(Map<String, Object> p) { throw nope(); }
        @Override public Map<String, Object> getComponentDetails(Map<String, Object> p) { throw nope(); }
        @Override public List<Map<String, Object>> findComponent(Map<String, Object> p) { throw nope(); }
        @Override public Map<String, Object> getTableData(Map<String, Object> p) { throw nope(); }
        @Override public Map<String, Object> getListItems(Map<String, Object> p) { throw nope(); }
        @Override public List<Map<String, Object>> listDialogs() { throw nope(); }
        @Override public Map<String, Object> takeScreenshot(Map<String, Object> p) { throw nope(); }
        @Override public Object click(Map<String, Object> p) { throw nope(); }
        @Override public String fill(Map<String, Object> p) { throw nope(); }
        @Override public String typeText(Map<String, Object> p) { throw nope(); }
        @Override public String focus(Map<String, Object> p) { throw nope(); }
        @Override public String hover(Map<String, Object> p) { throw nope(); }
        @Override public String selectOption(Map<String, Object> p) { throw nope(); }
        @Override public String selectTreeNode(Map<String, Object> p) { throw nope(); }
        @Override public String selectTableCell(Map<String, Object> p) { throw nope(); }
        @Override public Object selectMenuItem(Map<String, Object> p) { throw nope(); }
        @Override public Object selectContextMenuItem(Map<String, Object> p) { throw nope(); }
        @Override public Object handleDialog(Map<String, Object> p) { throw nope(); }
        @Override public String pressKey(Map<String, Object> p) { throw nope(); }
        @Override public String drag(Map<String, Object> p) { throw nope(); }
        @Override public String scroll(Map<String, Object> p) { throw nope(); }
        @Override public String waitFor(Map<String, Object> p) { throw nope(); }
        @Override public String selectWindow(Map<String, Object> p) { throw nope(); }
        @Override public String resizeWindow(Map<String, Object> p) { throw nope(); }
        @Override public String moveWindow(Map<String, Object> p) { throw nope(); }
        @Override public String setWindowState(Map<String, Object> p) { throw nope(); }
        @Override public Object closeWindow(Map<String, Object> p) { throw nope(); }
        @Override public Map<String, Object> getClipboard() { throw nope(); }
        @Override public String setClipboard(Map<String, Object> p) { throw nope(); }
        @Override public String evaluateJava(Map<String, Object> p) { throw nope(); }
    }

    private static final StubToolkit SWING = new StubToolkit("swing", "comp-", true, false);
    private static final StubToolkit FX = new StubToolkit("javafx", "fx-", true, true);

    @Test
    void routesUidToTheToolkitThatIssuedIt() {
        ToolkitRegistry registry = new ToolkitRegistry(List.of(SWING, FX));
        assertSame(SWING, registry.forUid("comp-42"));
        assertSame(FX, registry.forUid("fx-7"));
    }

    @Test
    void routesByUidEvenWhenAnotherToolkitIsFocused() {
        // The whole point of prefixing: a command naming a Swing component must
        // reach Swing even while the JavaFX window has focus.
        ToolkitRegistry registry = new ToolkitRegistry(List.of(SWING, FX));
        assertSame(SWING, registry.forParams(Map.of("uid", "comp-1")));
    }

    @Test
    void unknownUidPrefixIsRejectedWithTheValidOnes() {
        ToolkitRegistry registry = new ToolkitRegistry(List.of(SWING, FX));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> registry.forUid("widget-9"));
        assertTrue(e.getMessage().contains("comp-*"), e.getMessage());
        assertTrue(e.getMessage().contains("fx-*"), e.getMessage());
    }

    @Test
    void primaryPrefersTheToolkitWithAFocusedWindow() {
        ToolkitRegistry registry = new ToolkitRegistry(List.of(SWING, FX));
        assertSame(FX, registry.primary());
    }

    @Test
    void primaryFallsBackToTheOnlyLiveToolkit() {
        ToolkitRegistry registry = new ToolkitRegistry(
            List.of(new StubToolkit("swing", "comp-", true, false),
                    new StubToolkit("javafx", "fx-", false, false)));
        assertEquals("swing", registry.primary().id());
    }

    @Test
    void explicitToolkitParameterWins() {
        ToolkitRegistry registry = new ToolkitRegistry(List.of(SWING, FX));
        assertSame(SWING, registry.forParams(Map.of("toolkit", "swing")));
    }

    @Test
    void listAllWindowsTagsEachWindowWithItsToolkit() throws Exception {
        ToolkitRegistry registry = new ToolkitRegistry(List.of(SWING, FX));
        List<Map<String, Object>> windows = registry.listAllWindows();
        assertEquals(2, windows.size());
        assertEquals("swing:0", windows.get(0).get("windowId"));
        assertEquals("javafx:0", windows.get(1).get("windowId"));
        assertEquals("swing", windows.get(0).get("toolkit"));
        assertEquals("javafx", windows.get(1).get("toolkit"));
    }

    @Test
    void javaFxToolkitLoadsReflectivelyWhenJavaFxIsOnTheClasspath() {
        // JavaFX is a provided dependency, so it is present during tests. The
        // point of the assertion is that the reflective path works at all —
        // the absent case cannot be exercised from inside this classpath.
        UiToolkit fx = ToolkitRegistry.loadJavaFx();
        assertNotNull(fx, "expected JavaFxToolkit to load reflectively");
        assertEquals("javafx", fx.id());
        assertEquals("fx-", fx.uidPrefix());
    }

    @Test
    void swingKeepsItsHistoricUidPrefix() {
        // Released clients hold comp-* uids; changing this would break them.
        assertEquals("comp-", new SwingToolkit().uidPrefix());
    }
}
