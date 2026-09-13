package solutions.crosstech.swingmcp.agent.toolkit;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import solutions.crosstech.swingmcp.common.dto.ComponentDescriptor;
import solutions.crosstech.swingmcp.common.dto.SnapshotNode;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaFX implementation of the agent's toolkit interface.
 *
 * <p>Loaded reflectively by {@link ToolkitRegistry} and only when JavaFX is
 * actually present, so this class — and every {@code javafx.*} type it names —
 * is never resolved inside a Swing-only application.</p>
 *
 * <p>Interaction is done through each control's own API ({@code fire()},
 * {@code setText}, selection models) rather than by synthesising native input.
 * That is deliberate: it is deterministic, works when the window is not
 * focused, and cannot be thrown off by DPI scaling or a screensaver. Where a
 * control has no programmatic equivalent, a JavaFX event is dispatched to the
 * node instead, which still runs the application's own handlers.</p>
 */
public class JavaFxToolkit extends AbstractUiToolkit {

    private static final int DEFAULT_MAX_NODES = 2000;
    private static final int DEFAULT_MAX_ROWS = 200;

    private final FxScene scene = new FxScene();
    private volatile Window activeWindow;

    @Override
    public String id() {
        return "javafx";
    }

    @Override
    public String uidPrefix() {
        return FxScene.UID_PREFIX;
    }

