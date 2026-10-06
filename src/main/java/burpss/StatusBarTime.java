package burpss;

import javax.swing.JLabel;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StatusBarTime {

    private static final Pattern STATUS = Pattern.compile("([\\d,.]+)\\s*bytes\\s*\\|\\s*([\\d,.]+)\\s*millis");

    private StatusBarTime() {
    }

    static long millis(InputEvent event, int responseBytes) {
        if (event == null || !(event.getSource() instanceof Component source)) return -1;
        for (Container c = source.getParent(); c != null; c = c.getParent()) {
            long found = search(c, responseBytes);
            if (found >= 0) return found;
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

    private static long number(String digits) {
        try {
            return Long.parseLong(digits.replaceAll("[,.]", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
