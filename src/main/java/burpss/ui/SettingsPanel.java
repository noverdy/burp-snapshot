package burpss.ui;

import burpss.core.Mark;
import burpss.core.Settings;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.io.File;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

final class SettingsPanel extends JPanel {

    private final Settings s;
    private final Runnable onChange;
    private final Runnable onPreference;
    private JPanel body;
    private final java.util.List<JLabel> captions = new java.util.ArrayList<>();

    SettingsPanel(Settings settings, boolean table, Runnable onChange, Runnable onPreference) {
        this.onPreference = onPreference;
        this.s = settings;
        this.onChange = onChange;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

        section("Quick", true, false);
        segmented("Theme", Settings.ThemeName.values(), () -> s.theme, v -> s.theme = v);
        if (!table) segmented("Layout", Settings.Layout.values(), () -> s.layout, v -> s.layout = v);
        swatches();
        segmented("Notes", Settings.Notes.values(), () -> s.notes, v -> s.notes = v);
        if (table) {
            text("Contains", false, () -> s.containsText, v -> s.containsText = v);
        } else {
            segmented("Redaction", Settings.RedactStyle.values(), () -> s.redactStyle, v -> s.redactStyle = v);
        }
        check("Show response time", () -> s.showTime, v -> s.showTime = v);
        watermarkQuick();
        segmented("Export scale", new String[]{"1x", "2x", "3x"}, s.exportScale - 1, i -> s.exportScale = i + 1);

        if (!table) {
            section("Content", false, true);
            check("Pretty-print JSON", () -> s.prettyJson, v -> s.prettyJson = v);
            check("Line numbers", () -> s.lineNumbers, v -> s.lineNumbers = v);
            number("Max body lines", 0, 10_000, () -> s.maxBodyLines, v -> s.maxBodyLines = v);
            number("Wrap at", 20, 400, () -> s.wrapColumns, v -> s.wrapColumns = v);
            text("Hidden headers", true, () -> s.hideHeaders, v -> s.hideHeaders = v);

            section("Redaction rules", false, true);
            check("Auto-redact", () -> s.autoRedact, v -> s.autoRedact = v);
            number("Reveal last (auto)", 0, 12, () -> s.revealChars, v -> s.revealChars = v);
            text("Headers", true, () -> s.redactHeaders, v -> s.redactHeaders = v);
            text("Param / key names", true, () -> s.redactParams, v -> s.redactParams = v);
        }

        section("Watermark style", false, true);
        logoPicker();
        choice("Placement", Settings.WatermarkPlacement.values(), () -> s.watermarkPlacement, v -> s.watermarkPlacement = v);
        slider("Opacity", 2, 60, "%", () -> s.watermarkOpacity, v -> s.watermarkOpacity = v);
        slider("Spacing", 40, 250, "%", () -> s.watermarkSpacing, v -> s.watermarkSpacing = v);
        number("Size", 8, 72, () -> s.watermarkSize, v -> s.watermarkSize = v);
        number("Angle", -90, 90, () -> s.watermarkAngle, v -> s.watermarkAngle = v);

        section("Frame & text", false, true);
        segmented("Frame", Settings.Frame.values(), () -> s.frame, v -> s.frame = v);
        check("Header bar", () -> s.showHeaderBar, v -> s.showHeaderBar = v);
        check("Number noted marks", () -> s.numberMarks, v -> s.numberMarks = v);
        number("Font size", 9, 24, () -> s.fontSize, v -> s.fontSize = v);
        alignCaptions();
    }