    @Override
    public boolean isAvailable() {
        try {
            // Touching Window.getWindows() before the toolkit starts is safe and
            // simply yields an empty list, which is exactly the "not live" answer.
            return Platform.isFxApplicationThread() || !Window.getWindows().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isUiThread() {
        return Platform.isFxApplicationThread();
    }

    @Override
    public void postToUiThread(Runnable work) {
        Platform.runLater(work);
    }

    @Override
    protected String uiThreadName() {
        return "JavaFX Application Thread";
    }

    // ---- window helpers ---------------------------------------------------

    private List<Window> windows() {
        List<Window> out = new ArrayList<>();
        for (Window w : Window.getWindows()) {
            if (w.isShowing()) {
                out.add(w);
            }
        }
        return out;
    }

    /** Target window: an explicit index, else the last-selected, else the focused one. */
    private Window targetWindow(Map<String, Object> params) {
        List<Window> all = windows();
        if (all.isEmpty()) {
            return null;
        }
        Object idx = params == null ? null : params.get("windowIndex");
        if (idx != null) {
            int i = ((Number) idx).intValue();
            if (i < 0 || i >= all.size()) {
                throw new IllegalArgumentException("windowIndex " + i + " out of range; "
                    + all.size() + " window(s) open");
            }
            return all.get(i);
        }
        if (activeWindow != null && activeWindow.isShowing()) {
            return activeWindow;
        }
        for (Window w : all) {
            if (w.isFocused()) {
                return w;
            }
        }
        return all.get(all.size() - 1);
    }

    private static String str(Map<String, Object> p, String key) {
        Object v = p == null ? null : p.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String required(Map<String, Object> p, String key) {
        String v = str(p, key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return v;
    }

    private static int intOr(Map<String, Object> p, String key, int fallback) {
        Object v = p == null ? null : p.get(key);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    private Node nodeParam(Map<String, Object> p) {
        return scene.node(required(p, "uid"));
    }

    // ---- scene inspection -------------------------------------------------

    @Override
    public SnapshotNode takeSnapshot(Map<String, Object> p) throws Exception {
        return onUi(() -> {
            scene.prune();
            Window win = targetWindow(p);
            if (win == null) {
                return new SnapshotNode("No window", "null", 0, 0, 0, 0, List.of());
            }
            boolean visibleOnly = "VISIBLE".equalsIgnoreCase(str(p, "filter"));
            FxScene.Budget budget = new FxScene.Budget(
                Math.max(1, intOr(p, "maxNodes", DEFAULT_MAX_NODES)),
                intOr(p, "maxDepth", Integer.MAX_VALUE));
            Scene sc = FxScene.sceneOf(win);
            List<ComponentDescriptor> children = sc == null || sc.getRoot() == null
                ? List.of()
                : List.of(scene.describe(sc.getRoot(), visibleOnly, budget, 0));
            return new SnapshotNode(
                FxScene.titleOf(win),
                win.getClass().getSimpleName(),
                (int) win.getX(), (int) win.getY(),
                (int) win.getWidth(), (int) win.getHeight(),
                children,
                budget.truncated ? Boolean.TRUE : null);
        });
    }

    @Override
    public List<Map<String, Object>> listWindows() throws Exception {
        return onUi(() -> {
            List<Map<String, Object>> out = new ArrayList<>();
            List<Window> all = windows();
            for (int i = 0; i < all.size(); i++) {
                Window w = all.get(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("index", i);
                m.put("title", FxScene.titleOf(w));
                m.put("windowClass", w.getClass().getSimpleName());
                m.put("toolkit", id());
                m.put("focused", w.isFocused());
                m.put("modal", FxScene.isModal(w));
                m.put("x", (int) w.getX());
                m.put("y", (int) w.getY());
                m.put("width", (int) w.getWidth());
                m.put("height", (int) w.getHeight());
                out.add(m);
            }
            return out;
        });
    }

    @Override
    public Map<String, Object> getComponentDetails(Map<String, Object> p) throws Exception {
        return onUi(() -> {
            String uid = required(p, "uid");
            if (scene.isMenuUid(uid)) {
                MenuItem item = scene.menuItem(uid);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("uid", uid);
                m.put("componentClass", item.getClass().getSimpleName());
                m.put("text", item.getText());
                m.put("enabled", !item.isDisable());
                return m;
            }
            Node n = scene.node(uid);
            Bounds b = n.getBoundsInParent();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", uid);
            m.put("componentClass", n.getClass().getSimpleName());
            m.put("id", n.getId());
            m.put("text", FxScene.textOf(n));
            m.put("enabled", !n.isDisabled());
            m.put("visible", n.isVisible());
            m.put("focused", n.isFocused());
            m.put("focusable", n.isFocusTraversable());
            m.put("selectionState", FxScene.selectionStateOf(n));
            m.put("styleClass", new ArrayList<>(n.getStyleClass()));
            m.put("x", (int) b.getMinX());
            m.put("y", (int) b.getMinY());
            m.put("width", (int) b.getWidth());
            m.put("height", (int) b.getHeight());
            if (n instanceof TextInputControl t) {
                m.put("editable", t.isEditable());
                m.put("promptText", t.getPromptText());
            }
            if (n instanceof ComboBox<?> cb) {
                m.put("itemCount", cb.getItems().size());
                m.put("editable", cb.isEditable());
            }
            return m;
        });
    }

    @Override
    public List<Map<String, Object>> findComponent(Map<String, Object> p) throws Exception {
        return onUi(() -> {
            Window win = targetWindow(p);
            Scene sc = win == null ? null : FxScene.sceneOf(win);
            if (sc == null || sc.getRoot() == null) {
                return List.<Map<String, Object>>of();
            }
            String byId = str(p, "name");
            String byText = str(p, "text");
            String byType = str(p, "componentClass");
            List<Map<String, Object>> hits = new ArrayList<>();
            collectMatches(sc.getRoot(), byId, byText, byType, hits);
            return hits;
        });
    }

    private void collectMatches(Node node, String byId, String byText, String byType,
                                List<Map<String, Object>> hits) {
        String pkg = node.getClass().getPackageName();
        if (pkg.startsWith("javafx.scene.control.skin") || pkg.startsWith("com.sun.")) {
            return;
        }
        boolean match = true;
        if (byId != null) {
            match = byId.equals(node.getId());
        }
        if (match && byText != null) {
            String t = FxScene.textOf(node);
            match = t != null && t.contains(byText);
        }
        if (match && byType != null) {
            match = node.getClass().getSimpleName().equalsIgnoreCase(byType);
        }
        if (match && (byId != null || byText != null || byType != null)) {
            Bounds b = node.getBoundsInParent();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", scene.uidFor(node));
            m.put("componentClass", node.getClass().getSimpleName());
            m.put("id", node.getId());
            m.put("text", FxScene.textOf(node));
            m.put("enabled", !node.isDisabled());
            m.put("visible", node.isVisible());
            m.put("x", (int) b.getMinX());
            m.put("y", (int) b.getMinY());
            m.put("width", (int) b.getWidth());
            m.put("height", (int) b.getHeight());
            hits.add(m);
        }
        // find must reach content that a snapshot does not descend into: a
        // Control is a leaf in the tree view, but the nodes inside a ScrollPane,
        // TabPane or SplitPane are real UI and must still be findable. Uses the
        // same contentOf() definition as the walker so the two cannot drift.
        if (node instanceof javafx.scene.control.Control control) {
            for (Node child : FxScene.contentOf(control)) {
                collectMatches(child, byId, byText, byType, hits);
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectMatches(child, byId, byText, byType, hits);
            }
        }
    }

    @Override
    public Map<String, Object> getTableData(Map<String, Object> p) throws Exception {
        return onUi(() -> {
            Node n = nodeParam(p);
            if (!(n instanceof TableView<?> table)) {
                throw new IllegalArgumentException("Component " + str(p, "uid")
                    + " is a " + n.getClass().getSimpleName() + ", not a TableView");
            }
            return FxScene.tableData(table, intOr(p, "maxRows", DEFAULT_MAX_ROWS));
        });
    }

    @Override
    public Map<String, Object> getListItems(Map<String, Object> p) throws Exception {
        return onUi(() -> {
            Node n = nodeParam(p);
            if (n instanceof ListView<?> list) {
                return FxScene.listItems(list, intOr(p, "maxItems", DEFAULT_MAX_ROWS));
            }
            if (n instanceof ComboBox<?> cb) {
                Map<String, Object> m = new LinkedHashMap<>();
                List<String> items = new ArrayList<>();
                for (Object o : cb.getItems()) {
                    items.add(o == null ? null : String.valueOf(o));
                }
                m.put("items", items);
                m.put("itemCount", items.size());
                m.put("selectedIndex", cb.getSelectionModel().getSelectedIndex());
                return m;
            }
            if (n instanceof ChoiceBox<?> cb) {
                Map<String, Object> m = new LinkedHashMap<>();
                List<String> items = new ArrayList<>();
                for (Object o : cb.getItems()) {
                    items.add(o == null ? null : String.valueOf(o));
                }
                m.put("items", items);
                m.put("itemCount", items.size());
                m.put("selectedIndex", cb.getSelectionModel().getSelectedIndex());
                return m;
            }
            throw new IllegalArgumentException("Component " + str(p, "uid")
                + " is a " + n.getClass().getSimpleName() + ", not a ListView, ComboBox or ChoiceBox");
        });
    }

    @Override
    public List<Map<String, Object>> listDialogs() throws Exception {
        return onUi(this::listDialogsOnUiThread);
    }

    @Override
    protected List<Map<String, Object>> listDialogsOnUiThread() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Window> all = windows();
        for (int i = 0; i < all.size(); i++) {
            Window w = all.get(i);
            if (!(w instanceof Stage stage) || stage.getOwner() == null && !FxScene.isModal(w)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", i);
            m.put("title", FxScene.titleOf(w));
            m.put("modal", FxScene.isModal(w));
            m.put("toolkit", id());
            Scene sc = FxScene.sceneOf(w);
            if (sc != null && sc.getRoot() != null) {
                List<String> buttons = new ArrayList<>();
                collectButtonLabels(sc.getRoot(), buttons);
                m.put("buttons", buttons);
                m.put("message", firstText(sc.getRoot()));
            }
            out.add(m);
        }
        return out;
    }

    private void collectButtonLabels(Node node, List<String> out) {
        if (node instanceof ButtonBase b && b.getText() != null && !b.getText().isBlank()) {
            out.add(b.getText());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectButtonLabels(child, out);
            }
        }
    }

    private String firstText(Node node) {
        if (node instanceof javafx.scene.control.Label l && l.getText() != null && !l.getText().isBlank()) {
            return l.getText();
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                String t = firstText(child);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> takeScreenshot(Map<String, Object> p) throws Exception {
        return onUi(() -> {
            Window win = targetWindow(p);
            Scene sc = win == null ? null : FxScene.sceneOf(win);
            if (sc == null) {
                throw new IllegalStateException("No JavaFX window to capture");
            }
            String uid = str(p, "uid");
            Node target = uid == null ? sc.getRoot() : scene.node(uid);
            WritableImage image = target.snapshot(new SnapshotParameters(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(toBufferedImage(image), "png", out);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("format", "png");
            m.put("width", (int) image.getWidth());
            m.put("height", (int) image.getHeight());
            m.put("base64", Base64.getEncoder().encodeToString(out.toByteArray()));
            return m;
        });
    }

    /**
     * Converts a JavaFX image to an AWT one by reading pixels directly.
     *
     * <p>Deliberately avoids {@code javafx.embed.swing.SwingFXUtils}: that lives
     * in the {@code javafx.swing} module, which a JavaFX application has no
     * reason to ship. Depending on it would make screenshots fail on exactly the
     * pure-JavaFX applications this toolkit exists to serve.</p>
     */
    private static java.awt.image.BufferedImage toBufferedImage(WritableImage image) {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        java.awt.image.BufferedImage out =
            new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javafx.scene.image.PixelReader reader = image.getPixelReader();
        if (reader == null) {
            throw new IllegalStateException("Snapshot produced no readable pixels");
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return out;
    }

    // ---- interaction ------------------------------------------------------

    @Override
    public Object click(Map<String, Object> p) throws Exception {
        String uid = required(p, "uid");
        return action(() -> {
            if (scene.isMenuUid(uid)) {
                scene.menuItem(uid).fire();
                return "Fired menu item " + uid;
            }
            Node n = scene.node(uid);
            if (n.isDisabled()) {
                throw new IllegalStateException("Component " + uid + " is disabled");
            }
            if (n instanceof ButtonBase b) {
                b.fire();
                return "Clicked " + uid;
            }
            dispatchClick(n);
            return "Dispatched click to " + uid;
        });
    }

    /** Synthesises a press/release/click sequence on a node that has no fire(). */
    private void dispatchClick(Node n) {
        Bounds b = n.getBoundsInLocal();
        double x = b.getWidth() / 2;
        double y = b.getHeight() / 2;
        for (javafx.event.EventType<MouseEvent> type :
            List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
            Event.fireEvent(n, new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, true, null));
        }
    }

    @Override
    public String fill(Map<String, Object> p) throws Exception {
        String text = str(p, "text") == null ? "" : str(p, "text");
        return onUi(() -> {
            Node n = nodeParam(p);
            if (n instanceof TextInputControl t) {
                if (!t.isEditable()) {
                    throw new IllegalStateException("Component is not editable");
                }
                t.setText(text);
                t.positionCaret(text.length());
                return "Filled " + n.getId() + " with " + text.length() + " character(s)";
            }
            if (n instanceof ComboBox<?> cb && cb.isEditable()) {
                cb.getEditor().setText(text);
                return "Filled editable combo box";
            }
            if (n instanceof Spinner<?> sp && sp.getEditor() != null) {
                sp.getEditor().setText(text);
                return "Filled spinner editor";
            }
            throw new IllegalArgumentException("Component " + str(p, "uid") + " is a "
                + n.getClass().getSimpleName() + " and cannot be filled with text");
        });
    }

    /**
     * Types character by character so the application's own key handlers and
     * validators fire, which {@link #fill} deliberately bypasses.
     */
    @Override
    public String typeText(Map<String, Object> p) throws Exception {
        String text = required(p, "text");
        return onUi(() -> {
            Node n = nodeParam(p);
            n.requestFocus();
            for (char c : text.toCharArray()) {
                String s = String.valueOf(c);
                Event.fireEvent(n, new KeyEvent(KeyEvent.KEY_TYPED, s, s, KeyCode.UNDEFINED,
                    false, false, false, false));
                if (n instanceof TextInputControl t) {
                    t.appendText(s);
                }
            }
            return "Typed " + text.length() + " character(s)";
        });
    }

    @Override
    public String focus(Map<String, Object> p) throws Exception {
        return onUi(() -> {
            Node n = nodeParam(p);
            n.requestFocus();
            return "Focused " + str(p, "uid");
        });
    }

    @Override
    public String hover(Map<String, Object> p) throws Exception {
        return onUi(() -> {
            Node n = nodeParam(p);
            Bounds b = n.getBoundsInLocal();
            Event.fireEvent(n, new MouseEvent(MouseEvent.MOUSE_ENTERED,
                b.getWidth() / 2, b.getHeight() / 2, 0, 0, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null));
            return "Hovered " + str(p, "uid");
        });
    }

    @Override
    public String selectOption(Map<String, Object> p) throws Exception {
        String text = str(p, "text");
        Integer index = p.get("index") instanceof Number n ? n.intValue() : null;
        return onUi(() -> {
            Node n = nodeParam(p);
            if (n instanceof ComboBox<?> cb) {
                return select(cb.getItems(), cb.getSelectionModel(), text, index, "combo box");
            }
            if (n instanceof ChoiceBox<?> cb) {
                return select(cb.getItems(), cb.getSelectionModel(), text, index, "choice box");
            }
            if (n instanceof ListView<?> lv) {
                return select(lv.getItems(), lv.getSelectionModel(), text, index, "list");
            }
            if (n instanceof TabPane tp) {
                for (int i = 0; i < tp.getTabs().size(); i++) {
                    if (text != null && text.equals(tp.getTabs().get(i).getText())) {
                        tp.getSelectionModel().select(i);
                        return "Selected tab " + text;
                    }
                }
                if (index != null) {
                    tp.getSelectionModel().select(index);
                    return "Selected tab " + index;
                }
                throw new IllegalArgumentException("No tab matching " + text);
            }
            if (n instanceof CheckBox cb) {
                cb.setSelected(!cb.isSelected());
                return "Toggled check box to " + cb.isSelected();
            }
            if (n instanceof ToggleButton tb) {
                tb.setSelected(!tb.isSelected());
                return "Toggled to " + tb.isSelected();
            }
            throw new IllegalArgumentException("Component " + str(p, "uid") + " is a "
                + n.getClass().getSimpleName() + " and has no selectable options");
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String select(List<?> items, javafx.scene.control.SelectionModel model,
                          String text, Integer index, String what) {
        if (index != null) {
            if (index < 0 || index >= items.size()) {
                throw new IllegalArgumentException("index " + index + " out of range; "
                    + items.size() + " item(s)");
            }
            model.select(index);
            return "Selected " + what + " item " + index;
        }
        if (text == null) {
            throw new IllegalArgumentException("Provide either text or index");
        }
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item != null && text.equals(String.valueOf(item))) {
                model.select(i);
                return "Selected " + what + " item " + text;
            }
        }
        throw new IllegalArgumentException("No " + what + " item matching " + text);
    }

    @Override
    public String selectTreeNode(Map<String, Object> p) throws Exception {
        String path = required(p, "path");
        return onUi(() -> {
            Node n = nodeParam(p);
            if (!(n instanceof TreeView<?> tree)) {
                throw new IllegalArgumentException("Component is not a TreeView");
            }
            String[] parts = path.split("/");
            TreeItem<?> current = tree.getRoot();
            if (current == null) {
                throw new IllegalStateException("Tree has no root");
            }
            int start = parts.length > 0 && String.valueOf(current.getValue()).equals(parts[0]) ? 1 : 0;
            for (int i = start; i < parts.length; i++) {
                current.setExpanded(true);
                TreeItem<?> next = null;
                for (TreeItem<?> child : current.getChildren()) {
                    if (String.valueOf(child.getValue()).equals(parts[i])) {
                        next = child;
                        break;
                    }
                }
                if (next == null) {
                    throw new IllegalArgumentException("No tree node named " + parts[i]
                        + " under " + current.getValue());
                }
                current = next;
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            javafx.scene.control.SelectionModel model = tree.getSelectionModel();
            model.select(tree.getRow((TreeItem) current));
            return "Selected tree node " + path;
        });
    }

    @Override
    public String selectTableCell(Map<String, Object> p) throws Exception {
        int row = intOr(p, "row", -1);
        int col = intOr(p, "col", 0);
        return onUi(() -> {
            Node n = nodeParam(p);
            if (!(n instanceof TableView<?> table)) {
                throw new IllegalArgumentException("Component is not a TableView");
            }
            if (row < 0 || row >= table.getItems().size()) {
                throw new IllegalArgumentException("row " + row + " out of range; "
                    + table.getItems().size() + " row(s)");
            }
            if (col < 0 || col >= table.getColumns().size()) {
                throw new IllegalArgumentException("col " + col + " out of range; "
                    + table.getColumns().size() + " column(s)");
            }
            table.getSelectionModel().setCellSelectionEnabled(true);
            @SuppressWarnings({"unchecked", "rawtypes"})
            javafx.scene.control.TableColumn column = table.getColumns().get(col);
            @SuppressWarnings({"unchecked", "rawtypes"})
            javafx.scene.control.TableView.TableViewSelectionModel model = table.getSelectionModel();
            model.clearAndSelect(row, column);
            return "Selected cell (" + row + ", " + col + ")";
        });
    }

    @Override
    public Object selectMenuItem(Map<String, Object> p) throws Exception {
        String path = required(p, "path");
        return action(() -> {
            String[] parts = path.split("\\s*>\\s*");
            Window win = targetWindow(p);
            Scene sc = win == null ? null : FxScene.sceneOf(win);
            if (sc == null) {
                throw new IllegalStateException("No JavaFX window");
            }
            javafx.scene.control.MenuBar bar = findMenuBar(sc.getRoot());
            if (bar == null) {
                throw new IllegalStateException("No MenuBar in the active window");
            }
            List<? extends MenuItem> level = bar.getMenus();
            MenuItem found = null;
            for (String part : parts) {
                found = null;
                for (MenuItem item : level) {
                    if (part.equals(item.getText())) {
                        found = item;
                        break;
                    }
                }
                if (found == null) {
                    throw new IllegalArgumentException("No menu entry named " + part + " in " + path);
                }
                if (found instanceof javafx.scene.control.Menu m) {
                    level = m.getItems();
                }
            }
            found.fire();
            return "Selected menu item " + path;
        });
    }

    private javafx.scene.control.MenuBar findMenuBar(Node node) {
        if (node instanceof javafx.scene.control.MenuBar bar) {
            return bar;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                javafx.scene.control.MenuBar bar = findMenuBar(child);
                if (bar != null) {
                    return bar;
                }
            }
        }
        return null;
    }

    @Override
    public Object selectContextMenuItem(Map<String, Object> p) throws Exception {
        String text = required(p, "text");
        return action(() -> {
            Node n = nodeParam(p);
            javafx.scene.control.ContextMenu menu = contextMenuOf(n);
            if (menu == null) {
                throw new IllegalStateException("Component has no context menu");
            }
            for (MenuItem item : menu.getItems()) {
                if (text.equals(item.getText())) {
                    item.fire();
                    return "Selected context menu item " + text;
                }
            }
            throw new IllegalArgumentException("No context menu item named " + text);
        });
    }

    private javafx.scene.control.ContextMenu contextMenuOf(Node n) {
        if (n instanceof javafx.scene.control.Control c && c.getContextMenu() != null) {
            return c.getContextMenu();
        }
        return null;
    }

    @Override
    public Object handleDialog(Map<String, Object> p) throws Exception {
        String button = str(p, "button");
        return action(() -> {
            for (Window w : windows()) {
                if (!FxScene.isModal(w)) {
                    continue;
                }
                Scene sc = FxScene.sceneOf(w);
                if (sc == null) {
                    continue;
                }
                if (button == null) {
                    ((Stage) w).close();
                    return "Closed dialog";
                }
                ButtonBase target = findButton(sc.getRoot(), button);
                if (target != null) {
                    target.fire();
                    return "Pressed " + button;
                }
            }
            throw new IllegalStateException(button == null
                ? "No modal dialog is open"
                : "No modal dialog with a button labelled " + button);
        });
    }

    private ButtonBase findButton(Node node, String label) {
        if (node instanceof ButtonBase b && label.equalsIgnoreCase(b.getText())) {
            return b;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                ButtonBase b = findButton(child, label);
                if (b != null) {
                    return b;
                }
            }
        }
        return null;
    }

    @Override
    public String pressKey(Map<String, Object> p) throws Exception {
        String key = required(p, "key");
        return onUi(() -> {
            KeyCode code = KeyCode.valueOf(key.toUpperCase().replace(' ', '_'));
            Window win = targetWindow(p);
            Scene sc = win == null ? null : FxScene.sceneOf(win);
            if (sc == null) {
                throw new IllegalStateException("No JavaFX window");
            }
            Node target = sc.getFocusOwner() != null ? sc.getFocusOwner() : sc.getRoot();
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false));
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code,
                false, false, false, false));
            return "Pressed " + key;
        });
    }

    @Override
    public String drag(Map<String, Object> p) {
        throw unsupported("drag", "JavaFX drag-and-drop is application-defined; "
            + "use the control's own API, or click the source and target in sequence");
    }

    @Override
    public String scroll(Map<String, Object> p) throws Exception {
        String direction = str(p, "direction") == null ? "DOWN" : str(p, "direction").toUpperCase();
        double amount = intOr(p, "amount", 1) * 0.1;
        return onUi(() -> {
            Node n = nodeParam(p);
            javafx.scene.control.ScrollPane pane = n instanceof javafx.scene.control.ScrollPane sp
                ? sp : enclosingScrollPane(n);
            if (pane == null) {
                throw new IllegalArgumentException("Component " + str(p, "uid")
                    + " is not inside a ScrollPane");
            }
            switch (direction) {
                case "UP" -> pane.setVvalue(clamp(pane.getVvalue() - amount));
                case "DOWN" -> pane.setVvalue(clamp(pane.getVvalue() + amount));
                case "LEFT" -> pane.setHvalue(clamp(pane.getHvalue() - amount));
                case "RIGHT" -> pane.setHvalue(clamp(pane.getHvalue() + amount));
                default -> throw new IllegalArgumentException("Unknown direction: " + direction);
            }
            return "Scrolled " + direction;
        });
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private javafx.scene.control.ScrollPane enclosingScrollPane(Node n) {
        for (Node cur = n.getParent(); cur != null; cur = cur.getParent()) {
            if (cur instanceof javafx.scene.control.ScrollPane sp) {
                return sp;
            }
        }
        return null;
    }

    @Override
    public String waitFor(Map<String, Object> p) throws Exception {
        long timeoutMs = intOr(p, "timeoutMs", 5000);
        String text = str(p, "text");
        String nodeId = str(p, "name");
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Boolean hit = onUi(() -> {
                Window win = targetWindow(p);
                Scene sc = win == null ? null : FxScene.sceneOf(win);
                if (sc == null || sc.getRoot() == null) {
                    return Boolean.FALSE;
                }
                return matchExists(sc.getRoot(), nodeId, text);
            });
            if (Boolean.TRUE.equals(hit)) {
                return "Condition met";
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Timed out after " + timeoutMs
            + " ms waiting for " + (nodeId != null ? "id=" + nodeId : "text=" + text));
    }

    private Boolean matchExists(Node node, String nodeId, String text) {
        if (nodeId != null && nodeId.equals(node.getId())) {
            return Boolean.TRUE;
        }
        if (text != null) {
            String t = FxScene.textOf(node);
            if (t != null && t.contains(text)) {
                return Boolean.TRUE;
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (Boolean.TRUE.equals(matchExists(child, nodeId, text))) {
                    return Boolean.TRUE;
                }
            }
        }
        return Boolean.FALSE;
    }

    // ---- windows ----------------------------------------------------------

    @Override
    public String selectWindow(Map<String, Object> p) throws Exception {
        return onUi(() -> {
            Window w = targetWindow(p);
            if (w == null) {
                throw new IllegalStateException("No JavaFX window");
            }
            activeWindow = w;
            if (w instanceof Stage s) {
                s.requestFocus();
            }
            return "Selected window " + FxScene.titleOf(w);
        });
    }

    @Override
    public String resizeWindow(Map<String, Object> p) throws Exception {
        int width = intOr(p, "width", -1);
        int height = intOr(p, "height", -1);
        return onUi(() -> {
            Window w = targetWindow(p);
            if (width > 0) {
                w.setWidth(width);
            }
            if (height > 0) {
                w.setHeight(height);
            }
            return "Resized to " + (int) w.getWidth() + "x" + (int) w.getHeight();
        });
    }

    @Override
    public String moveWindow(Map<String, Object> p) throws Exception {
        int x = intOr(p, "x", Integer.MIN_VALUE);
        int y = intOr(p, "y", Integer.MIN_VALUE);
        return onUi(() -> {
            Window w = targetWindow(p);
            if (x != Integer.MIN_VALUE) {
                w.setX(x);
            }
            if (y != Integer.MIN_VALUE) {
                w.setY(y);
            }
            return "Moved to " + (int) w.getX() + "," + (int) w.getY();
        });
    }

    @Override
    public String setWindowState(Map<String, Object> p) throws Exception {
        String state = required(p, "state").toUpperCase();
        return onUi(() -> {
            Window w = targetWindow(p);
            if (!(w instanceof Stage s)) {
                throw new IllegalStateException("Window is not a Stage and has no state");
            }
            switch (state) {
                case "MAXIMIZED" -> s.setMaximized(true);
                case "MINIMIZED", "ICONIFIED" -> s.setIconified(true);
                case "NORMAL", "RESTORED" -> {
                    s.setIconified(false);
                    s.setMaximized(false);
                }
                case "FULLSCREEN" -> s.setFullScreen(true);
                default -> throw new IllegalArgumentException("Unknown window state: " + state);
            }
            return "Window state set to " + state;
        });
    }

    @Override
    public Object closeWindow(Map<String, Object> p) throws Exception {
        return action(() -> {
            Window w = targetWindow(p);
            if (w instanceof Stage s) {
                s.close();
                return "Closed " + FxScene.titleOf(w);
            }
            w.hide();
            return "Hid " + FxScene.titleOf(w);
        });
    }

    // ---- misc -------------------------------------------------------------

    @Override
    public Map<String, Object> getClipboard() throws Exception {
        return onUi(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", Clipboard.getSystemClipboard().getString());
            return m;
        });
    }

    @Override
    public String setClipboard(Map<String, Object> p) throws Exception {
        String text = required(p, "text");
        return onUi(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
            return "Clipboard set (" + text.length() + " characters)";
        });
    }

    @Override
    public String evaluateJava(Map<String, Object> p) {
        throw unsupported("evaluate_java", "arbitrary evaluation is only wired for Swing targets");
    }
}
