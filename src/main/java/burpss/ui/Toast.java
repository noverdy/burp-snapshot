package burpss.ui;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Window;
import java.awt.image.BufferedImage;

public final class Toast extends JWindow {

    private static Toast current;
    private final Timer close = new Timer(3500, e -> closeUnlessHovered());

    public Toast(Window owner, BufferedImage image, String message, String detail, Runnable onOpen) {
        super(owner);
        JPanel root = new JPanel(new BorderLayout(12, 0));
        Color border = UIManager.getColor("Separator.foreground");
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border == null ? Color.GRAY : border),
                BorderFactory.createEmptyBorder(10, 10, 10, 12)));
        if (image != null) root.add(new JLabel(new ImageIcon(thumbnail(image))), BorderLayout.WEST);

        JLabel title = new JLabel(message);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        JLabel sub = new JLabel(detail);
        sub.putClientProperty("html.disable", Boolean.TRUE);
        Color muted = UIManager.getColor("Label.disabledForeground");
        sub.setForeground(muted == null ? Color.GRAY : muted);
        JPanel text = new JPanel(new GridLayout(0, 1, 0, 2));
        text.setOpaque(false);
        text.add(title);
        text.add(sub);
        root.add(text, BorderLayout.CENTER);

        if (onOpen != null) {
            JButton open = new JButton("Open");
            open.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            open.addActionListener(e -> {
                dispose();
                onOpen.run();
            });
            JPanel holder = new JPanel(new java.awt.GridBagLayout());
            holder.setOpaque(false);
            holder.add(open);
            root.add(holder, BorderLayout.EAST);
        }
        setContentPane(root);
        close.setRepeats(false);
    }

    public void popup() {
        if (current != null) current.dispose();
        current = this;
        pack();
        Window owner = getOwner();
        if (owner != null && owner.isShowing()) {
            Point o = owner.getLocationOnScreen();
            setLocation(o.x + owner.getWidth() - getWidth() - 24, o.y + owner.getHeight() - getHeight() - 24);
        }
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        setVisible(true);
        close.start();
    }

    private void closeUnlessHovered() {
        Point mouse = MouseInfo.getPointerInfo() == null ? null : MouseInfo.getPointerInfo().getLocation();
        if (mouse != null && isShowing() && getBounds().contains(mouse)) {
            close.restart();
            return;
        }
        dispose();
    }

    @Override
    public void dispose() {
        close.stop();
        if (current == this) current = null;
        super.dispose();
    }

    private static Image thumbnail(BufferedImage image) {
        double scale = Math.min(1, Math.min(160.0 / image.getWidth(), 90.0 / image.getHeight()));
        return image.getScaledInstance(Math.max(1, (int) (image.getWidth() * scale)), Math.max(1, (int) (image.getHeight() * scale)),
                Image.SCALE_SMOOTH);
    }
}
