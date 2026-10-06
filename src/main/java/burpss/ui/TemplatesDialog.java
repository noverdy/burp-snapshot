package burpss.ui;

import burpss.core.Template;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import java.util.function.Consumer;

final class TemplatesDialog extends JDialog {

    private final TemplateStore store;
    private final Consumer<Template> apply;
    private final JTextField search = new JTextField(28);
    private final DefaultListModel<Template> model = new DefaultListModel<>();
    private final JList<Template> list = new JList<>(model);

    private TemplatesDialog(Window owner, TemplateStore store, Consumer<Template> apply) {
        super(owner, "Templates", ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.apply = apply;
        search.putClientProperty("JTextField.placeholderText", "Search templates");
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { reload(); }
            public void removeUpdate(DocumentEvent e) { reload(); }
            public void changedUpdate(DocumentEvent e) { }
        });
        search.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int at = list.getSelectedIndex();
                if (e.getKeyCode() == KeyEvent.VK_DOWN && at < model.size() - 1) list.setSelectedIndex(at + 1);
                else if (e.getKeyCode() == KeyEvent.VK_UP && at > 0) list.setSelectedIndex(at - 1);
                else return;
                list.ensureIndexIsVisible(list.getSelectedIndex());
                e.consume();
            }
        });
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(renderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) applySelected();
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete");
        list.getActionMap().put("delete", new javax.swing.AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { deleteSelected(); }
        });

        JButton applyButton = button("Apply", this::applySelected);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(button("Rename…", this::renameSelected));
        buttons.add(button("Delete…", this::deleteSelected));
        buttons.add(button("Close", this::dispose));
        buttons.add(applyButton);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(420, 320));
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(search, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(applyButton);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        reload();
        pack();
        setLocationRelativeTo(owner);
    }

    static void show(Window owner, TemplateStore store, Consumer<Template> apply) {
        new TemplatesDialog(owner, store, apply).setVisible(true);
    }

    private void reload() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        Template selected = list.getSelectedValue();
        model.clear();
        for (Template t : store.recent()) {
            if (query.isEmpty() || t.name.toLowerCase(Locale.ROOT).contains(query) || t.summary().contains(query)) model.addElement(t);
        }
        int keep = selected == null ? -1 : indexOf(selected.id);
        if (!model.isEmpty()) list.setSelectedIndex(Math.max(0, keep));
    }

    private int indexOf(String id) {
        for (int i = 0; i < model.size(); i++) if (model.get(i).id.equals(id)) return i;
        return -1;
    }

    private void applySelected() {
        Template t = list.getSelectedValue();
        if (t == null) return;
        dispose();
        apply.accept(t);
    }

    private void renameSelected() {
        Template t = list.getSelectedValue();
        if (t == null) return;
        Object value = JOptionPane.showInputDialog(this, "New name", "Rename template", JOptionPane.PLAIN_MESSAGE, null, null, t.name);
        if (value == null || value.toString().isBlank()) return;
        t.name = value.toString().strip();
        store.saveTemplate(t);
        reload();
    }

    private void deleteSelected() {
        Template t = list.getSelectedValue();
        if (t == null) return;
        if (JOptionPane.showConfirmDialog(this, "Delete the template “" + t.name + "”?", "Delete template",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        store.deleteTemplate(t.id);
        reload();
    }

    private static JButton button(String label, Runnable action) {
        JButton b = new JButton(label);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static ListCellRenderer<Template> renderer() {
        return (list, t, index, selected, focus) -> {
            JLabel name = new JLabel(t.name);
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            name.putClientProperty("html.disable", Boolean.TRUE);
            JLabel detail = new JLabel(t.summary() + "  ·  " + (t.lastUsed > 0 ? "used " + SnapshotsTab.ago(t.lastUsed) : "never used"));
            detail.setForeground(selected ? list.getSelectionForeground() : UIManager.getColor("Label.disabledForeground"));
            name.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            JPanel cell = new JPanel(new GridLayout(2, 1));
            cell.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            cell.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            cell.add(name);
            cell.add(detail);
            return cell;
        };
    }
}
