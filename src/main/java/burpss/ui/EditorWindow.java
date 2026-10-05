package burpss.ui;

import burpss.core.Anchor;
import burpss.core.EditState;
import burpss.core.History;
import burpss.core.Mark;
import burpss.core.Settings;
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
import javax.swing.JFrame;
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
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;

public abstract class EditorWindow extends JFrame implements Canvas.Handler {

    enum Tool { MARK, REDACT }

    protected final Settings settings;
    private final Settings.Store store;
    protected final Canvas canvas = new Canvas(this);
    private final SettingsPanel settingsPanel;
    private final JTextField titleField = new JTextField(40);
    private final JComboBox<String> zoom = new JComboBox<>(new String[]{"Fit", "50%", "75%", "100%", "150%", "200%"});
    private final JLabel status = new JLabel(" ");
    protected final JToolBar toolbar = new JToolBar();
    protected Tool tool = Tool.MARK;
    protected Scene scene;
    private String logoPath;
    private Image logo;
    private boolean syncingTitle;
    private boolean syncingZoom;

    EditorWindow(String title, Settings settings, Settings.Store store, boolean table) {
        super(title);
        this.settings = settings;
        this.store = store;
        this.settingsPanel = new SettingsPanel(settings, table, this::settingsChanged);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildToolbar(!table);

        JScrollPane canvasScroll = new JScrollPane(canvas, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
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
        JScrollPane sidebar = settingsPanel.scrollable();
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UIManager.getColor("Separator.foreground")));
        getContentPane().add(canvasScroll, BorderLayout.CENTER);
        getContentPane().add(sidebar, BorderLayout.EAST);
        getContentPane().add(buildFooter(), BorderLayout.SOUTH);
        bindKeys();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1400, screen.width - 40), Math.min(860, screen.height - 40));
    }

    abstract EditState state();

    abstract History history();

    abstract Content buildContent(Theme theme);

    abstract HeaderInfo header();

    abstract String fileName();

    void redactClick(Point2D contentPoint) {
    }

    void redactDrag(Point2D from, Point2D to) {
    }

    void addContentMenuItems(JPopupMenu menu, Point2D contentPoint) {
    }

    public void open(Component parent) {
        syncTitleField();
        setLocationRelativeTo(parent);
        setVisible(true);
        rebuild();
    }

    void rebuild() {
        scene = new Scene(buildContent(Theme.of(settings)), header(), state().marks, settings, logo());
        canvas.setScene(scene);
    }

    void edit(Runnable mutation) {
        history().checkpoint();
        mutation.run();
        rebuild();
    }

    void syncTitleField() {
        syncingTitle = true;
        titleField.setText(state().title);
        syncingTitle = false;
    }

    private void settingsChanged() {
        settings.save(store);
        rebuild();
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
            flash("Could not read logo: " + e.getMessage());
        }
        return logo;
    }

    private void buildToolbar(boolean redaction) {
        toolbar.setFloatable(false);
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

    private JComponent buildFooter() {
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.add(new JLabel("Title"));
        left.add(titleField);
        Timer debounce = new Timer(300, e -> edit(() -> state().title = titleField.getText().strip()));
        debounce.setRepeats(false);
        titleField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { if (!syncingTitle) debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { if (!syncingTitle) debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { }
        });
        left.add(status);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(button("Copy to clipboard", this::copyImage));
        right.add(button("Save PNG…", this::saveImage));

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        footer.add(left, BorderLayout.CENTER);
        footer.add(right, BorderLayout.EAST);
        return footer;
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
        rebuild();
    }

    private void copyImage() {
        try {
            ImageExport.copy(scene.toImage(settings.exportScale));
            flash("Copied to clipboard");
        } catch (IOException | IllegalStateException e) {
            flash("Copy failed: " + e.getMessage());
        }
    }

    private void saveImage() {
        if (ImageExport.save(this, scene.toImage(settings.exportScale), settings, fileName())) {
            settings.save(store);
            flash("Saved");
        }
    }

    private void flash(String message) {
        status.setText(message);
        Timer clear = new Timer(2500, e -> status.setText(" "));
        clear.setRepeats(false);
        clear.start();
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
