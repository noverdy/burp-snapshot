package burpss.ui;

import burpss.core.Mark;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.BorderLayout;
import java.awt.Component;

final class NoteDialog {

    private NoteDialog() {
    }

    static boolean edit(Component parent, Mark mark) {
        JTextArea text = new JTextArea(mark.note, 4, 36);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.addAncestorListener(new AncestorListener() {
            public void ancestorAdded(AncestorEvent e) { SwingUtilities.invokeLater(text::requestFocusInWindow); }
            public void ancestorRemoved(AncestorEvent e) { }
            public void ancestorMoved(AncestorEvent e) { }
        });
        JComboBox<String> color = new JComboBox<>(Mark.PALETTE_NAMES);
        color.setSelectedIndex(Math.floorMod(mark.color, Mark.PALETTE.length));
        color.setRenderer(Swatch.paletteRenderer());
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("Note (leave empty for a plain box)"), BorderLayout.NORTH);
        panel.add(new JScrollPane(text), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.add(new JLabel("Color"), BorderLayout.WEST);
        bottom.add(color, BorderLayout.CENTER);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        panel.add(bottom, BorderLayout.SOUTH);
        int result = JOptionPane.showConfirmDialog(parent, panel, "Mark note", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return false;
        mark.note = text.getText().strip();
        mark.color = color.getSelectedIndex();
        return true;
    }
}
