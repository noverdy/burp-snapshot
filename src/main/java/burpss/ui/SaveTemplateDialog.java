package burpss.ui;

import burpss.core.Template;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class SaveTemplateDialog {

    private static final String PARTS = "burpss.templateParts";

    private SaveTemplateDialog() {
    }

    static Template show(Component parent, Template captured, String suggestedName, TemplateStore store, burpss.core.Settings.Store prefs) {
        List<String> remembered = Arrays.asList(Optional.ofNullable(prefs.get(PARTS)).orElse("marks,redactions,title,caption,headers,settings").split(","));
        JTextField name = new JTextField(suggestedName, 28);
        name.addAncestorListener(new AncestorListener() {
            public void ancestorAdded(AncestorEvent e) { SwingUtilities.invokeLater(() -> { name.requestFocusInWindow(); name.selectAll(); }); }
            public void ancestorRemoved(AncestorEvent e) { }
            public void ancestorMoved(AncestorEvent e) { }
        });
        JTextField title = new JTextField(captured.title, 28);
        JTextField caption = new JTextField(captured.caption, 28);
        String placeholders = "{method}, {path}, {host} and {status} are filled in from each snapshot";
        title.setToolTipText(placeholders);
        caption.setToolTipText(placeholders);
        int redactions = captured.redactions.size() + captured.unredactions.size();
        int headers = captured.toggledHeaders.get(0).size() + captured.toggledHeaders.get(1).size();
        JCheckBox marks = part("Marks (" + captured.marks.size() + ")", "marks", captured.hasMarks, remembered);
        JCheckBox redact = part("Redactions (" + redactions + ")", "redactions", captured.hasRedactions, remembered);
        JCheckBox useTitle = part("Title", "title", !captured.title.isBlank(), remembered);
        JCheckBox useCaption = part("Caption", "caption", !captured.caption.isBlank(), remembered);
        JCheckBox header = part("Header visibility (" + headers + ")", "headers", captured.hasHeaders, remembered);
        JCheckBox look = part("Snapshot settings (theme, layout, body offsets…)", "settings", true, remembered);
        title.setEnabled(useTitle.isSelected());
        caption.setEnabled(useCaption.isSelected());
        useTitle.addActionListener(e -> title.setEnabled(useTitle.isSelected()));
        useCaption.addActionListener(e -> caption.setEnabled(useCaption.isSelected()));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(left(bold("Name")));
        panel.add(left(name));
        panel.add(left(spacer()));
        panel.add(left(bold("Include")));
        panel.add(left(marks));
        panel.add(left(redact));
        panel.add(left(useTitle));
        panel.add(left(indented(title)));
        panel.add(left(useCaption));
        panel.add(left(indented(caption)));
        panel.add(left(header));
        panel.add(left(look));

        while (true) {
            int result = JOptionPane.showConfirmDialog(parent, panel, "Save as template", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return null;
            String chosen = name.getText().strip();
            if (chosen.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "Give the template a name.", "Save as template", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (!(marks.isSelected() || redact.isSelected() || useTitle.isSelected() || useCaption.isSelected() || header.isSelected() || look.isSelected())) {
                JOptionPane.showMessageDialog(parent, "Pick at least one part to include.", "Save as template", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            Optional<Template> existing = store.templates().stream().filter(t -> t.name.equalsIgnoreCase(chosen)).findFirst();
            if (existing.isPresent() && JOptionPane.showConfirmDialog(parent, "Replace the template “" + existing.get().name + "”?",
                    "Save as template", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) continue;
            Template t = new Template();
            existing.ifPresent(e -> t.id = e.id);
            t.name = chosen;
            t.lastUsed = System.currentTimeMillis();
            if (marks.isSelected()) {
                t.hasMarks = true;
                t.marks.addAll(captured.marks);
            }
            if (redact.isSelected()) {
                t.hasRedactions = true;
                t.redactions.addAll(captured.redactions);
                t.unredactions.addAll(captured.unredactions);
            }
            if (header.isSelected()) {
                t.hasHeaders = true;
                for (int pane = 0; pane < 2; pane++) t.toggledHeaders.get(pane).addAll(captured.toggledHeaders.get(pane));
            }
            if (useTitle.isSelected()) t.title = title.getText().strip();
            if (useCaption.isSelected()) t.caption = caption.getText().strip();
            if (look.isSelected()) {
                t.settings = captured.settings;
                t.bodyOffset[0] = captured.bodyOffset[0];
                t.bodyOffset[1] = captured.bodyOffset[1];
            }
            List<String> parts = new ArrayList<>();
            for (JCheckBox box : List.of(marks, redact, useTitle, useCaption, header, look)) {
                if (box.isSelected()) parts.add((String) box.getClientProperty("part"));
            }
            prefs.set(PARTS, String.join(",", parts));
            return t;
        }
    }

    private static JCheckBox part(String label, String key, boolean available, List<String> remembered) {
        JCheckBox box = new JCheckBox(label, available && remembered.contains(key));
        box.setEnabled(available);
        box.putClientProperty("part", key);
        return box;
    }

    private static JLabel bold(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return label;
    }

    private static JComponent indented(JComponent field) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBorder(BorderFactory.createEmptyBorder(0, 24, 4, 0));
        holder.add(field);
        return holder;
    }

    private static JComponent spacer() {
        JPanel gap = new JPanel();
        gap.setPreferredSize(new java.awt.Dimension(1, 10));
        gap.setMaximumSize(new java.awt.Dimension(Short.MAX_VALUE, 10));
        return gap;
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (!(c instanceof JCheckBox) && !(c instanceof JLabel)) c.setMaximumSize(new java.awt.Dimension(Short.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }
}
