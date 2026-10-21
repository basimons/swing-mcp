package solutions.crosstech.swingmcp.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import solutions.crosstech.swingmcp.common.command.CommandRequest;
import solutions.crosstech.swingmcp.common.command.CommandResponse;
import solutions.crosstech.swingmcp.common.enums.CommandType;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Issue #19: click, hover and drag reach a point inside a component, hold
 * modifier keys, and are delivered as events rather than through the real mouse.
 */
class PointerInputTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CommandHandler handler = new CommandHandler(new JsonCodec());
    private final List<MouseEvent> canvasEvents = new CopyOnWriteArrayList<>();
    private final AtomicInteger buttonActions = new AtomicInteger();
    private JFrame frame;
    private JPanel holder;
    private JButton inner;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        SwingUtilities.invokeAndWait(() -> {
            JPanel canvas = new JPanel();
            canvas.setName("canvas");
            canvas.setPreferredSize(new Dimension(300, 200));
            MouseAdapter recorder = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { canvasEvents.add(e); }
                @Override public void mouseReleased(MouseEvent e) { canvasEvents.add(e); }
                @Override public void mouseClicked(MouseEvent e) { canvasEvents.add(e); }
                @Override public void mouseDragged(MouseEvent e) { canvasEvents.add(e); }
            };
            canvas.addMouseListener(recorder);
            canvas.addMouseMotionListener(recorder);

            holder = new JPanel(null);
            holder.setName("holder");
            holder.setPreferredSize(new Dimension(300, 80));
            inner = new JButton("Inner");
            inner.setToolTipText("Inner tooltip");
            inner.setBounds(150, 20, 100, 30);
            inner.addActionListener(e -> buttonActions.incrementAndGet());
            holder.add(inner);

            frame = new JFrame("Pointer Input Test");
            frame.add(canvas, BorderLayout.CENTER);
            frame.add(holder, BorderLayout.SOUTH);
            frame.pack();
            frame.setVisible(true);
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window w : Window.getWindows()) {
                w.dispose();
            }
        });
    }

    @Test
    void clickHitsThePointWithModifiers() throws Exception {
        CommandResponse r = call(CommandType.CLICK,
            Map.of("uid", uidOf("canvas"), "x", 10, "y", 20, "modifiers", "CTRL+SHIFT"));
        assertTrue(r.success(), r.error());
        MouseEvent press = awaitEvent(MouseEvent.MOUSE_PRESSED);
        assertEquals(new Point(10, 20), press.getPoint());
        assertTrue(press.isControlDown(), "CTRL held");
        assertTrue(press.isShiftDown(), "SHIFT held");
        assertTrue(SwingUtilities.isLeftMouseButton(press));
        assertEquals(1, awaitEvent(MouseEvent.MOUSE_CLICKED).getClickCount());
    }

    @Test
    void doubleClickReportsClickCountTwo() throws Exception {
        assertTrue(call(CommandType.CLICK, Map.of("uid", uidOf("canvas"), "clickType", "DOUBLE")).success());
        waitUntil("second click", () -> canvasEvents.stream()
            .anyMatch(e -> e.getID() == MouseEvent.MOUSE_CLICKED && e.getClickCount() == 2));
    }

    @Test
    void rightClickIsAPopupTrigger() throws Exception {
        assertTrue(call(CommandType.CLICK, Map.of("uid", uidOf("canvas"), "button", "RIGHT")).success());
        waitUntil("popup trigger", () -> canvasEvents.stream()
            .anyMatch(e -> e.isPopupTrigger() && SwingUtilities.isRightMouseButton(e)));
    }

    @Test
    void clickAtAPointReachesTheChildUnderIt() throws Exception {
        CommandResponse r = call(CommandType.CLICK, Map.of("uid", uidOf("holder"), "x", 200, "y", 35));
        assertTrue(r.success(), r.error());
        assertTrue(String.valueOf(r.result()).contains("JButton"), String.valueOf(r.result()));
        waitUntil("button action", () -> buttonActions.get() == 1);
    }

    @Test
    void pointOutsideTheComponentIsRejected() throws Exception {
        CommandResponse r = call(CommandType.CLICK, Map.of("uid", uidOf("canvas"), "x", 5000, "y", 1));
        assertFalse(r.success());
        assertTrue(r.error().contains("outside"), r.error());
        assertFalse(call(CommandType.CLICK, Map.of("uid", uidOf("canvas"), "modifiers", "HYPER")).success());
    }

    @Test
    void dragWithinAComponentPressesDragsAndReleases() throws Exception {
        String canvas = uidOf("canvas");
        CommandResponse r = call(CommandType.DRAG,
            Map.of("fromUid", canvas, "fromX", 10, "fromY", 10, "toX", 110, "toY", 60));
        assertTrue(r.success(), r.error());
        assertEquals(new Point(10, 10), awaitEvent(MouseEvent.MOUSE_PRESSED).getPoint());
        MouseEvent release = awaitEvent(MouseEvent.MOUSE_RELEASED);
        assertEquals(new Point(110, 60), release.getPoint());
        long drags = canvasEvents.stream().filter(e -> e.getID() == MouseEvent.MOUSE_DRAGGED).count();
        assertTrue(drags >= 5, "expected intermediate drag events, got " + drags);
        assertTrue(canvasEvents.stream().filter(e -> e.getID() == MouseEvent.MOUSE_DRAGGED)
            .allMatch(e -> (e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0), "button held while dragging");
    }

    @Test
    void dragToAnotherComponentReleasesInSourceCoordinates() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("fromUid", uidOf("canvas"));
        params.put("toUid", uidOf("holder"));
        params.put("toX", 0);
        params.put("toY", 0);
        assertTrue(call(CommandType.DRAG, params).success());
        MouseEvent release = awaitEvent(MouseEvent.MOUSE_RELEASED);
        // The holder sits directly below the 200 px high canvas.
        assertEquals(new Point(0, 200), release.getPoint());
    }

    @Test
    void hoverShowsTheTooltip() throws Exception {
        int initialDelay = ToolTipManager.sharedInstance().getInitialDelay();
        ToolTipManager.sharedInstance().setInitialDelay(0);
        try {
            CommandResponse r = call(CommandType.HOVER, Map.of("uid", uidOf("holder"), "x", 200, "y", 35));
            assertTrue(r.success(), r.error());
            waitUntil("tooltip shown", this::tooltipShowing);
        } finally {
            ToolTipManager.sharedInstance().setInitialDelay(initialDelay);
        }
    }

    private boolean tooltipShowing() {
        for (Window w : Window.getWindows()) {
            if (w.isShowing() && containsShowingTooltip(w)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsShowingTooltip(java.awt.Component c) {
        if (c instanceof JToolTip tip && tip.isShowing()) {
            return true;
        }
        if (c instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                if (containsShowingTooltip(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private MouseEvent awaitEvent(int id) throws InterruptedException {
        waitUntil("mouse event " + id, () -> canvasEvents.stream().anyMatch(e -> e.getID() == id));
        return canvasEvents.stream().filter(e -> e.getID() == id).findFirst().orElseThrow();
    }

    private String uidOf(String name) throws Exception {
        CommandResponse found = call(CommandType.FIND_COMPONENT, Map.of("query", name, "by", "NAME"));
        assertTrue(found.success(), found.error());
        return String.valueOf(((Map<?, ?>) ((List<?>) found.result()).get(0)).get("uid"));
    }

    private CommandResponse call(CommandType type, Map<String, Object> params) throws Exception {
        String request = mapper.writeValueAsString(new CommandRequest(UUID.randomUUID().toString(), type, params));
        return mapper.readValue(handler.handle(request), CommandResponse.class);
    }

    private static void waitUntil(String what, java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for: " + what);
    }
}
