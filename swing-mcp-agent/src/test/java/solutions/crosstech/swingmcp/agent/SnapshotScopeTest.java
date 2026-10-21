package solutions.crosstech.swingmcp.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import solutions.crosstech.swingmcp.common.command.CommandRequest;
import solutions.crosstech.swingmcp.common.command.CommandResponse;
import solutions.crosstech.swingmcp.common.enums.CommandType;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Issue #21: snapshots can be limited by depth and node count, can start at a
 * component, report what a depth limit left out, and carry tooltips.
 */
class SnapshotScopeTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CommandHandler handler = new CommandHandler(new JsonCodec());
    private JFrame frame;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        SwingUtilities.invokeAndWait(() -> {
            JPanel outer = new JPanel();
            outer.setName("outer");
            JPanel inner = new JPanel();
            inner.setName("inner");
            JButton save = new JButton();
            save.setName("saveButton");
            save.setToolTipText("<html>Save the <b>file</b></html>");
            JButton same = new JButton("Print");
            same.setName("printButton");
            same.setToolTipText("Print");
            inner.add(save);
            inner.add(same);
            outer.add(inner);
            outer.add(new JLabel("Sibling"));
            frame = new JFrame("Snapshot Scope Test");
            frame.setContentPane(outer);
            frame.setSize(300, 200);
            frame.setVisible(true);
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (frame != null) {
            SwingUtilities.invokeAndWait(() -> frame.dispose());
        }
    }

    @Test
    void subtreeSnapshotStartsAtTheComponent() throws Exception {
        String inner = uidOf("inner");
        JsonNode snapshot = snapshot(Map.of("uid", inner));
        assertEquals("Snapshot Scope Test", snapshot.path("windowTitle").asText());
        JsonNode root = snapshot.path("components").get(0);
        assertEquals("inner", root.path("name").asText());
        String json = snapshot.toString();
        assertTrue(json.contains("saveButton"), json);
        assertFalse(json.contains("Sibling"), "the sibling is outside the subtree: " + json);
        assertFalse(snapshot.has("truncated"), json);
    }

    @Test
    void depthLimitReportsOmittedChildren() throws Exception {
        JsonNode snapshot = snapshot(Map.of("uid", uidOf("outer"), "maxDepth", 1));
        assertTrue(snapshot.path("truncated").asBoolean(), snapshot.toString());
        JsonNode root = snapshot.path("components").get(0);
        JsonNode inner = find(root, "inner");
        assertEquals(2, inner.path("omittedChildren").asInt(), inner.toString());
        assertFalse(inner.has("children"), inner.toString());
        assertFalse(root.has("omittedChildren"), "the root's children were included: " + root);
    }

    @Test
    void nodeLimitTruncates() throws Exception {
        JsonNode snapshot = snapshot(Map.of("uid", uidOf("outer"), "maxNodes", 2));
        assertTrue(snapshot.path("truncated").asBoolean(), snapshot.toString());
    }

    @Test
    void tooltipsAreReportedAsPlainText() throws Exception {
        JsonNode root = snapshot(Map.of("uid", uidOf("inner"))).path("components").get(0);
        assertEquals("Save the file", find(root, "saveButton").path("tooltip").asText());
        assertFalse(find(root, "printButton").has("tooltip"), "a tooltip equal to the text is redundant");

        CommandResponse details = call(CommandType.GET_COMPONENT_DETAILS, Map.of("uid", uidOf("saveButton")));
        assertTrue(details.success(), details.error());
        assertEquals("Save the file", ((Map<?, ?>) details.result()).get("tooltip"));
    }

    private JsonNode find(JsonNode node, String name) {
        if (name.equals(node.path("name").asText())) {
            return node;
        }
        for (JsonNode child : node.path("children")) {
            JsonNode found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private JsonNode snapshot(Map<String, Object> params) throws Exception {
        CommandResponse response = call(CommandType.TAKE_SNAPSHOT, params);
        assertTrue(response.success(), response.error());
        return mapper.valueToTree(response.result());
    }

    private String uidOf(String name) throws Exception {
        CommandResponse found = call(CommandType.FIND_COMPONENT, Map.of("query", name, "by", "NAME"));
        assertTrue(found.success(), found.error());
        for (Object hit : (List<?>) found.result()) {
            if (name.equals(((Map<?, ?>) hit).get("name"))) {
                return String.valueOf(((Map<?, ?>) hit).get("uid"));
            }
        }
        throw new AssertionError("no component named " + name);
    }

    private CommandResponse call(CommandType type, Map<String, Object> params) throws Exception {
        String request = mapper.writeValueAsString(new CommandRequest(UUID.randomUUID().toString(), type, params));
        return mapper.readValue(handler.handle(request), CommandResponse.class);
    }
}
