package solutions.crosstech.swingmcp.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import solutions.crosstech.swingmcp.common.command.CommandRequest;
import solutions.crosstech.swingmcp.common.command.CommandResponse;
import solutions.crosstech.swingmcp.common.enums.CommandType;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Issue #24: screenshots render the component itself, so overlapping windows
 * do not end up in the image and the image reflects the last action.
 */
class ScreenshotPaintTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CommandHandler handler = new CommandHandler(new JsonCodec());
    private JFrame target;
    private JFrame cover;
    private JPanel visiblePanel;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        SwingUtilities.invokeAndWait(() -> {
            visiblePanel = new JPanel();
            visiblePanel.setName("redPanel");
            visiblePanel.setBackground(Color.RED);
            visiblePanel.setPreferredSize(new Dimension(200, 150));
            JPanel hidden = new JPanel();
            hidden.setName("hiddenPanel");
            hidden.setBackground(Color.ORANGE);
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Visible", visiblePanel);
            tabs.addTab("Hidden", hidden);
            target = new JFrame("Screenshot Target");
            target.add(tabs);
            target.pack();
            target.setLocation(100, 100);
            target.setVisible(true);

            // A second window placed over the first, as a notification or editor would be.
            cover = new JFrame("Covering Window");
            JPanel blue = new JPanel();
            blue.setBackground(Color.BLUE);
            cover.add(blue);
            cover.setBounds(target.getBounds());
            cover.setVisible(true);
            cover.toFront();
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (target != null) {
                target.dispose();
            }
            if (cover != null) {
                cover.dispose();
            }
        });
    }

    @Test
    void paintModeIgnoresCoveringWindows() throws Exception {
        BufferedImage img = screenshot(Map.of("uid", uidOf("redPanel")));
        assertEquals(Color.RED.getRGB(), img.getRGB(img.getWidth() / 2, img.getHeight() / 2));
    }

    @Test
    void imageReflectsChangesStillInTheEventQueue() throws Exception {
        String uid = uidOf("redPanel");
        SwingUtilities.invokeLater(() -> visiblePanel.setBackground(Color.GREEN));
        BufferedImage img = screenshot(Map.of("uid", uid));
        assertEquals(Color.GREEN.getRGB(), img.getRGB(img.getWidth() / 2, img.getHeight() / 2));
    }

    @Test
    void componentsThatAreNotShowingCanBeRendered() throws Exception {
        String uid = uidOf("hiddenPanel");
        BufferedImage img = screenshot(Map.of("uid", uid));
        assertEquals(Color.ORANGE.getRGB(), img.getRGB(img.getWidth() / 2, img.getHeight() / 2));

        CommandResponse screen = call(CommandType.TAKE_SCREENSHOT, Map.of("uid", uid, "mode", "SCREEN"));
        assertFalse(screen.success(), "SCREEN cannot capture what is not on screen");
    }

    @Test
    void windowScreenshotRendersTheContent() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("windowIndex", windowIndexOf("Screenshot Target"));
        BufferedImage img = screenshot(params);
        assertEquals(target.getRootPane().getWidth(), img.getWidth());
        assertEquals(target.getRootPane().getHeight(), img.getHeight());
    }

    @Test
    void screenModeStillWorks() throws Exception {
        CommandResponse response = call(CommandType.TAKE_SCREENSHOT,
            Map.of("uid", uidOf("redPanel"), "mode", "SCREEN"));
        assertTrue(response.success(), response.error());
        assertEquals("SCREEN", ((Map<?, ?>) response.result()).get("mode"));
    }

    @Test
    void unknownModeIsRejected() throws Exception {
        assertFalse(call(CommandType.TAKE_SCREENSHOT, Map.of("mode", "BOTH")).success());
    }

    private BufferedImage screenshot(Map<String, Object> params) throws Exception {
        CommandResponse response = call(CommandType.TAKE_SCREENSHOT, params);
        assertTrue(response.success(), response.error());
        Map<?, ?> result = (Map<?, ?>) response.result();
        assertEquals("PAINT", result.get("mode"));
        byte[] png = Base64.getDecoder().decode((String) result.get("imageBase64"));
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private int windowIndexOf(String title) throws Exception {
        for (Object w : (List<?>) call(CommandType.LIST_WINDOWS, Map.of()).result()) {
            Map<?, ?> window = (Map<?, ?>) w;
            if (title.equals(window.get("title"))) {
                return ((Number) window.get("index")).intValue();
            }
        }
        throw new AssertionError("no window titled " + title);
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
}
