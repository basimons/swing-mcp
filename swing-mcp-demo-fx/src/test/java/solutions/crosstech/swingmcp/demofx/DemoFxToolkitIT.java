package solutions.crosstech.swingmcp.demofx;

import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import solutions.crosstech.swingmcp.agent.toolkit.JavaFxToolkit;
import solutions.crosstech.swingmcp.common.dto.ComponentDescriptor;
import solutions.crosstech.swingmcp.common.dto.SnapshotNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real JavaFX demo application through {@link JavaFxToolkit}.
 *
 * <p>This is the test that makes the JavaFX claim credible: it starts a genuine
 * scene graph, then reads and manipulates it through exactly the operations the
 * MCP commands call, with no mocks in between.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoFxToolkitIT {

    private static JavaFxToolkit toolkit;

    @BeforeAll
    static void startUi() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        assertTrue(started.await(20, TimeUnit.SECONDS), "JavaFX toolkit did not start");

        CountDownLatch shown = new CountDownLatch(1);
        Platform.runLater(() -> {
            new DemoFxApp().start(new Stage());
            shown.countDown();
        });
        assertTrue(shown.await(20, TimeUnit.SECONDS), "demo window did not open");
        toolkit = new JavaFxToolkit();
    }

    @AfterAll
    static void stopUi() {
        Platform.exit();
    }

    // ---- helpers ----------------------------------------------------------

    /** Resolves a control's uid the way a client does: find_component by id. */
    private String uidOf(String id) throws Exception {
        List<Map<String, Object>> hits = toolkit.findComponent(Map.of("name", id));
        assertFalse(hits.isEmpty(), "no component with id " + id);
        return String.valueOf(hits.get(0).get("uid"));
    }

    private static void flatten(List<ComponentDescriptor> in, List<ComponentDescriptor> out) {
        if (in == null) {
            return;
        }
        for (ComponentDescriptor d : in) {
            out.add(d);
            flatten(d.children(), out);
        }
    }

    private List<ComponentDescriptor> allNodes(SnapshotNode snapshot) {
        List<ComponentDescriptor> flat = new ArrayList<>();
        flatten(snapshot.components(), flat);
        return flat;
    }

    /** Runs a query on the FX thread and waits for it. */
    private static <T> T fx(Callable<T> work) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                out.set(work.call());
            } catch (Exception e) {
                err.set(e);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS), "FX thread did not respond");
        if (err.get() != null) {
            throw err.get();
        }
        return out.get();
    }

    /** Polls until {@code condition} holds, failing after five seconds. */
    private static void waitUntil(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.call()) {
            assertTrue(System.currentTimeMillis() < deadline, "timed out waiting for: " + what);
            Thread.sleep(50);
        }
    }

    private static ListView<?> logList() throws Exception {
        return fx(() -> (ListView<?>) Window.getWindows().get(0).getScene().lookup("#logList"));
    }

    /** Index of the first visible row of the Canvas tab's log list, or -1 before it has laid out. */
    private static int firstVisibleLogLine() throws Exception {
        ListView<?> list = logList();
        return fx(() -> {
            VirtualFlow<?> flow = (VirtualFlow<?>) list.lookup(".virtual-flow");
            return flow == null || flow.getFirstVisibleCell() == null ? -1 : flow.getFirstVisibleCell().getIndex();
        });
    }

    private void showCanvasTab() throws Exception {
        toolkit.selectOption(Map.of("uid", uidOf("tabs"), "text", "Canvas"));
        waitUntil("logList laid out", () -> firstVisibleLogLine() >= 0);
    }

    private String canvasState() throws Exception {
        return String.valueOf(toolkit.getComponentDetails(Map.of("uid", uidOf("canvasState"))).get("text"));
    }

    // ---- tests ------------------------------------------------------------

    @Test
    @DisplayName("the toolkit reports itself live and owns the fx- uid space")
    void availability() {
        assertTrue(toolkit.isAvailable());
        assertEquals("javafx", toolkit.id());
        assertEquals("fx-", toolkit.uidPrefix());
    }

    @Test
    @DisplayName("snapshot returns the window and its controls")
    void snapshotFindsControls() throws Exception {
        SnapshotNode snapshot = toolkit.takeSnapshot(Map.of());
        assertEquals("Swing MCP JavaFX Demo", snapshot.windowTitle());
        List<ComponentDescriptor> nodes = allNodes(snapshot);
        List<String> ids = nodes.stream().map(ComponentDescriptor::name).filter(java.util.Objects::nonNull).toList();
        assertTrue(ids.contains("nameField"), "expected nameField in " + ids);
        assertTrue(ids.contains("submitButton"), "expected submitButton in " + ids);
        assertTrue(ids.contains("statusLabel"), "expected statusLabel in " + ids);
        assertTrue(nodes.stream().allMatch(d -> d.uid().startsWith("fx-")));
    }

    @Test
    @DisplayName("skin internals are filtered out of the snapshot")
    void snapshotExcludesSkinInternals() throws Exception {
        SnapshotNode snapshot = toolkit.takeSnapshot(Map.of());
        List<ComponentDescriptor> nodes = allNodes(snapshot);
        // LabeledText, and the Path/Text nodes a TextField uses for its caret and
        // selection, are rendering details. A model must never see them.
        List<String> classes = nodes.stream().map(ComponentDescriptor::componentClass).toList();
        assertFalse(classes.contains("LabeledText"), "skin internals leaked: " + classes);
        assertFalse(classes.contains("CheckBoxSkin"), "skin internals leaked: " + classes);
        // A Button must appear as one node, not as a subtree.
        ComponentDescriptor submit = nodes.stream()
            .filter(d -> "submitButton".equals(d.name())).findFirst().orElseThrow();
        assertTrue(submit.children() == null || submit.children().isEmpty(),
            "Button should be a leaf but had " + submit.children());
    }

    @Test
    @DisplayName("a button carries its label as text")
    void controlsExposeTheirValues() throws Exception {
        Map<String, Object> details = toolkit.getComponentDetails(Map.of("uid", uidOf("submitButton")));
        assertEquals("Submit", details.get("text"));
        assertEquals("Button", details.get("componentClass"));
        assertEquals(Boolean.TRUE, details.get("enabled"));
    }

    @Test
    @DisplayName("fill writes into a text field and the value reads back")
    void fillAndReadBack() throws Exception {
        String uid = uidOf("nameField");
        toolkit.fill(Map.of("uid", uid, "text", "Tinus"));
        assertEquals("Tinus", toolkit.getComponentDetails(Map.of("uid", uid)).get("text"));
    }

    @Test
    @DisplayName("filling a spinner commits the value, not just the editor text")
    void fillSpinnerCommitsValue() throws Exception {
        // Regression: the editor showed 42 while the spinner's value stayed 1, so the
        // application's submit handler read the wrong number.
        String uid = uidOf("quantitySpinner");
        toolkit.fill(Map.of("uid", uid, "text", "42"));
        assertEquals("42", toolkit.getComponentDetails(Map.of("uid", uid)).get("text"));
    }

    @Test
    @DisplayName("filling a non-text control fails with a message naming the type")
    void fillRejectsWrongControl() throws Exception {
        String uid = uidOf("submitButton");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> toolkit.fill(Map.of("uid", uid, "text", "x")));
        assertTrue(e.getMessage().contains("Button"), e.getMessage());
    }

    @Test
    @DisplayName("combo box options are listed and selectable by text")
    void selectComboOption() throws Exception {
        String uid = uidOf("countryCombo");
        Map<String, Object> items = toolkit.getListItems(Map.of("uid", uid));
        assertEquals(3, items.get("itemCount"));
        toolkit.selectOption(Map.of("uid", uid, "text", "South Africa"));
        assertEquals("South Africa", toolkit.getComponentDetails(Map.of("uid", uid)).get("text"));
    }

    @Test
    @DisplayName("a check box toggles and reports its state")
    void toggleCheckBox() throws Exception {
        String uid = uidOf("subscribeCheck");
        assertEquals("unselected", toolkit.getComponentDetails(Map.of("uid", uid)).get("selectionState"));
        toolkit.selectOption(Map.of("uid", uid));
        assertEquals("selected", toolkit.getComponentDetails(Map.of("uid", uid)).get("selectionState"));
    }

    @Test
    @DisplayName("clicking submit runs the application's own handler")
    void clickRunsTheHandler() throws Exception {
        toolkit.fill(Map.of("uid", uidOf("nameField"), "text", "Ada"));
        toolkit.selectOption(Map.of("uid", uidOf("countryCombo"), "text", "Netherlands"));
        toolkit.click(Map.of("uid", uidOf("submitButton")));
        String result = String.valueOf(
            toolkit.getComponentDetails(Map.of("uid", uidOf("formResult"))).get("text"));
        assertTrue(result.startsWith("submitted: Ada"), result);
        assertTrue(result.contains("Netherlands"), result);
    }

    @Test
    @DisplayName("table data comes back as rows and columns, not pixels")
    void readTable() throws Exception {
        toolkit.selectOption(Map.of("uid", uidOf("tabs"), "text", "Data"));
        Map<String, Object> data = toolkit.getTableData(Map.of("uid", uidOf("partsTable")));
        assertEquals(List.of("SKU", "Name", "Quantity"), data.get("columns"));
        assertEquals(3, data.get("rowCount"));
        @SuppressWarnings("unchecked")
        List<List<String>> rows = (List<List<String>>) data.get("rows");
        assertEquals("SKU-1", rows.get(0).get(0));
        assertEquals("Bearing", rows.get(0).get(1));
        assertEquals("48", rows.get(1).get(2));
    }

    @Test
    @DisplayName("list items are readable and selectable")
    void readAndSelectList() throws Exception {
        String uid = uidOf("cityList");
        Map<String, Object> items = toolkit.getListItems(Map.of("uid", uid));
        assertEquals(4, items.get("itemCount"));
        toolkit.selectOption(Map.of("uid", uid, "text", "Utrecht"));
        assertEquals("index 2", toolkit.getComponentDetails(Map.of("uid", uid)).get("selectionState"));
    }

    @Test
    @DisplayName("a tree node is selectable by path")
    void selectTreeNodeByPath() throws Exception {
        String uid = uidOf("regionTree");
        toolkit.selectTreeNode(Map.of("uid", uid, "path", "Regions/Europe/Belgium"));
        assertEquals("Belgium", toolkit.getComponentDetails(Map.of("uid", uid)).get("selectionState"));
    }

    @Test
    @DisplayName("an unknown uid is rejected with advice, not a null pointer")
    void unknownUid() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> toolkit.getComponentDetails(Map.of("uid", "fx-999999")));
        assertTrue(e.getMessage().contains("fresh snapshot"), e.getMessage());
    }

    @Test
    @DisplayName("windows are listed with the toolkit that owns them")
    void listWindows() throws Exception {
        List<Map<String, Object>> windows = toolkit.listWindows();
        assertFalse(windows.isEmpty());
        assertEquals("javafx", windows.get(0).get("toolkit"));
        assertEquals("Swing MCP JavaFX Demo", windows.get(0).get("title"));
    }

    @Test
    @DisplayName("a screenshot is produced from the scene graph")
    void screenshot() throws Exception {
        Map<String, Object> shot = toolkit.takeScreenshot(Map.of());
        assertEquals("png", shot.get("format"));
        assertNotNull(shot.get("base64"));
        assertTrue(((Integer) shot.get("width")) > 0);
    }

    @Test
    @DisplayName("waitFor succeeds once the expected text is present")
    void waitForText() throws Exception {
        toolkit.fill(Map.of("uid", uidOf("nameField"), "text", "Grace"));
        toolkit.click(Map.of("uid", uidOf("submitButton")));
        assertTrue(toolkit.waitFor(Map.of("text", "submitted: Grace", "timeoutMs", 3000)).startsWith("Condition met"));
    }

    @Test
    @DisplayName("typeText inserts each character exactly once")
    void typeTextInsertsOnce() throws Exception {
        // Regression: an earlier version dispatched KEY_TYPED *and* appended the
        // character, so "abc" came out as "aabbcc". The control's own handler
        // does the inserting; the toolkit must not.
        String uid = uidOf("nameField");
        toolkit.fill(Map.of("uid", uid, "text", ""));
        toolkit.typeText(Map.of("uid", uid, "text", "abc"));
        assertEquals("abc", toolkit.getComponentDetails(Map.of("uid", uid)).get("text"));
    }

    @Test
    @DisplayName("a modal Alert reports pending, is listed with its real text, and is dismissable")
    void modalDialogRoundTrip() throws Exception {
        // The whole fire-and-poll protocol on JavaFX: the menu action opens an
        // Alert via showAndWait, which nests an event loop and never returns, so
        // the toolkit must come back with a pending result instead of hanging.
        Object result = toolkit.selectMenuItem(Map.of("path", "Help > About"));
        assertTrue(result instanceof Map, "expected a pending map, got " + result);
        @SuppressWarnings("unchecked")
        Map<String, Object> pending = (Map<String, Object>) result;
        assertEquals("pending", pending.get("status"));
        assertEquals(Boolean.TRUE, pending.get("modalDialogOpen"));

        List<Map<String, Object>> dialogs = toolkit.listDialogs();
        assertEquals(1, dialogs.size(), "dialogs: " + dialogs);
        Map<String, Object> about = dialogs.get(0);
        assertEquals("About", about.get("title"));
        assertEquals(Boolean.TRUE, about.get("modal"));
        // The message must be what the user sees, read from the DialogPane, not
        // whichever Label the graph walk happens to hit first.
        assertEquals("Used for testing the swing-mcp server.", about.get("message"));
        assertEquals("Swing MCP JavaFX Demo", about.get("header"));
        assertEquals(List.of("OK"), about.get("buttons"));

        assertEquals("Pressed OK", toolkit.handleDialog(Map.of("button", "OK")));
        assertTrue(toolkit.listDialogs().isEmpty(), "dialog should be gone");
    }

    @Test
    @DisplayName("commands a toolkit cannot serve say so clearly")
    void unsupportedCommandsExplainThemselves() {
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
            () -> toolkit.evaluateJava(Map.of("code", "1+1")));
        assertTrue(e.getMessage().contains("javafx"), e.getMessage());
    }

    @Test
    @DisplayName("mouse_wheel zooms a custom canvas, and modifiers pan it instead")
    void mouseWheelDrivesCustomCanvas() throws Exception {
        showCanvasTab();
        String canvas = uidOf("graphCanvas");
        String before = canvasState();
        assertTrue(before.startsWith("Canvas zoom "), before);
        int zoom = Integer.parseInt(before.replaceAll("Canvas zoom (\\d+)%.*", "$1"));

        String result = toolkit.mouseWheel(Map.of("uid", canvas, "rotation", -2));
        assertTrue(result.contains("2 notch"), result);
        assertTrue(canvasState().startsWith("Canvas zoom " + (zoom + 20) + "%"), canvasState());

        String zoomed = canvasState();
        toolkit.mouseWheel(Map.of("uid", canvas, "rotation", 1, "x", 10, "y", 10, "modifiers", "CTRL"));
        assertTrue(canvasState().startsWith("Canvas zoom " + (zoom + 20) + "%"), "CTRL must pan, not zoom");
        assertFalse(canvasState().equals(zoomed), "CTRL+wheel moved the view: " + canvasState());
    }

    @Test
    @DisplayName("scroll moves a ListView, which has no enclosing ScrollPane")
    void scrollFallsBackToWheelOnListView() throws Exception {
        showCanvasTab();
        ListView<?> list = logList();
        fx(() -> {
            list.scrollTo(0);
            return null;
        });
        waitUntil("logList at the top", () -> firstVisibleLogLine() == 0);
        String result = toolkit.scroll(Map.of("uid", uidOf("logList"), "direction", "DOWN", "amount", 5));
        assertTrue(result.contains("mouse-wheel"), result);
        waitUntil("logList scrolled down", () -> firstVisibleLogLine() > 0);
    }

    @Test
    @DisplayName("mouse_wheel rejects a point outside the component, an unknown modifier and zero notches")
    void mouseWheelValidatesInput() throws Exception {
        showCanvasTab();
        String canvas = uidOf("graphCanvas");
        assertThrows(IllegalArgumentException.class,
            () -> toolkit.mouseWheel(Map.of("uid", canvas, "rotation", 1, "x", 99_999, "y", 1)));
        assertThrows(IllegalArgumentException.class,
            () -> toolkit.mouseWheel(Map.of("uid", canvas, "rotation", 1, "modifiers", "HYPER")));
        assertThrows(IllegalArgumentException.class,
            () -> toolkit.mouseWheel(Map.of("uid", canvas, "rotation", 0)));
    }
}
