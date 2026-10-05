package solutions.crosstech.swingmcp.demo;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseWheelEvent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A painted canvas with no scroll pane, in the style of a graph viewer: the
 * mouse wheel zooms, Shift+wheel pans horizontally and Ctrl+wheel pans
 * vertically. Its nodes are painted, not components, so they can only be
 * reached through wheel events and points within the canvas.
 */
public class CanvasPanel extends JPanel {

    private static final int PAN_STEP = 20;
    private static final int[][] NODES = {{80, 60}, {220, 140}, {360, 80}, {160, 260}, {320, 240}};

    private final JLabel statusLabel;
    private int zoomPercent = 100;
    private int offsetX;
    private int offsetY;

    public CanvasPanel(JLabel statusLabel) {
        this.statusLabel = statusLabel;
        setName("graphCanvas");
        setBackground(Color.WHITE);
        addMouseWheelListener(this::onWheel);
    }

    private void onWheel(MouseWheelEvent e) {
        int notches = e.getWheelRotation();
        if (e.isShiftDown()) {
            offsetX -= notches * PAN_STEP;
        } else if (e.isControlDown()) {
            offsetY -= notches * PAN_STEP;
        } else {
            zoomPercent = Math.max(10, Math.min(1000, zoomPercent - notches * 10));
        }
        statusLabel.setText(describe());
        repaint();
    }

    /** Current view, e.g. "Canvas zoom 120%, offset (0,-40)". */
    private String describe() {
        return "Canvas zoom " + zoomPercent + "%, offset (" + offsetX + "," + offsetY + ")";
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(offsetX, offsetY);
            g2.scale(zoomPercent / 100.0, zoomPercent / 100.0);
            g2.setColor(Color.GRAY);
            for (int i = 1; i < NODES.length; i++) {
                g2.drawLine(NODES[0][0], NODES[0][1], NODES[i][0], NODES[i][1]);
            }
            g2.setColor(new Color(0x3366CC));
            for (int[] n : NODES) {
                g2.fillOval(n[0] - 12, n[1] - 12, 24, 24);
            }
        } finally {
            g2.dispose();
        }
    }
}
