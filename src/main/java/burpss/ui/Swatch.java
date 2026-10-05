package burpss.ui;

import burpss.core.Mark;

import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

record Swatch(Color color, int size, boolean ring) implements Icon {

    Swatch(Color color) {
        this(color, 12, false);
    }

    static ListCellRenderer<Object> paletteRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                int i = java.util.Arrays.asList(Mark.PALETTE_NAMES).indexOf(String.valueOf(value));
                label.setIcon(i < 0 ? null : new Swatch(Mark.PALETTE[i]));
                return label;
            }
        };
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int inset = size <= 12 ? 0 : ring ? 4 : 3;
        if (ring) {
            g2.setStroke(new java.awt.BasicStroke(2f));
            g2.setColor(color);
            g2.drawOval(x + 1, y + 1, size - 2, size - 2);
        }
        g2.setColor(color);
        g2.fillOval(x + inset, y + inset, size - 2 * inset, size - 2 * inset);
        g2.dispose();
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }
}
