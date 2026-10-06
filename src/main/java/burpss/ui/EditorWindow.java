package burpss.ui;

import burpss.ai.AiMarkup;
import burpss.core.Anchor;
import burpss.core.EditState;
import burpss.core.History;
import burpss.core.Mark;
import burpss.core.Settings;
import burpss.core.Template;
import burpss.core.Templates;
import burpss.render.Content;
import burpss.render.HeaderInfo;
import burpss.render.Scene;
import burpss.render.Theme;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Image;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class EditorWindow extends JDialog implements Canvas.Handler {

    enum Tool { MARK, REDACT }

    public record Update(int item, EditState state, Settings settings, boolean exported) {
    }

    protected final Settings settings;
    private final Settings.Store store;
    protected final Canvas canvas = new Canvas(this);
    private final boolean table;
    private JComponent sidebar;
    private final JComponent actions = buildActions();
    private SettingsPanel settingsPanel;
    private final JTextField titleField = new JTextField(20);
    private final javax.swing.JTextArea captionField = new javax.swing.JTextArea(3, 20);
    private AiMarkup.Model ai;
    private AiDialog aiDialog;
    private TemplateStore templates;
    private final JComboBox<String> zoom = new JComboBox<>(new String[]{"Fit", "50%", "75%", "100%", "150%", "200%"});
    private JScrollPane canvasScroll;
    protected final JToolBar toolbar = new JToolBar();
    protected Tool tool = Tool.MARK;
    protected Scene scene;
    private String logoPath;
    private Image logo;
    private boolean syncingTitle;
    private static final int RECENT_TEMPLATES = 8;
    private final Timer autosave = new Timer(800, e -> flush());
    private final List<Timer> debounces = new ArrayList<>();
    private Consumer<Update> onUpdate = update -> { };
    private boolean dirty;
    private boolean syncingZoom;

    EditorWindow(Window owner, String title, Settings settings, Settings.Store store, boolean table) {
        super(owner, title, ModalityType.MODELESS);
        this.settings = settings;
        this.store = store;
        this.table = table;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        autosave.setRepeats(false);
        buildToolbar(!table);
        watch(titleField, () -> state().title = titleField.getText().strip());
        watch(captionField, () -> state().caption = captionField.getText().strip());
        captionField.setToolTipText("Evidence caption, shown under the content");

        canvasScroll = new JScrollPane(canvas, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        canvasScroll.getVerticalScrollBar().setUnitIncrement(16);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(16);
        canvasScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (scene != null) canvas.refreshZoom();
            }
        });

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(toolbar, BorderLayout.NORTH);
        sidebar = sidebar();
        getContentPane().add(canvasScroll, BorderLayout.CENTER);
        getContentPane().add(sidebar, BorderLayout.EAST);
        bindKeys();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1400, screen.width - 40), Math.min(860, screen.height - 40));
    }

    abstract EditState state();

    abstract History history();

    abstract Content buildContent(Theme theme);

    abstract HeaderInfo header();

    abstract String fileName();

    abstract String aiSystem();

    abstract String aiPrompt(String context);

    abstract AiMarkup.Result aiResult(String reply);

    Template captureTemplate() {
        return null;
    }

    Templates.Applied applyTemplate(Template template) {
        return new Templates.Applied(0, List.of());
    }

    void redactClick(Point2D contentPoint) {
    }

    void redactDrag(Point2D from, Point2D to) {
    }

    void addContentMenuItems(JPopupMenu menu, Point2D contentPoint) {
    }

    public void open(Component parent) {
        AiButton aiButton = new AiButton("AI markup");
        aiButton.setToolTipText("Let Burp AI mark the evidence and write the title and caption");
        aiButton.addActionListener(e -> openAi());
        toolbar.add(javax.swing.Box.createHorizontalGlue());
        toolbar.add(aiButton);
        syncTitleField();
        settingsPanel.syncOffsets();
        setLocationRelativeTo(parent);
        setVisible(true);
        rebuild();
    }

    void rebuild() {
        scene = new Scene(buildContent(Theme.of(settings)), header(), state().marks, settings, logo());
        canvas.setScene(scene);
        settingsPanel.timeAvailable(timeAvailable());
    }

    boolean timeAvailable() {
        return header().timeMs() >= 0;
    }

    void edit(Runnable mutation) {
        history().checkpoint();
        mutation.run();
        rebuild();
        changed();
    }

    public void useAi(AiMarkup.Model model) {
        ai = model;
    }

    public void useTemplates(TemplateStore store) {
        templates = store;
    }

    public void onUpdate(Consumer<Update> listener) {
        onUpdate = listener;
    }

    int item() {
        return 0;
    }

    @Override
    public void dispose() {
        debounces.forEach(Timer::stop);
        flush();
        autosave.stop();
        super.dispose();
    }

    void flush() {
        if (dirty) publish(false);
    }

    private void changed() {
        dirty = true;
        autosave.restart();
    }

    private void publish(boolean exported) {
        dirty = false;
        autosave.stop();
        onUpdate.accept(new Update(item(), state().copy(), settings.copy(), exported));
    }

    void syncTitleField() {
        syncingTitle = true;
        titleField.setText(state().title);
        captionField.setText(state().caption);
        syncingTitle = false;
    }

    private JComponent sidebar() {
        settingsPanel = new SettingsPanel(settings, table, this::settingsChanged, () -> settings.save(store), new SettingsPanel.Offsets() {
            public int get(int pane) { return state().bodyOffset[pane]; }
            public void set(int pane, int lines) { edit(() -> state().bodyOffset[pane] = lines); }
        });
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UIManager.getColor("Separator.foreground")));
        panel.add(settingsPanel.scrollable(textSection()), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    void reloadSettings() {
        getContentPane().remove(sidebar);
        sidebar = sidebar();
        getContentPane().add(sidebar, BorderLayout.EAST);
        settingsPanel.syncOffsets();
        getContentPane().revalidate();
    }

    private void settingsChanged() {
        settings.save(store);
        rebuild();
        changed();
    }

    private static double parseZoom(Object value) {
        try {
            double percent = Double.parseDouble(String.valueOf(value).replace("%", "").strip());
            return Math.max(10, Math.min(500, percent)) / 100.0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void applyZoomField() {
        if (syncingZoom) return;
        double z = parseZoom(zoom.getSelectedItem());
        settings.zoom = z == 0 ? "Fit" : Math.round(z * 100) + "%";
        settings.save(store);
        canvas.setRequestedZoom(z);
    }

    @Override
    public void zoomChanged(double value) {
        syncingZoom = true;
        settings.zoom = Math.round(value * 100) + "%";
        zoom.setSelectedItem(settings.zoom);
        syncingZoom = false;
        settings.save(store);
    }

    private Image logo() {
        if (settings.watermarkLogo.equals(logoPath)) return logo;
        logoPath = settings.watermarkLogo;
        try {
            logo = logoPath.isBlank() ? null : ImageIO.read(new File(logoPath));
        } catch (IOException e) {
            logo = null;
            flash("Could not read logo: " + e.getMessage(), Notice.Kind.ERROR);
        }
        return logo;
    }

    private void buildToolbar(boolean redaction) {
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(2, 4, 2, 6)));
        if (redaction) {
            JButton menu = new JButton("▤ Templates ▾");
            menu.setToolTipText("Apply a saved markup template, or save this one");
            menu.addActionListener(e -> templateMenu().show(menu, 0, menu.getHeight()));
            toolbar.add(menu);
            toolbar.addSeparator();
        }
        ButtonGroup tools = new ButtonGroup();
        toolButton(tools, "▢ Mark", "Click a param to box it, or drag over any text (M)", Tool.MARK).setSelected(true);
        if (redaction) toolButton(tools, "▒ Redact", "Click a value to redact/unredact it, or drag over any text (R)", Tool.REDACT);
        toolbar.addSeparator();
        toolbar.add(button("↶ Undo", () -> { if (history().undo()) afterHistory(); }));
        toolbar.add(button("↷ Redo", () -> { if (history().redo()) afterHistory(); }));
        toolbar.add(button("Clear marks", () -> edit(() -> state().marks.clear())));
        toolbar.addSeparator();

        toolbar.add(new JLabel("Zoom "));
        zoom.setEditable(true);
        zoom.setToolTipText("Pinch or ⌘/Ctrl + scroll to zoom");
        zoom.setSelectedItem(settings.zoom);
        canvas.setRequestedZoom(parseZoom(settings.zoom));
        zoom.setMaximumSize(new java.awt.Dimension(90, zoom.getPreferredSize().height));
        zoom.addActionListener(e -> applyZoomField());
        toolbar.add(zoom);
        toolbar.addSeparator();
    }

    private JToggleButton toolButton(ButtonGroup group, String label, String tip, Tool value) {
        JToggleButton b = new JToggleButton(label);
        b.setToolTipText(tip);
        b.addActionListener(e -> tool = value);
        group.add(b);
        toolbar.add(b);
        b.putClientProperty("tool", value);
        return b;
    }

    private JComponent buildActions() {
        JPanel panel = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        panel.add(button("Copy to clipboard", this::copyImage));
        panel.add(button("Save PNG…", this::saveImage));
        return panel;
    }

    private JComponent textSection() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(14, 12, 4, 12));
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.gridx = 0;
        c.anchor = java.awt.GridBagConstraints.WEST;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new java.awt.Insets(0, 0, 4, 0);
        panel.add(caption("Title"), c);
        c.insets = new java.awt.Insets(0, 0, 10, 0);
        panel.add(titleField, c);
        c.insets = new java.awt.Insets(0, 0, 4, 0);
        panel.add(caption("Caption"), c);
        captionField.setLineWrap(true);
        captionField.setWrapStyleWord(true);
        captionField.setFont(UIManager.getFont("TextField.font"));
        captionField.setMargin(new java.awt.Insets(4, 6, 4, 6));
        JScrollPane captionScroll = new JScrollPane(captionField, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        captionScroll.setPreferredSize(new java.awt.Dimension(100, 72));
        c.insets = new java.awt.Insets(0, 0, 6, 0);
        panel.add(captionScroll, c);
        JButton copyCaption = button("Copy caption", this::copyCaption);
        copyCaption.setToolTipText("Copy the evidence caption as text for your report");
        c.fill = java.awt.GridBagConstraints.NONE;
        c.anchor = java.awt.GridBagConstraints.EAST;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        panel.add(copyCaption, c);
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.insets = new java.awt.Insets(12, 0, 0, 0);
        panel.add(new javax.swing.JSeparator(), c);
        return panel;
    }

    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        return label;
    }

    private void watch(javax.swing.text.JTextComponent field, Runnable apply) {
        Timer debounce = new Timer(300, e -> edit(apply));
        debounce.setRepeats(false);
        debounces.add(debounce);
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { if (!syncingTitle) debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { if (!syncingTitle) debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { }
        });
    }

    private void copyCaption() {
        String caption = state().caption;
        if (caption.isBlank()) {
            flash("No caption yet", Notice.Kind.INFO);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(caption), null);
        flash("Caption copied", Notice.Kind.SUCCESS);
    }

    private void openAi() {
        if (ai == null || !ai.available()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Burp AI isn't available. It needs Burp Suite Professional with AI enabled,\n"
                            + "and \"Use AI\" turned on for Snapshot in Extensions > Installed.",
                    "AI markup", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (aiDialog != null && aiDialog.isDisplayable()) {
            aiDialog.toFront();
            return;
        }
        String initial = state().aiContext.isBlank() ? state().title : state().aiContext;
        aiDialog = new AiDialog(this, initial, !state().marks.isEmpty(), ai, aiSystem(), this::aiPrompt, this::applyAi);
        aiDialog.setVisible(true);
    }

    private String applyAi(String reply, String context, boolean replace) {
        AiMarkup.Result result;
        try {
            result = aiResult(reply);
        } catch (RuntimeException e) {
            return "Couldn't read the AI reply: " + e.getMessage();
        }
        if (result.marks().isEmpty() && result.title().isBlank() && result.caption().isBlank()) {
            return "Burp AI found nothing to mark. Try adding more context.";
        }
        edit(() -> {
            if (replace) state().marks.clear();
            state().marks.addAll(result.marks());
            if (!result.title().isBlank()) state().title = result.title();
            if (!result.caption().isBlank()) state().caption = result.caption();
            state().aiContext = context;
        });
        syncTitleField();
        flash(result.marks().size() + " mark" + (result.marks().size() == 1 ? "" : "s") + " added"
                + (result.unmatched() > 0 ? ", " + result.unmatched() + " skipped" : ""), Notice.Kind.SUCCESS);
        return null;
    }

    private JPopupMenu templateMenu() {
        JPopupMenu menu = new JPopupMenu();
        List<Template> recent = templates == null ? List.of() : templates.recent();
        if (recent.isEmpty()) {
            JMenuItem none = new JMenuItem("No templates yet");
            none.setEnabled(false);
            menu.add(none);
        }
        for (Template t : recent.subList(0, Math.min(RECENT_TEMPLATES, recent.size()))) {
            JMenuItem item = item(t.name, () -> useTemplate(t));
            item.putClientProperty("html.disable", Boolean.TRUE);
            item.setToolTipText(t.summary());
            menu.add(item);
        }
        if (!recent.isEmpty()) {
            menu.addSeparator();
            menu.add(item("All templates… (" + recent.size() + ")", () -> TemplatesDialog.show(this, templates, this::useTemplate)));
        }
        JMenuItem save = item("Save current as template…", this::saveTemplate);
        save.setEnabled(templates != null);
        menu.add(save);
        return menu;
    }

    private void saveTemplate() {
        Template captured = captureTemplate();
        if (captured == null) return;
        captured.settings = settings.encode();
        Template saved = SaveTemplateDialog.show(this, captured, state().title, templates, store);
        if (saved == null) return;
        templates.saveTemplate(saved);
        flash("Saved template “" + saved.name + "”", Notice.Kind.SUCCESS);
    }

    private void useTemplate(Template template) {
        if (template.settings != null) {
            Settings applied = Settings.decode(template.settings, settings);
            applied.zoom = settings.zoom;
            applied.openSections = settings.openSections;
            applied.lastSaveDir = settings.lastSaveDir;
            settings.copyFrom(applied);
            settings.save(store);
            reloadSettings();
        }
        Templates.Applied[] result = new Templates.Applied[1];
        edit(() -> result[0] = applyTemplate(template));
        syncTitleField();
        settingsPanel.syncOffsets();
        template.lastUsed = System.currentTimeMillis();
        templates.saveTemplate(template);
        List<String> missing = result[0].missing();
        if (missing.isEmpty()) flash("Applied “" + template.name + "”", Notice.Kind.SUCCESS);
        else flash("Applied “" + template.name + "”, not found: " + String.join(", ", missing), Notice.Kind.INFO);
    }

    private static JButton button(String label, Runnable action) {
        JButton b = new JButton(label);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void bindKeys() {
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        key(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu), () -> { if (history().undo()) afterHistory(); });
        key(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu | InputEvent.SHIFT_DOWN_MASK), () -> { if (history().redo()) afterHistory(); });
        key(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menu), () -> { if (history().redo()) afterHistory(); });
        key(KeyStroke.getKeyStroke(KeyEvent.VK_C, menu | InputEvent.SHIFT_DOWN_MASK), this::copyImage);
        key(KeyStroke.getKeyStroke(KeyEvent.VK_S, menu), this::saveImage);
        key(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0), () -> selectTool(Tool.MARK));
        key(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), () -> selectTool(Tool.REDACT));
    }

    private void key(KeyStroke stroke, Runnable action) {
        String name = stroke.toString();
        canvas.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
        canvas.getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) { action.run(); }
        });
        if (stroke.getModifiers() != 0) {
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
            getRootPane().getActionMap().put(name, canvas.getActionMap().get(name));
        }
    }

    private void selectTool(Tool value) {
        for (Component c : toolbar.getComponents()) {
            if (c instanceof JToggleButton b && b.getClientProperty("tool") == value) {
                b.setSelected(true);
                tool = value;
            }
        }
    }

    private void afterHistory() {
        syncTitleField();
        settingsPanel.syncOffsets();
        rebuild();
        changed();
    }

    private void copyImage() {
        try {
            ImageExport.copy(scene.toImage(settings.exportScale));
            flash("Copied to clipboard", Notice.Kind.SUCCESS);
            publish(true);
        } catch (IOException | IllegalStateException e) {
            flash("Copy failed: " + e.getMessage(), Notice.Kind.ERROR);
        }
    }

    private void saveImage() {
        if (ImageExport.save(this, scene.toImage(settings.exportScale), settings, fileName())) {
            settings.save(store);
            flash("Saved PNG", Notice.Kind.SUCCESS);
            publish(true);
        }
    }

    private void flash(String message, Notice.Kind kind) {
        if (canvasScroll.isShowing()) Notice.show(getLayeredPane(), Notice.areaOf(canvasScroll, getLayeredPane()), message, kind);
    }

    @Override
    public void click(Point2D imagePoint, int clickCount) {
        int callout = scene.calloutAt(imagePoint);
        if (callout >= 0) {
            if (clickCount >= 2) editNote(callout);
            return;
        }
        Point2D cp = scene.toContent(imagePoint);
        if (tool == Tool.REDACT) {
            redactClick(cp);
            return;
        }
        Anchor anchor = scene.content().anchorAt(cp);
        if (anchor == null) {
            int mark = scene.markAt(imagePoint);
            if (mark >= 0) editNote(mark);
            return;
        }
        for (int i = 0; i < state().marks.size(); i++) {
            if (state().marks.get(i).anchor.equals(anchor)) {
                editNote(i);
                return;
            }
        }
        addMark(anchor);
    }

    @Override
    public void drag(Point2D from, Point2D to) {
        Point2D a = scene.toContent(from), b = scene.toContent(to);
        if (tool == Tool.REDACT) {
            redactDrag(a, b);
            return;
        }
        Anchor anchor = scene.content().anchorBetween(a, b);
        if (anchor != null) addMark(anchor);
    }

    @Override
    public void calloutMoveStarted() {
        history().checkpoint();
    }

    @Override
    public void calloutMoved(int markIndex, Point2D offset) {
        state().marks.get(markIndex).calloutOffset = offset;
        rebuild();
        changed();
    }

    @Override
    public void popup(Point2D imagePoint, Point screenPoint) {
        JPopupMenu menu = new JPopupMenu();
        int index = scene.calloutAt(imagePoint);
        if (index < 0) index = scene.markAt(imagePoint);
        if (index >= 0) addMarkMenuItems(menu, index);
        addContentMenuItems(menu, scene.toContent(imagePoint));
        if (menu.getComponentCount() > 0) menu.show(canvas, screenPoint.x, screenPoint.y);
    }

    private void addMarkMenuItems(JPopupMenu menu, int index) {
        Mark mark = state().marks.get(index);
        menu.add(item("Edit note…", () -> editNote(index)));
        JMenu color = new JMenu("Color");
        for (int c = 0; c < Mark.PALETTE.length; c++) {
            int value = c;
            JMenuItem item = item(Mark.PALETTE_NAMES[c], () -> edit(() -> mark.color = value));
            item.setIcon(new Swatch(Mark.PALETTE[c]));
            color.add(item);
        }
        menu.add(color);
        if (mark.calloutOffset != null) menu.add(item("Reset note position", () -> edit(() -> mark.calloutOffset = null)));
        menu.add(item("Delete mark", () -> edit(() -> state().marks.remove(mark))));
        menu.addSeparator();
    }

    static JMenuItem item(String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        return item;
    }

    private void addMark(Anchor anchor) {
        Mark mark = new Mark(anchor, settings.markColor);
        if (NoteDialog.edit(this, mark)) edit(() -> state().marks.add(mark));
    }

    private void editNote(int index) {
        Mark copy = state().marks.get(index).copy();
        if (NoteDialog.edit(this, copy)) edit(() -> state().marks.set(index, copy));
    }
}
