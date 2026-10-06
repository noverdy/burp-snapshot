package burpss.ui;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

final class Notice extends JComponent {

    enum Kind {
        SUCCESS(new Color(0x2EA043)), INFO(new Color(0x2F81F7)), ERROR(new Color(0xE5534B));

        final Color color;

        Kind(Color color) {
            this.color = color;
        }
    }

    private static final int HEIGHT = 38, PAD = 14, ICON = 18;
    private static final Color BACKGROUND = new Color(0x1F2328), TEXT = new Color(0xF0F3F6);

    private final String message;
    private final Kind kind;
    private final Font font;
    private float alpha = 1f;
    private Timer life, fade;

    private Notice(String message, Kind kind) {
        this.message = message;
        this.kind = kind;
        this.font = burpss.render.Fonts.sans(13, true);
        setOpaque(false);
    }

    static void show(JLayeredPane layer, Rectangle area, String message, Kind kind) {
        for (java.awt.Component c : layer.getComponentsInLayer(JLayeredPane.POPUP_LAYER)) {
            if (c instanceof Notice old) old.remove();
        }
        Notice notice = new Notice(message, kind);
        FontMetrics fm = notice.getFontMetrics(notice.font);
        int width = Math.min(area.width - 24, PAD * 2 + ICON + 10 + fm.stringWidth(message));
        notice.setBounds(area.x + (area.width - width) / 2, area.y + area.height - HEIGHT - 24, width, HEIGHT);
        layer.add(notice, JLayeredPane.POPUP_LAYER);
        layer.repaint(notice.getBounds());
        notice.life = new Timer(kind == Kind.ERROR ? 4000 : 2200, e -> notice.fadeOut());
        notice.life.setRepeats(false);
        notice.life.start();
    }

    private void fadeOut() {
        fade = new Timer(30, e -> {
            alpha -= 0.12f;
            if (alpha <= 0) remove();
            else repaint();
        });
        fade.start();
    }

    private void remove() {
        if (life != null) life.stop();
        if (fade != null) fade.stop();
        java.awt.Container parent = getParent();
        if (parent == null) return;
        Rectangle bounds = getBounds();
        parent.remove(this);
        parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, alpha)));
        int w = getWidth(), h = getHeight();
        g.setColor(new Color(0, 0, 0, 50));
        g.fill(new RoundRectangle2D.Double(1, 3, w - 2, h - 3, h - 3, h - 3));
        g.setColor(BACKGROUND);
        g.fill(new RoundRectangle2D.Double(0, 0, w, h - 2, h - 2, h - 2));
        g.setColor(kind.color);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 3, h - 3, h - 3));

        double cx = PAD + ICON / 2.0, cy = (h - 2) / 2.0;
        g.fill(new Ellipse2D.Double(cx - ICON / 2.0, cy - ICON / 2.0, ICON, ICON));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D icon = new Path2D.Double();
        switch (kind) {
            case SUCCESS -> {
                icon.moveTo(cx - 4, cy);
                icon.lineTo(cx - 1, cy + 3);
                icon.lineTo(cx + 4.5, cy - 3.5);
            }
            case ERROR -> {
                icon.moveTo(cx - 3.5, cy - 3.5);
                icon.lineTo(cx + 3.5, cy + 3.5);
                icon.moveTo(cx + 3.5, cy - 3.5);
                icon.lineTo(cx - 3.5, cy + 3.5);
            }
            case INFO -> {
                icon.moveTo(cx, cy - 1);
                icon.lineTo(cx, cy + 4);
                g.fill(new Ellipse2D.Double(cx - 1.3, cy - 5.3, 2.6, 2.6));
            }
        }
        g.draw(icon);

        g.setFont(font);
        g.setColor(TEXT);
        FontMetrics fm = g.getFontMetrics();
        String text = message;
        int room = w - PAD * 2 - ICON - 10;
        while (fm.stringWidth(text) > room && text.length() > 2) text = text.substring(0, text.length() - 2) + "…";
        g.drawString(text, PAD + ICON + 10, (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
        g.dispose();
    }

    static Rectangle areaOf(JComponent component, JLayeredPane layer) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), layer);
    }
}
