package burpss.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class SnapshotsTab extends JPanel {

    public record Entry(String id, String title, String detail, long updated, boolean exported) {
    }

    public interface Source {
        List<Entry> entries();

        BufferedImage preview(String id);

        void open(String id);

        void delete(String id);
    }

    private final Source source;
    private final DefaultListModel<Entry> drafts = new DefaultListModel<>();
    private final DefaultListModel<Entry> exported = new DefaultListModel<>();
    private final JList<Entry> draftList = list(drafts);
    private final JList<Entry> exportedList = list(exported);
    private final JTabbedPane lists = new JTabbedPane();
    private final Preview preview = new Preview();

    public SnapshotsTab(Source source) {
        super(new BorderLayout());
        this.source = source;
        lists.addTab("Drafts", new JScrollPane(draftList));
        lists.addTab("Exported", new JScrollPane(exportedList));
        lists.addChangeListener(e -> showSelection());
        lists.putClientProperty("JTabbedPane.trailingComponent", help());

        JButton open = new JButton("Open");
        open.addActionListener(e -> withSelection(source::open));
        JButton delete = new JButton("Delete");
        delete.addActionListener(e -> withSelection(this::confirmDelete));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        buttons.add(open);
        buttons.add(delete);

        JPanel left = new JPanel(new BorderLayout());
        left.add(lists, BorderLayout.CENTER);
        left.add(buttons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, preview);
        split.setResizeWeight(0.3);
        split.setDividerLocation(420);
        add(split, BorderLayout.CENTER);

        addAncestorListener(new AncestorListener() {
            public void ancestorAdded(AncestorEvent e) { refresh(); }
            public void ancestorRemoved(AncestorEvent e) { }
            public void ancestorMoved(AncestorEvent e) { }
        });
    }

    private static JComponent help() {
        JButton help = new JButton("?");
        help.putClientProperty("JButton.buttonType", "help");
        help.setFocusable(false);
        help.setToolTipText("<html><div style='width:260px'>Drafts keep your last 20 unexported snapshots. "
                + "Copying or saving an image moves it to Exported. Everything is stored in the current Burp project.</div></html>");
        JPanel trailing = new JPanel(new java.awt.GridBagLayout());
        trailing.setOpaque(false);
        trailing.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.weightx = 1;
        c.anchor = java.awt.GridBagConstraints.EAST;
        trailing.add(help, c);
        return trailing;
    }

    public void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        String selected = selected() == null ? null : selected().id();
        drafts.clear();
        exported.clear();
        for (Entry e : source.entries()) (e.exported() ? exported : drafts).addElement(e);
        lists.setTitleAt(0, "Drafts (" + drafts.size() + ")");
        lists.setTitleAt(1, "Exported (" + exported.size() + ")");
        reselect(draftList, selected);
        reselect(exportedList, selected);
        showSelection();
    }

    private JList<Entry> list(DefaultListModel<Entry> model) {
        JList<Entry> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(renderer());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelection();
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) withSelection(source::open);
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) withSelection(source::open);
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) withSelection(id -> confirmDelete(id));
            }
        });
        return list;
    }

    private static ListCellRenderer<Entry> renderer() {
        return (list, entry, index, selected, focus) -> {
            JLabel title = new JLabel(entry.title().isBlank() ? entry.detail() : entry.title());
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            JLabel detail = new JLabel((entry.title().isBlank() ? "" : entry.detail() + "  ·  ") + ago(entry.updated()));
            detail.setForeground(selected ? list.getSelectionForeground() : UIManager.getColor("Label.disabledForeground"));
            title.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            JPanel cell = new JPanel(new GridLayout(2, 1));
            cell.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            cell.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            cell.add(title);
            cell.add(detail);
            return cell;
        };
    }

    private static String ago(long millis) {
        Duration d = Duration.between(Instant.ofEpochMilli(millis), Instant.now());
        if (d.toMinutes() < 1) return "just now";
        if (d.toHours() < 1) return d.toMinutes() + " min ago";
        if (d.toDays() < 1) return d.toHours() + " h ago";
        return d.toDays() + " d ago";
    }

    private Entry selected() {
        JList<Entry> list = lists.getSelectedIndex() == 0 ? draftList : exportedList;
        return list.getSelectedValue();
    }

    private void withSelection(java.util.function.Consumer<String> action) {
        Entry entry = selected();
        if (entry != null) action.accept(entry.id());
    }

    private void confirmDelete(String id) {
        if (JOptionPane.showConfirmDialog(this, "Delete this snapshot from the project?", "Delete snapshot",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            source.delete(id);
        }
    }

    private static void reselect(JList<Entry> list, String id) {
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (list.getModel().getElementAt(i).id().equals(id)) {
                list.setSelectedIndex(i);
                return;
            }
        }
        if (list.getModel().getSize() > 0 && list.getSelectedIndex() < 0) list.setSelectedIndex(0);
    }

    private void showSelection() {
        Entry entry = selected();
        preview.show(entry == null ? null : source.preview(entry.id()));
    }

    private static final class Preview extends JComponent {

        private BufferedImage image;

        void show(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setColor(UIManager.getColor("Panel.background"));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (image == null) {
                g.setColor(UIManager.getColor("Label.disabledForeground"));
                String text = "Select a snapshot to preview it";
                g.drawString(text, (getWidth() - g.getFontMetrics().stringWidth(text)) / 2, getHeight() / 2);
                g.dispose();
                return;
            }
            double scale = Math.min(1, Math.min((getWidth() - 40.0) / image.getWidth(), (getHeight() - 40.0) / image.getHeight()));
            int w = (int) (image.getWidth() * scale), h = (int) (image.getHeight() * scale);
            int x = (getWidth() - w) / 2, y = 20;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(new Color(0, 0, 0, 40));
            g.fillRect(x + 2, y + 3, w, h);
            g.drawImage(image, x, y, w, h, null);
            g.dispose();
        }
    }
}
