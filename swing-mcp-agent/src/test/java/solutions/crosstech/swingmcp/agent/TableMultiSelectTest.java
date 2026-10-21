package solutions.crosstech.swingmcp.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import solutions.crosstech.swingmcp.common.command.CommandRequest;
import solutions.crosstech.swingmcp.common.command.CommandResponse;
import solutions.crosstech.swingmcp.common.enums.CommandType;
import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Issue #18: {@code select_table_cell} can extend and toggle the selection and
 * select several rows at once.
 */
class TableMultiSelectTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CommandHandler handler = new CommandHandler(new JsonCodec());
    private JFrame frame;
    private JTable table;
    private String uid;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        SwingUtilities.invokeAndWait(() -> {
            Object[][] data = new Object[6][2];
            for (int r = 0; r < data.length; r++) {
                data[r] = new Object[] {r, "Item " + r};
            }
            table = new JTable(data, new Object[] {"ID", "Name"});
            table.setName("itemsTable");
            frame = new JFrame("Table Multi-Select Test");
            frame.add(new JScrollPane(table));
            frame.setSize(300, 250);
            frame.setVisible(true);
        });
        CommandResponse found = call(CommandType.FIND_COMPONENT, Map.of("query", "itemsTable", "by", "NAME"));
        assertTrue(found.success(), found.error());
        uid = String.valueOf(((Map<?, ?>) ((List<?>) found.result()).get(0)).get("uid"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (frame != null) {
            SwingUtilities.invokeAndWait(() -> frame.dispose());
        }
    }

    @Test
    void plainSelectReplacesSelection() throws Exception {
        select(Map.of("row", 1));
        select(Map.of("row", 3));
        assertArrayEquals(new int[] {3}, selectedRows());
    }

    @Test
    void toggleAddsAndRemovesRows() throws Exception {
        select(Map.of("row", 1));
        String result = select(Map.of("row", 3, "toggle", true));
        assertArrayEquals(new int[] {1, 3}, selectedRows());
        assertTrue(result.contains("[1, 3]"), result);
        select(Map.of("row", 1, "toggle", true));
        assertArrayEquals(new int[] {3}, selectedRows());
    }

    @Test
    void extendSelectsRangeFromAnchor() throws Exception {
        select(Map.of("row", 1));
        select(Map.of("row", 4, "extend", true));
        assertArrayEquals(new int[] {1, 2, 3, 4}, selectedRows());
    }

    @Test
    void rowsBecomeTheWholeSelection() throws Exception {
        select(Map.of("row", 5));
        select(Map.of("rows", List.of(0, 2, 4)));
        assertArrayEquals(new int[] {0, 2, 4}, selectedRows());
        select(Map.of("rows", List.of()));
        assertArrayEquals(new int[] {}, selectedRows());
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        assertFalse(call(Map.of("row", 99)).success(), "row out of range");
        assertFalse(call(Map.of("rows", List.of(1), "toggle", true)).success(), "rows with toggle");
        assertFalse(call(Map.of()).success(), "neither row nor rows");
    }

    private String select(Map<String, Object> params) throws Exception {
        CommandResponse response = call(params);
        assertTrue(response.success(), response.error());
        return String.valueOf(response.result());
    }

    private CommandResponse call(Map<String, Object> params) throws Exception {
        Map<String, Object> withUid = new HashMap<>(params);
        withUid.put("uid", uid);
        return call(CommandType.SELECT_TABLE_CELL, withUid);
    }

    private int[] selectedRows() throws Exception {
        AtomicReference<int[]> rows = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> rows.set(table.getSelectedRows()));
        return rows.get();
    }

    private CommandResponse call(CommandType type, Map<String, Object> params) throws Exception {
        String request = mapper.writeValueAsString(new CommandRequest(UUID.randomUUID().toString(), type, params));
        return mapper.readValue(handler.handle(request), CommandResponse.class);
    }
}
