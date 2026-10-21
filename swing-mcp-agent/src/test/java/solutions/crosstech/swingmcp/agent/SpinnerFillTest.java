package solutions.crosstech.swingmcp.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import solutions.crosstech.swingmcp.common.command.CommandRequest;
import solutions.crosstech.swingmcp.common.command.CommandResponse;
import solutions.crosstech.swingmcp.common.enums.CommandType;
import java.awt.GraphicsEnvironment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.JSpinner;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Issue #22: {@code fill} on a {@link JSpinner} must commit a value of the
 * model's own number class, not always a {@code Double}.
 */
class SpinnerFillTest {

    @Test
    void integerModelGetsInteger() {
        Object value = ComponentScanner.spinnerValue(new SpinnerNumberModel(30, 0, 150, 1), "42");
        assertEquals(42, value);
        assertInstanceOf(Integer.class, value);
    }

    @Test
    void wholeNumberWithFractionZeroIsAcceptedForIntegers() {
        assertEquals(42, ComponentScanner.spinnerValue(new SpinnerNumberModel(30, 0, 150, 1), " 42.0 "));
    }

    @Test
    void otherNumberClassesAreKept() {
        assertEquals(7L, ComponentScanner.spinnerValue(
            new SpinnerNumberModel(Long.valueOf(1), Long.valueOf(0), Long.valueOf(10), Long.valueOf(1)), "7"));
        assertEquals(2.5f, ComponentScanner.spinnerValue(
            new SpinnerNumberModel(Float.valueOf(1), Float.valueOf(0), Float.valueOf(10), Float.valueOf(0.5f)), "2.5"));
        assertEquals(2.5d, ComponentScanner.spinnerValue(new SpinnerNumberModel(1.0, 0.0, 10.0, 0.5), "2.5"));
        assertEquals(new BigDecimal("2.50"), ComponentScanner.spinnerValue(
            new SpinnerNumberModel(BigDecimal.ONE, null, null, BigDecimal.ONE), "2.50"));
    }

    @Test
    void fractionForIntegerModelIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ComponentScanner.spinnerValue(new SpinnerNumberModel(30, 0, 150, 1), "4.5"));
        assertTrue(e.getMessage().contains("Integer"), e.getMessage());
    }

    @Test
    void valueOutsideBoundsIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ComponentScanner.spinnerValue(new SpinnerNumberModel(30, 0, 150, 1), "200"));
        assertTrue(e.getMessage().contains("[0, 150]"), e.getMessage());
    }

    @Test
    void nonNumberIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ComponentScanner.spinnerValue(new SpinnerNumberModel(30, 0, 150, 1), "abc"));
    }

    @Test
    void nonNumberModelsGetTheText() {
        assertEquals("b", ComponentScanner.spinnerValue(new SpinnerListModel(List.of("a", "b")), "b"));
    }

    /** End to end: an application listener casting to Integer must not throw. */
    @Test
    void fillCommandKeepsIntegerModelIntact() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        ObjectMapper mapper = new ObjectMapper();
        CommandHandler handler = new CommandHandler(new JsonCodec());
        AtomicReference<JFrame> frame = new AtomicReference<>();
        AtomicReference<JSpinner> spinner = new AtomicReference<>();
        AtomicReference<Throwable> listenerError = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JSpinner s = new JSpinner(new SpinnerNumberModel(30, 0, 150, 1));
            s.setName("ageSpinner");
            s.addChangeListener(e -> {
                try {
                    int unused = (Integer) s.getValue();
                } catch (Throwable t) {
                    listenerError.set(t);
                }
            });
            JFrame f = new JFrame("Spinner Fill Test");
            f.add(s);
            f.pack();
            f.setVisible(true);
            frame.set(f);
            spinner.set(s);
        });
        try {
            CommandResponse found = call(mapper, handler, CommandType.FIND_COMPONENT,
                Map.of("query", "ageSpinner", "by", "NAME"));
            assertTrue(found.success(), found.error());
            String uid = String.valueOf(((List<?>) found.result()).stream()
                .map(m -> ((Map<?, ?>) m).get("uid")).findFirst().orElseThrow());

            CommandResponse filled = call(mapper, handler, CommandType.FILL, Map.of("uid", uid, "text", "42"));
            assertTrue(filled.success(), filled.error());
            AtomicReference<Object> value = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> value.set(spinner.get().getValue()));
            assertEquals(42, value.get());
            assertEquals(null, listenerError.get(), "change listener failed");

            CommandResponse rejected = call(mapper, handler, CommandType.FILL, Map.of("uid", uid, "text", "999"));
            assertFalse(rejected.success());
            assertTrue(rejected.error().contains("range"), rejected.error());
        } finally {
            SwingUtilities.invokeAndWait(() -> frame.get().dispose());
        }
    }

    private static CommandResponse call(ObjectMapper mapper, CommandHandler handler,
                                        CommandType type, Map<String, Object> params) throws Exception {
        String request = mapper.writeValueAsString(new CommandRequest(UUID.randomUUID().toString(), type, params));
        return mapper.readValue(handler.handle(request), CommandResponse.class);
    }
}
