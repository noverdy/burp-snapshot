package burpss;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableModel;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShownTime {

    private static final Pattern STATUS = Pattern.compile("([\\d,.]+)\\s*bytes\\s*\\|\\s*([\\d,.]+)\\s*millis");

    private ShownTime() {
    }

    static long millis(InputEvent event, int responseBytes, int index) {
        Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        Component source = event != null && event.getSource() instanceof Component c ? c : focused;
        if (source == null) return -1;
        long fromTable = tableMillis(source, responseBytes, index);
        if (fromTable < 0 && focused != null && focused != source) fromTable = tableMillis(focused, responseBytes, index);
        if (fromTable >= 0) return fromTable;
        for (Container c = source.getParent(); c != null; c = c.getParent()) {
            long found = search(c, responseBytes);
            if (found >= 0) return found;
        }
        return -1;
    }

    private static long tableMillis(Component source, int responseBytes, int index) {
        JTable own = source instanceof JTable t ? t : (JTable) SwingUtilities.getAncestorOfClass(JTable.class, source);
        if (own != null) return rowMillis(own, responseBytes, index);
        for (Container c = source.getParent(); c != null; c = c.getParent()) {
            List<JTable> tables = new ArrayList<>();
            collectTables(c, tables);
            for (JTable table : tables) {
                long ms = rowMillis(table, responseBytes, index);
                if (ms >= 0) return ms;
            }
        }
        return -1;
    }

    private static void collectTables(Component component, List<JTable> tables) {
        if (!component.isShowing()) return;
        if (component instanceof JTable table) tables.add(table);
        else if (component instanceof Container container) {
            for (Component child : container.getComponents()) collectTables(child, tables);
        }
    }

    private static long rowMillis(JTable table, int responseBytes, int index) {
        TableModel model = table.getModel();
        int time = column(model, "end response"), length = column(model, "length");
        if (time < 0 || length < 0) return -1;
        int[] selected = table.getSelectedRows();
        List<Integer> rows = new ArrayList<>();
        if (index < selected.length) rows.add(selected[index]);
        for (int row : selected) rows.add(row);
        for (int viewRow : rows) {
            int row = table.convertRowIndexToModel(viewRow);
            if (number(model.getValueAt(row, length)) != responseBytes) continue;
            long ms = number(model.getValueAt(row, time));
            if (ms >= 0) return ms;
        }
        return -1;
    }

    private static int column(TableModel model, String name) {
        for (int c = 0; c < model.getColumnCount(); c++) {
            String column = model.getColumnName(c);
            if (column != null && column.toLowerCase(java.util.Locale.ROOT).startsWith(name)) return c;
        }
        return -1;
    }

    private static long search(Component component, int responseBytes) {
        if (!component.isShowing()) return -1;
        String text = component instanceof JLabel l ? l.getText() : component instanceof JTextComponent t && t.getDocument().getLength() < 200 ? t.getText() : null;
        if (text != null) {
            Matcher m = STATUS.matcher(text);
            if (m.find() && number(m.group(1)) == responseBytes) return number(m.group(2));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                long found = search(child, responseBytes);
                if (found >= 0) return found;
            }
        }
        return -1;
    }

    private static long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        String digits = value == null ? "" : value.toString().replaceAll("[,.\\s]", "");
        try {
            return digits.isEmpty() ? -1 : Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