    JScrollPane scrollable() {
        JPanel top = new ViewportWidthPanel();
        top.add(this, BorderLayout.NORTH);
        JScrollPane pane = new JScrollPane(top, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getVerticalScrollBar().setUnitIncrement(16);
        pane.setPreferredSize(new Dimension(400, 600));
        return pane;
    }

    private void section(String title, boolean open, boolean collapsible) {
        if (getComponentCount() > 0) {
            add(Box.createVerticalStrut(6));
            JSeparator line = new JSeparator();
            line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            add(line);
            add(Box.createVerticalStrut(2));
        }
        body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        body.setVisible(open || java.util.List.of(s.openSections.split(",")).contains(title));
        JButton header = new JButton();
        header.setHorizontalAlignment(JButton.LEFT);
        header.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        header.setContentAreaFilled(false);
        header.setFocusPainted(false);
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height + 12));
        JPanel section = body;
        Runnable label = () -> header.setText(collapsible ? (section.isVisible() ? "▾  " : "▸  ") + title : title);
        label.run();
        if (collapsible) {
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            header.addActionListener(e -> {
                section.setVisible(!section.isVisible());
                java.util.Set<String> opened = new java.util.LinkedHashSet<>(java.util.List.of(s.openSections.split(",")));
                if (section.isVisible()) opened.add(title);
                else opened.remove(title);
                opened.remove("");
                s.openSections = String.join(",", opened);
                onPreference.run();
                label.run();
                revalidate();
            });
        }
        add(header);
        add(section);
    }

    private void row(String label, JComponent field) {
        JLabel caption = new JLabel(label);
        caption.setForeground(secondaryText());
        captions.add(caption);
        JPanel line = new JPanel(new BorderLayout(10, 0));
        line.add(caption, BorderLayout.WEST);
        line.add(field, BorderLayout.CENTER);
        wide(line);
    }

    private void wide(JComponent field) {
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0), field.getBorder()));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
        body.add(field);
    }

    private void alignCaptions() {
        int width = captions.stream().mapToInt(c -> c.getPreferredSize().width).max().orElse(0);
        for (JLabel c : captions) c.setPreferredSize(new Dimension(width, c.getPreferredSize().height));
    }

    private static Color secondaryText() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c == null ? Color.GRAY : c.darker();
    }

    private <E extends Enum<E>> void segmented(String label, E[] values, Supplier<E> get, Consumer<E> set) {
        String[] names = java.util.Arrays.stream(values).map(SettingsPanel::pretty).toArray(String[]::new);
        segmented(label, names, get.get().ordinal(), i -> set.accept(values[i]));
    }

    private void segmented(String label, String[] names, int selected, IntConsumer select) {
        JPanel group = new JPanel(new GridLayout(1, names.length, 0, 0));
        ButtonGroup buttons = new ButtonGroup();
        for (int i = 0; i < names.length; i++) {
            int index = i;
            JToggleButton b = new JToggleButton(names[i]);
            b.setToolTipText(names[i]);
            b.setMinimumSize(new Dimension(0, b.getPreferredSize().height));
            b.setPreferredSize(new Dimension(0, b.getPreferredSize().height));
            b.setFocusPainted(false);
            b.setMargin(new Insets(3, 2, 3, 2));
            b.putClientProperty("JButton.buttonType", "segmented");
            b.putClientProperty("JButton.segmentPosition", i == 0 ? "first" : i == names.length - 1 ? "last" : "middle");
            b.setSelected(i == selected);
            b.addActionListener(e -> {
                select.accept(index);
                onChange.run();
            });
            buttons.add(b);
            group.add(b);
        }
        row(label, group);
    }

    private void swatches() {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ButtonGroup buttons = new ButtonGroup();
        for (int i = 0; i < Mark.PALETTE.length; i++) {
            int index = i;
            JToggleButton b = new JToggleButton(new Swatch(Mark.PALETTE[i], 20, false));
            b.setSelectedIcon(new Swatch(Mark.PALETTE[i], 20, true));
            b.setToolTipText(Mark.PALETTE_NAMES[i]);
            b.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            b.setContentAreaFilled(false);
            b.setFocusPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setSelected(i == s.markColor);
            b.addActionListener(e -> {
                s.markColor = index;
                onPreference.run();
            });
            buttons.add(b);
            group.add(b);
        }
        row("Mark color", group);
    }

    private void watermarkQuick() {
        JCheckBox enabled = new JCheckBox();
        enabled.setSelected(s.watermark);
        JTextField text = new JTextField(s.watermarkText, 6);
        text.setToolTipText("Supports {date} and {host}");
        text.setEnabled(s.watermark);
        enabled.addActionListener(e -> {
            s.watermark = enabled.isSelected();
            text.setEnabled(s.watermark);
            onChange.run();
        });
        onEdit(text, () -> s.watermarkText = text.getText());
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.add(enabled, BorderLayout.WEST);
        panel.add(text, BorderLayout.CENTER);
        row("Watermark", panel);
    }

    private <E extends Enum<E>> void choice(String label, E[] values, Supplier<E> get, Consumer<E> set) {
        JComboBox<String> box = new JComboBox<>(java.util.Arrays.stream(values).map(SettingsPanel::pretty).toArray(String[]::new));
        box.setSelectedIndex(get.get().ordinal());
        box.addActionListener(e -> {
            set.accept(values[box.getSelectedIndex()]);
            onChange.run();
        });
        row(label, box);
    }

    private void check(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
        JCheckBox box = new JCheckBox(label);
        box.setSelected(get.get());
        box.addActionListener(e -> {
            set.accept(box.isSelected());
            onChange.run();
        });
        wide(box);
    }

    private void number(String label, int min, int max, Supplier<Integer> get, IntConsumer set) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(Math.max(min, Math.min(max, get.get())), min, max, 1));
        spinner.addChangeListener(e -> {
            set.accept((Integer) spinner.getValue());
            onChange.run();
        });
        row(label, spinner);
    }

    private void slider(String label, int min, int max, String unit, Supplier<Integer> get, IntConsumer set) {
        JSlider slider = new JSlider(min, max, Math.max(min, Math.min(max, get.get())));
        slider.setPreferredSize(new Dimension(80, slider.getPreferredSize().height));
        JLabel value = new JLabel(slider.getValue() + unit);
        value.setPreferredSize(new Dimension(40, value.getPreferredSize().height));
        value.setHorizontalAlignment(JLabel.RIGHT);
        slider.addChangeListener(e -> {
            value.setText(slider.getValue() + unit);
            set.accept(slider.getValue());
            if (!slider.getValueIsAdjusting()) onChange.run();
        });
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.add(slider, BorderLayout.CENTER);
        panel.add(value, BorderLayout.EAST);
        row(label, panel);
    }

    private void text(String label, boolean multiline, Supplier<String> get, Consumer<String> set) {
        JTextComponent field = multiline ? new JTextArea(get.get(), 3, 10) : new JTextField(get.get(), 6);
        onEdit(field, () -> set.accept(field.getText()));
        if (!multiline) {
            row(label, field);
            return;
        }
        JTextArea area = (JTextArea) field;
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(UIManager.getFont("TextField.font"));
        area.setMargin(new Insets(4, 6, 4, 6));
        JLabel caption = new JLabel(label);
        caption.setForeground(secondaryText());
        wide(caption);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(100, 64));
        JPanel holder = new JPanel(new BorderLayout());
        holder.add(scroll);
        wide(holder);
    }

    private void onEdit(JTextComponent field, Runnable apply) {
        Timer debounce = new Timer(350, e -> {
            apply.run();
            onChange.run();
        });
        debounce.setRepeats(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });
    }

    private void logoPicker() {
        JButton choose = new JButton(logoName());
        choose.setHorizontalAlignment(AbstractButton.LEFT);
        JButton clear = new JButton("✕");
        clear.setToolTipText("Remove logo");
        clear.setEnabled(!s.watermarkLogo.isBlank());
        Runnable refresh = () -> {
            choose.setText(logoName());
            clear.setEnabled(!s.watermarkLogo.isBlank());
            onChange.run();
        };
        choose.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                s.watermarkLogo = chooser.getSelectedFile().getAbsolutePath();
                refresh.run();
            }
        });
        clear.addActionListener(e -> {
            s.watermarkLogo = "";
            refresh.run();
        });
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.add(choose, BorderLayout.CENTER);
        panel.add(clear, BorderLayout.EAST);
        row("Logo", panel);
    }

    private String logoName() {
        return s.watermarkLogo.isBlank() ? "Choose image…" : new File(s.watermarkLogo).getName();
    }

    static String pretty(Object value) {
        String raw = String.valueOf(value).replace('_', ' ').toLowerCase(Locale.ROOT);
        return raw.isEmpty() ? raw : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {

        ViewportWidthPanel() {
            super(new BorderLayout());
        }

        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 16; }
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return r.height; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
