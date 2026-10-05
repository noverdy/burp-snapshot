package burpss.ui;

import burpss.core.HttpText;
import burpss.render.Fonts;
import burpss.render.Theme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

final class MockBurp {

    static final Color ORANGE = new Color(0xFF6633);
    static final Color LINE = new Color(0xDFDFDF);

    private MockBurp() {
    }

    static JFrame frame(JComponent content, int width, int height) {
        JFrame frame = new JFrame();
        frame.setContentPane(content);
        frame.addNotify();
        java.awt.Insets insets = frame.getInsets();
        frame.setSize(width + insets.left + insets.right, height + insets.top + insets.bottom);
        frame.validate();
        return frame;
    }

    static JPanel mainWindow(String selectedTab, JComponent body) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);
        root.add(tabs(selectedTab, 14f, true, "Dashboard", "Target", "Proxy", "Intruder", "Repeater", "Collaborator",
                "Sequencer", "Decoder", "Comparer", "Logger", "Organizer", "Extensions", "Learn"), BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        return root;
    }

    static JComponent tabs(String selected, float size, boolean top, String... names) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bar.setBackground(top ? Color.WHITE : new Color(0xFBFBFB));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
        for (String name : names) {
            JLabel tab = new JLabel(name);
            boolean on = name.equals(selected);
            tab.setFont(Fonts.sans(size, on));
            tab.setForeground(on ? (top ? ORANGE : Color.BLACK) : new Color(0x4B4B4B));
            tab.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, on ? 3 : 0, 0, ORANGE),
                    BorderFactory.createEmptyBorder(top ? 10 : 8, 14, on ? 7 : 10, 14)));
            bar.add(tab);
        }
        return bar;
    }

    static JTable table(String[] columns, Object[][] rows) {
        JTable table = new JTable(new DefaultTableModel(rows, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        }) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                return renderer.getTableCellRendererComponent(this, getValueAt(row, column), isCellSelected(row, column), false, row, column);
            }
        };
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setRowHeight(24);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFont(Fonts.sans(13, false));
        table.getTableHeader().setFont(Fonts.sans(12.5f, true));
        table.setSelectionBackground(new Color(0xFFD4C2));
        table.setSelectionForeground(Color.BLACK);
        return table;
    }

    static JPanel proxyHistory(JTable history, String request, String response) {
        JScrollPane tableScroll = new JScrollPane(history);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        JSplitPane editors = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editor("Request", request, true), editor("Response", response, false));
        editors.setResizeWeight(0.5);
        editors.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, editors);
        split.setResizeWeight(0.38);
        split.setBorder(BorderFactory.createEmptyBorder());
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(tabs("HTTP history", 13f, false, "Intercept", "HTTP history", "WebSockets history", "Match and replace", "Proxy settings"),
                BorderLayout.NORTH);
        JLabel filter = new JLabel("  Filter settings: Hiding CSS, image and general binary content");
        filter.setFont(Fonts.sans(12.5f, false));
        filter.setForeground(new Color(0x6A6A6C));
        filter.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        JPanel center = new JPanel(new BorderLayout());
        center.add(filter, BorderLayout.NORTH);
        center.add(split, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    static JPanel editor(String title, String text, boolean request) {
        JLabel heading = new JLabel(title);
        heading.setFont(Fonts.sans(15, true));
        heading.setBorder(BorderFactory.createEmptyBorder(8, 12, 2, 12));
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setBackground(Color.WHITE);
        top.add(heading);
        JComponent viewTabs = tabs("Pretty", 13f, true, "Pretty", "Raw", "Hex");
        top.add(viewTabs);
        JTextPane pane = new JTextPane();
        pane.setFont(Fonts.mono(13));
        pane.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        HttpText parsed = HttpText.parse(text, request);
        String clean = text.replace("\r", "");
        StyledDocument doc = pane.getStyledDocument();
        try {
            doc.insertString(0, clean, null);
        } catch (javax.swing.text.BadLocationException e) {
            throw new IllegalStateException(e);
        }
        int out = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\r') continue;
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, Theme.LIGHT.color(parsed.styleAt(i)));
            StyleConstants.setFontFamily(attrs, Fonts.mono(13).getFamily());
            doc.setCharacterAttributes(out++, 1, attrs, true);
        }
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.add(top, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    static JPanel attackResults(JTable results) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.add(tabs("Results", 13.5f, true, "Results", "Positions", "Payloads", "Resource pool", "Settings"), BorderLayout.NORTH);
        JLabel filter = new JLabel("  Filter: Showing all items");
        filter.setFont(Fonts.sans(12.5f, false));
        filter.setForeground(new Color(0x6A6A6C));
        filter.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        JScrollPane scroll = new JScrollPane(results);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
        JPanel center = new JPanel(new BorderLayout());
        center.add(filter, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        JLabel status = new JLabel("  Finished");
        status.setFont(Fonts.sans(12.5f, false));
        status.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
                BorderFactory.createEmptyBorder(6, 4, 6, 4)));
        panel.add(center, BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    static Font menuFont() {
        return Fonts.sans(13.5f, false);
    }
}
