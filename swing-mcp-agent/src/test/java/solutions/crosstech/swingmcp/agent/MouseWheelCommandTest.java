package solutions.crosstech.swingmcp.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import solutions.crosstech.swingmcp.common.command.CommandRequest;
import solutions.crosstech.swingmcp.common.command.CommandResponse;
import solutions.crosstech.swingmcp.common.enums.CommandType;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code MOUSE_WHEEL}, and {@code SCROLL}'s wheel fallback, against a custom
 * scroller in the style of JUNG's {@code GraphZoomScrollPane}: a plain panel
 * that handles the wheel itself and has no {@link JScrollPane} anywhere.
 *
 * <p>Skipped in headless environments (CI runs it under xvfb).</p>
 */
class MouseWheelCommandTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CommandHandler handler = new CommandHandler(new JsonCodec());
    private final List<MouseWheelEvent> canvasEvents = new CopyOnWriteArrayList<>();
    private JFrame frame;
    private JList<String> list;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("Wheel Test Frame");
            JPanel canvas = new JPanel(null);
            canvas.setName("canvas");
            canvas.addMouseWheelListener(canvasEvents::add);
            // A painted-on child with no wheel listener of its own: the wheel must
            // reach the canvas through it, as a physical wheel's would.
            JLabel vertex = new JLabel("vertex");
            vertex.setName("vertex");
            vertex.setBounds(10, 10, 60, 20);
            canvas.add(vertex);

            String[] items = new String[200];
            for (int i = 0; i < items.length; i++) {
                items[i] = "Item " + i;
            }
            list = new JList<>(items);
            list.setName("longList");

            frame.setLayout(new BorderLayout());
            frame.add(canvas, BorderLayout.CENTER);
            frame.add(new JScrollPane(list), BorderLayout.EAST);
            frame.setSize(500, 300);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (frame == null) {
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            for (Window w : Window.getWindows()) {
                w.dispose();
            }
        });
    }

    @Test
    void wheelWithModifiersReachesCustomScrollerAtThePoint() throws Exception {
        String canvas = uidOf("canvas");
        CommandResponse r = call(CommandType.MOUSE_WHEEL,
            Map.of("uid", canvas, "rotation", -2, "x", 150, "y", 100, "modifiers", "CTRL+SHIFT"));
        assertTrue(r.success(), "mouse_wheel failed: " + r.error());

        assertEquals(2, canvasEvents.size(), "one event per notch");
        for (MouseWheelEvent e : canvasEvents) {
            assertEquals(-1, e.getWheelRotation());
            assertEquals(150, e.getX());
            assertEquals(100, e.getY());
            assertTrue((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0, "CTRL held");
            assertTrue((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0, "SHIFT held");
            assertEquals(0, e.getModifiersEx() & InputEvent.ALT_DOWN_MASK, "ALT not held");
        }
    }

    @Test
    void wheelOverAPaintedChildBubblesToTheListeningAncestor() throws Exception {
        String canvas = uidOf("canvas");
        CommandResponse r = call(CommandType.MOUSE_WHEEL, Map.of("uid", canvas, "rotation", 1, "x", 20, "y", 15));
        assertTrue(r.success(), "mouse_wheel failed: " + r.error());
        assertTrue(String.valueOf(r.result()).contains("JLabel"), "delivered to deepest component: " + r.result());
        assertEquals(1, canvasEvents.size());
        assertEquals(1, canvasEvents.get(0).getWheelRotation());
        assertEquals(0, canvasEvents.get(0).getModifiersEx());
    }

    @Test
    void wheelDefaultsToTheCentre() throws Exception {
        String canvas = uidOf("canvas");
        assertTrue(call(CommandType.MOUSE_WHEEL, Map.of("uid", canvas, "rotation", 1)).success());
        MouseWheelEvent e = canvasEvents.get(0);
        int[] size = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            JPanel c = (JPanel) e.getComponent();
            size[0] = c.getWidth();
            size[1] = c.getHeight();
        });
        assertEquals(size[0] / 2, e.getX());
        assertEquals(size[1] / 2, e.getY());
    }

    @Test
    void scrollFallsBackToTheWheelWithoutAScrollPane() throws Exception {
        String canvas = uidOf("canvas");
        CommandResponse r = call(CommandType.SCROLL, Map.of("uid", canvas, "direction", "UP", "amount", 3));
        assertTrue(r.success(), "scroll failed: " + r.error());
        assertEquals(3, canvasEvents.size());
        assertEquals(-1, canvasEvents.get(0).getWheelRotation());

        canvasEvents.clear();
        assertTrue(call(CommandType.SCROLL, Map.of("uid", canvas, "direction", "RIGHT", "amount", 1)).success());
        assertEquals(1, canvasEvents.get(0).getWheelRotation());
        assertTrue(canvasEvents.get(0).isShiftDown(), "horizontal scroll is Shift+wheel");
    }

    @Test
    void wheelOnAListScrollsItsScrollPane() throws Exception {
        String longList = uidOf("longList");
        CommandResponse r = call(CommandType.MOUSE_WHEEL, Map.of("uid", longList, "rotation", 5));
        assertTrue(r.success(), "mouse_wheel failed: " + r.error());
        AtomicInteger first = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> first.set(list.getFirstVisibleIndex()));
        assertTrue(first.get() > 0, "list scrolled down, first visible index " + first.get());
    }

    @Test
    void pointOutsideTheComponentIsRejected() throws Exception {
        CommandResponse r = call(CommandType.MOUSE_WHEEL,
            Map.of("uid", uidOf("canvas"), "rotation", 1, "x", 5000, "y", 5));
        assertFalse(r.success());
        assertTrue(r.error().contains("outside"), r.error());
    }

    @Test
    void unknownModifierIsRejected() throws Exception {
        CommandResponse r = call(CommandType.MOUSE_WHEEL,
            Map.of("uid", uidOf("canvas"), "rotation", 1, "modifiers", "HYPER"));
        assertFalse(r.success());
        assertTrue(r.error().contains("Unknown modifier"), r.error());
        assertTrue(canvasEvents.isEmpty());
    }

    @Test
    void zeroOrTooManyNotchesAreRejected() throws Exception {
        String canvas = uidOf("canvas");
        CommandResponse zero = call(CommandType.MOUSE_WHEEL, Map.of("uid", canvas, "rotation", 0));
        assertFalse(zero.success());
        assertTrue(zero.error().contains("between 1 and 100"), zero.error());
        CommandResponse huge = call(CommandType.MOUSE_WHEEL, Map.of("uid", canvas, "rotation", 1_000_000));
        assertFalse(huge.success());
        assertTrue(canvasEvents.isEmpty());
    }

    @Test
    void disabledComponentIsRejected() throws Exception {
        String canvas = uidOf("canvas");
        SwingUtilities.invokeAndWait(() -> frame.getContentPane().getComponent(0).setEnabled(false));
        CommandResponse r = call(CommandType.MOUSE_WHEEL, Map.of("uid", canvas, "rotation", 1));
        assertFalse(r.success());
        assertTrue(r.error().contains("disabled"), r.error());
        assertTrue(canvasEvents.isEmpty());
    }

    private String uidOf(String name) throws Exception {
        CommandResponse r = call(CommandType.FIND_COMPONENT, Map.of("query", name, "by", "NAME"));
        assertTrue(r.success(), "find_component failed: " + r.error());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) r.result();
        for (Map<String, Object> hit : hits) {
            if (name.equals(hit.get("name"))) {
                return String.valueOf(hit.get("uid"));
            }
        }
        throw new AssertionError("no component named " + name + " in " + hits);
    }

    private CommandResponse call(CommandType type, Map<String, Object> params) throws Exception {
        String request = mapper.writeValueAsString(
            new CommandRequest(UUID.randomUUID().toString(), type, params));
        return mapper.readValue(handler.handle(request), CommandResponse.class);
    }
}
