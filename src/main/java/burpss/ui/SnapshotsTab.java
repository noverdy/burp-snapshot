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
import javax.swing.JToggleButton;
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
    private static final Color ACCENT = new Color(0xFF6633);
    private final java.awt.CardLayout cards = new java.awt.CardLayout();
    private final JPanel listCards = new JPanel(cards);
    private final JToggleButton draftsTab = new JToggleButton("Drafts");
    private final JToggleButton exportedTab = new JToggleButton("Exported");
    private final Preview preview = new Preview();

    public SnapshotsTab(Source source) {
        super(new BorderLayout());
        this.source = source;
        listCards.add(new JScrollPane(draftList), "drafts");
        listCards.add(new JScrollPane(exportedList), "exported");

        JButton open = new JButton("Open");
        open.addActionListener(e -> withSelection(source::open));
        JButton delete = new JButton("Delete");
        delete.addActionListener(e -> withSelection(this::confirmDelete));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        buttons.add(open);
        buttons.add(delete);

        JPanel left = new JPanel(new BorderLayout());
        left.add(header(), BorderLayout.NORTH);
        left.add(listCards, BorderLayout.CENTER);
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

    private JComponent header() {
        JPanel tabs = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        tabs.setOpaque(false);
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        for (JToggleButton tab : List.of(draftsTab, exportedTab)) {
            tab.setFocusPainted(false);
            tab.setContentAreaFilled(false);
            tab.addItemListener(e -> {
                styleTab(tab);
                cards.show(listCards, tab == draftsTab ? "drafts" : "exported");
                showSelection();
            });
            styleTab(tab);
            group.add(tab);
            tabs.add(tab);
        }
        draftsTab.setSelected(true);
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")));
        header.add(tabs, BorderLayout.WEST);
        header.add(new HelpMark("Drafts: last 20 unexported snapshots. Exported: copied or saved at least once. Stored in this Burp project."),
                BorderLayout.EAST);
        return header;
    }

    private static void styleTab(JToggleButton tab) {
        boolean on = tab.isSelected();
        tab.setFont(tab.getFont().deriveFont(on ? Font.BOLD : Font.PLAIN));
        tab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, on ? ACCENT : new Color(0, 0, 0, 0)),
                BorderFactory.createEmptyBorder(8, 14, 6, 14)));
    }

    private static final class HelpMark extends JComponent {

        HelpMark(String tooltip) {
            setToolTipText(tooltip);
            setPreferredSize(new java.awt.Dimension(40, 30));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int d = 18, x = getWidth() - d - 12, y = (getHeight() - d) / 2;
            Color fg = UIManager.getColor("Label.disabledForeground");
            g.setColor(fg == null ? Color.GRAY : fg);
            g.setStroke(new java.awt.BasicStroke(1.4f));
            g.drawOval(x, y, d, d);
            g.setFont(getFont().deriveFont(Font.BOLD, 12f));
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.drawString("?", x + (d - fm.stringWidth("?")) / 2f + 0.5f, y + (d + fm.getAscent() - fm.getDescent()) / 2f);
            g.dispose();
        }
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
        draftsTab.setText("Drafts (" + drafts.size() + ")");
        exportedTab.setText("Exported (" + exported.size() + ")");
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
        JList<Entry> list = draftsTab.isSelected() ? draftList : exportedList;
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
