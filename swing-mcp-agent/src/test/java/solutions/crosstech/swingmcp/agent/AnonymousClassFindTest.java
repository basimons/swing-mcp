package solutions.crosstech.swingmcp.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import solutions.crosstech.swingmcp.common.command.CommandRequest;
import solutions.crosstech.swingmcp.common.command.CommandResponse;
import solutions.crosstech.swingmcp.common.enums.CommandType;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Issue #20: components of anonymous classes must be reported with a usable
 * class name and be findable by class.
 */
class AnonymousClassFindTest {

    /** A named subclass, to check superclass matching beyond anonymous classes. */
    static class WorkspaceButton extends JButton {
    }

    @Test
    void displayNameFallsBackToNamedSuperclass() {
        JTabbedPane anonymous = new JTabbedPane() { };
        assertEquals("", anonymous.getClass().getSimpleName());
        assertEquals("JTabbedPane (anonymous)", ClassNames.displayName(anonymous.getClass()));
        assertEquals("JTabbedPane", ClassNames.displayName(JTabbedPane.class));
        assertEquals("WorkspaceButton", ClassNames.displayName(WorkspaceButton.class));
    }

    @Test
    void anonymousClassMatchesSuperclassAndBinaryName() {
        Class<?> type = new JTabbedPane() { }.getClass();
        assertTrue(ClassNames.matches(type, "TabbedPane", true));
        assertTrue(ClassNames.matches(type, "jtabbedpane", false));
        assertTrue(ClassNames.matches(type, "AnonymousClassFindTest$", true));
        assertTrue(ClassNames.matches(type, type.getName(), false), "fully qualified name");
    }

    @Test
    void namedSubclassMatchesItsSuperclass() {
        assertTrue(ClassNames.matches(WorkspaceButton.class, "JButton", false));
        assertTrue(ClassNames.matches(WorkspaceButton.class, "workspace", true));
    }

    @Test
    void outerClassOfNamedNestedClassDoesNotMatch() {
        assertFalse(ClassNames.matches(WorkspaceButton.class, "AnonymousClassFindTest", true));
    }

    @Test
    void genericBaseClassesAndPackagesDoNotMatch() {
        assertFalse(ClassNames.matches(JLabel.class, "JComponent", true));
        assertFalse(ClassNames.matches(JLabel.class, "Container", true));
        assertFalse(ClassNames.matches(JLabel.class, "swing", true), "package words must not match");
        assertTrue(ClassNames.matches(JLabel.class, "javax.swing.JLabel", false));
    }

    /** End to end: find_component by CLASS and the snapshot descriptor. */
    @Test
    void anonymousTabbedPaneIsFoundAndNamed() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        ObjectMapper mapper = new ObjectMapper();
        CommandHandler handler = new CommandHandler(new JsonCodec());
        AtomicReference<JFrame> frame = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane tabs = new JTabbedPane() {
                @Override
                public String toString() {
                    return "navigation";
                }
            };
            tabs.addTab("Workspace", new JPanel());
            JFrame f = new JFrame("Anonymous Class Test");
            f.add(tabs);
            f.setSize(300, 200);
            f.setVisible(true);
            frame.set(f);
        });
        try {
            CommandResponse found = call(mapper, handler, CommandType.FIND_COMPONENT,
                Map.of("query", "TabbedPane", "by", "CLASS"));
            assertTrue(found.success(), found.error());
            List<?> hits = (List<?>) found.result();
            assertEquals(1, hits.size(), "hits: " + hits);
            Map<?, ?> hit = (Map<?, ?>) hits.get(0);
            assertEquals("JTabbedPane (anonymous)", hit.get("simpleName"));

            CommandResponse snapshot = call(mapper, handler, CommandType.TAKE_SNAPSHOT, Map.of());
            assertTrue(snapshot.success(), snapshot.error());
            String json = mapper.writeValueAsString(snapshot.result());
            assertTrue(json.contains("\"componentClass\":\"JTabbedPane (anonymous)\""), json);
            assertFalse(json.contains("\"componentClass\":\"\""), json);
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
