package burpss.ui;

import burpss.core.Shortcuts;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class PreferencesDialog extends JDialog {

    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");
    private static final Map<Integer, String> KEY_NAMES = Map.ofEntries(
            Map.entry(KeyEvent.VK_COMMA, "Comma"), Map.entry(KeyEvent.VK_PERIOD, "Period"),
            Map.entry(KeyEvent.VK_EQUALS, "Equals"), Map.entry(KeyEvent.VK_MINUS, "Minus"),
            Map.entry(KeyEvent.VK_ENTER, "Enter"), Map.entry(KeyEvent.VK_BACK_SPACE, "Backspace"),
            Map.entry(KeyEvent.VK_DELETE, "Delete"), Map.entry(KeyEvent.VK_SPACE, "Space"),
            Map.entry(KeyEvent.VK_UP, "Up"), Map.entry(KeyEvent.VK_DOWN, "Down"),
            Map.entry(KeyEvent.VK_LEFT, "Left"), Map.entry(KeyEvent.VK_RIGHT, "Right"),
            Map.entry(KeyEvent.VK_HOME, "Home"), Map.entry(KeyEvent.VK_END, "End"));

    private final HotkeyField quickCopy, open;
    private final JTextField title;
    private final JLabel warning = new JLabel(" ");

    private PreferencesDialog(Window owner, Shortcuts current, Function<Shortcuts, String> apply) {
        super(owner, "Snapshot settings", ModalityType.APPLICATION_MODAL);
        quickCopy = new HotkeyField(current.quickCopy);
        open = new HotkeyField(current.open);
        title = new JTextField(current.titleTemplate, 22);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        List<JLabel> captions = new ArrayList<>();
        form.add(heading("Hotkeys"));
        form.add(row(captions, "Quick copy", quickCopy.panel()));
        form.add(hint("Copies a snapshot with your last settings, no window"));
        form.add(row(captions, "Open in Snapshot", open.panel()));
        form.add(hint("Click a field and press the keys."));
        warning.setForeground(new Color(0xD9822B));
        form.add(left(warning));
        form.add(Box.createVerticalStrut(10));
        form.add(heading("Quick copy title"));
        form.add(row(captions, "Template", title));
        form.add(hint("{method}  {path}  {host}  {status}  {date}"));
        int width = captions.stream().mapToInt(c -> c.getPreferredSize().width).max().orElse(0);
        captions.forEach(c -> c.setPreferredSize(new Dimension(width, c.getPreferredSize().height)));

        JButton reset = new JButton("Reset to defaults");
        reset.addActionListener(e -> {
            quickCopy.set(Shortcuts.QUICK_COPY);
            open.set(Shortcuts.OPEN);
            title.setText(Shortcuts.TITLE);
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            Shortcuts edited = new Shortcuts();
            edited.quickCopy = quickCopy.value;
            edited.open = open.value;
            edited.titleTemplate = title.getText().strip();
            String error = apply.apply(edited);
            if (error == null) dispose();
            else warning.setText(error);
        });
        getRootPane().setDefaultButton(save);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                save.requestFocusInWindow();
            }
        });
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        buttons.add(reset, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(cancel);
        right.add(save);
        buttons.add(right, BorderLayout.EAST);

        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        updateWarning();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    public static void show(Component parent, Shortcuts current, Function<Shortcuts, String> apply) {
        Window owner = parent instanceof Window w ? w : javax.swing.SwingUtilities.getWindowAncestor(parent);
        new PreferencesDialog(owner, current, apply).setVisible(true);
    }

    public static String display(String hotkey) {
        if (hotkey.isBlank()) return "None";
        if (!MAC) return hotkey;
        return hotkey.replace("Ctrl+", "⌘").replace("Alt+", "⌥").replace("Shift+", "⇧");
    }

    public static String record(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT || code == KeyEvent.VK_META) return null;
        String key = (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) || (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9)
                ? String.valueOf((char) code)
                : code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12 ? "F" + (code - KeyEvent.VK_F1 + 1) : KEY_NAMES.get(code);
        int mods = e.getModifiersEx();
        boolean ctrl = (mods & Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()) != 0;
        boolean alt = (mods & InputEvent.ALT_DOWN_MASK) != 0;
        if (key == null || !(ctrl || alt)) return null;
        return (ctrl ? "Ctrl+" : "") + (alt ? "Alt+" : "") + ((mods & InputEvent.SHIFT_DOWN_MASK) != 0 ? "Shift+" : "") + key;
    }

    private void updateWarning() {
        if (!quickCopy.value.isBlank() && quickCopy.value.equals(open.value)) {
            warning.setText("Both actions use the same keys");
        } else if (Shortcuts.usedByBurp(quickCopy.value) || Shortcuts.usedByBurp(open.value)) {
            warning.setText("This combination is one of Burp's default hotkeys");
        } else {
            warning.setText(" ");
        }
    }

    private static JComponent heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return left(label);
    }

    private static JComponent hint(String text) {
        JTextArea label = new JTextArea(text);
        label.setEditable(false);
        label.setFocusable(false);
        label.setOpaque(false);
        label.setLineWrap(true);
        label.setWrapStyleWord(true);
        label.setColumns(34);
        Color c = UIManager.getColor("Label.disabledForeground");
        label.setForeground(c == null ? Color.GRAY : c);
        label.setFont(UIManager.getFont("Label.font").deriveFont(UIManager.getFont("Label.font").getSize2D() - 1f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        label.setSize(label.getPreferredSize().width, Short.MAX_VALUE);
        return left(label);
    }

    private static JComponent row(List<JLabel> captions, String caption, JComponent field) {
        JLabel label = new JLabel(caption);
        captions.add(label);
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return left(row);
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private final class HotkeyField {

        private final JTextField field = new JTextField(12);
        private String value;

        HotkeyField(String initial) {
            field.setEditable(false);
            field.setFocusTraversalKeysEnabled(false);
            field.setToolTipText("Click, then press the key combination");
            field.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    e.consume();
                    String recorded = record(e);
                    if (recorded != null) set(recorded);
                }
            });
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                public void focusGained(java.awt.event.FocusEvent e) { field.setText(focusedText()); }
                public void focusLost(java.awt.event.FocusEvent e) { field.setText(display(value)); }
            });
            value = initial;
            field.setText(display(initial));
        }

        private String focusedText() {
            return value.isBlank() ? "Press keys…" : display(value) + "  (press new keys)";
        }

        void set(String hotkey) {
            value = hotkey;
            field.setText(field.hasFocus() ? focusedText() : display(hotkey));
            updateWarning();
        }

        JComponent panel() {
            JButton clear = new JButton("Clear");
            clear.setToolTipText("Turn this hotkey off");
            clear.addActionListener(e -> set(""));
            JPanel panel = new JPanel(new BorderLayout(4, 0));
            panel.add(field, BorderLayout.CENTER);
            panel.add(clear, BorderLayout.EAST);
            return panel;
        }
    }
}
