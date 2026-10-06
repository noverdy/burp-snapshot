package burpss.ui;

import javax.swing.JButton;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

final class AiButton extends JButton {

    private static final Color PURPLE = new Color(0xA371F7), PURPLE_DEEP = new Color(0x7C3AED), PINK = new Color(0xD16BF5);
    private static final int GLOW = 2, ICON = 12, GAP = 7, PAD_X = 11, HEIGHT = 24;

    private boolean hover;

    AiButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(burpss.render.Fonts.sans(12, true));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(2 * GLOW + 2 * PAD_X + ICON + GAP + fm.stringWidth(getText()), HEIGHT + 2 * GLOW);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        boolean dark = isDark();
        boolean pressed = getModel().isPressed();
        double x = GLOW, y = (getHeight() - HEIGHT) / 2.0, w = getWidth() - 2.0 * GLOW, h = HEIGHT, arc = 10;

        g.setColor(new Color(PURPLE.getRed(), PURPLE.getGreen(), PURPLE.getBlue(), hover ? 60 : 28));
        g.setStroke(new BasicStroke(GLOW * 2f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, arc, arc));

        Color base = getParent() != null ? getParent().getBackground() : UIManager.getColor("Panel.background");
        if (base == null) base = dark ? new Color(0x2B2B2B) : Color.WHITE;
        double lift = pressed ? 0.02 : hover ? 0.10 : 0.06;
        g.setColor(mix(base, dark ? Color.WHITE : Color.BLACK, lift));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
        g.setColor(hover ? PINK.darker() : PURPLE_DEEP.brighter());
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, arc, arc));

        FontMetrics fm = g.getFontMetrics(getFont());
        double ix = x + PAD_X, cy = y + h / 2;
        g.setPaint(new GradientPaint((float) ix, (float) (cy - ICON / 2.0), PINK, (float) (ix + ICON), (float) (cy + ICON / 2.0), PURPLE));
        g.fill(sparkle(ix + ICON * 0.42, cy + 1, ICON * 0.5));
        g.fill(sparkle(ix + ICON * 0.88, cy - ICON * 0.36, ICON * 0.22));

        g.setFont(getFont());
        Color fg = UIManager.getColor("Button.foreground");
        g.setColor(fg != null ? fg : dark ? new Color(0xDDDDDD) : new Color(0x222222));
        g.drawString(getText(), (float) (ix + ICON + GAP), (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
        g.dispose();
    }

    private static Path2D sparkle(double cx, double cy, double r) {
        double k = r * 0.22;
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - r);
        p.quadTo(cx + k, cy - k, cx + r, cy);
        p.quadTo(cx + k, cy + k, cx, cy + r);
        p.quadTo(cx - k, cy + k, cx - r, cy);
        p.quadTo(cx - k, cy - k, cx, cy - r);
        p.closePath();
        return p;
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t), (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    private boolean isDark() {
        Color bg = getParent() != null ? getParent().getBackground() : UIManager.getColor("Panel.background");
        return bg != null && (bg.getRed() * 299 + bg.getGreen() * 587 + bg.getBlue() * 114) / 1000 < 128;
    }
}
