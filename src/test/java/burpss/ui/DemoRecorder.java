package burpss.ui;

import burpss.Samples;
import burpss.core.Anchor;
import burpss.core.Exchange;
import burpss.core.HttpText;
import burpss.core.Mark;
import burpss.core.PayloadDiff;
import burpss.core.ResultRow;
import burpss.core.Settings;
import burpss.core.Token;
import burpss.render.Fonts;

import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class DemoRecorder {

    private static final int W = 1600, H = 900, FPS = 30, TITLE_H = 28;
    private static final double SCALE = 1.2;
    private static final int MENU_W = 260, ITEM_H = 26, SEPARATOR_H = 9;
    private static final String SEPARATOR = "-";
    private static final String INTRUDER_TEMPLATE = "POST /login HTTP/1.1\r\nHost: app.example.com\r\n"
            + "Content-Type: application/json\r\n\r\n{\"username\":\"§admin§\",\"password\":\"§admin§\"}";

    private static final class Win {
        final JFrame frame;
        final String title;
        final double x, y;
        double alpha = 1;

        Win(JFrame frame, String title, double x, double y) {
            this.frame = frame;
            this.title = title;
            this.x = x;
            this.y = y;
        }

        double width() { return frame.getRootPane().getWidth(); }
        double height() { return frame.getRootPane().getHeight() + TITLE_H; }
    }

    private static final class Menu {
        final double x, y;
        final List<String> items;

        Menu(double x, double y, List<String> items) {
            this.x = x;
            this.y = y;
            this.items = items;
        }

        double height() {
            return 8 + items.stream().mapToInt(i -> i.equals(SEPARATOR) ? SEPARATOR_H : ITEM_H).sum();
        }

        Rectangle2D item(String label) {
            double top = y + 4;
            for (String i : items) {
                int h = i.equals(SEPARATOR) ? SEPARATOR_H : ITEM_H;
                if (i.equals(label)) return new Rectangle2D.Double(x + 4, top, MENU_W - 8, h);
                top += h;
            }
            throw new IllegalArgumentException(label);
        }
    }

    private final OutputStream video;
    private final Process ffmpeg;
    private final BufferedImage frame = new BufferedImage((int) (W * SCALE), (int) (H * SCALE), BufferedImage.TYPE_3BYTE_BGR);
    private final Map<String, String> prefs = new HashMap<>();
    private final Settings.Store store = new Settings.Store() {
        public String get(String key) { return prefs.get(key); }
        public void set(String key, String value) { prefs.put(key, value); }
    };
    private final List<Win> windows = new ArrayList<>();
    private final List<Menu> menus = new ArrayList<>();
    private EditorWindow window;
    private Win snapshot;
    private Point2D cursor = new Point2D.Double(W * 0.55, H * 0.6);
    private String caption = "";
    private Point2D ripple;
    private double rippleAge = 1;
    private String toast;
    private Consumer<Graphics2D> card;

    private DemoRecorder(File output) throws IOException {
        ffmpeg = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "bgr24",
                "-s", frame.getWidth() + "x" + frame.getHeight(), "-r", String.valueOf(FPS), "-i", "-",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18", "-preset", "slow", "-movflags", "+faststart",
                output.getAbsolutePath())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        video = ffmpeg.getOutputStream();
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
                UIManager.put("Table.selectionInactiveBackground", new Color(0xFFD4C2));
                UIManager.put("Table.selectionInactiveForeground", Color.BLACK);
            } catch (Exception ignored) {
            }
        });
        File output = new File(args[0]);
        output.getParentFile().mkdirs();
        DemoRecorder demo = new DemoRecorder(output);
        demo.record();
        demo.video.close();
        System.exit(demo.ffmpeg.waitFor());
    }

    private void record() throws Exception {
        titleCard("Snapshot for Burp Suite", "Presentable, safely redacted evidence from any request", 3.0);

        JTable history = edt(this::historyTable);
        Win burp = edt(() -> new Win(MockBurp.frame(MockBurp.mainWindow("Proxy",
                MockBurp.proxyHistory(history, Samples.REQUEST, Samples.RESPONSE)), W, H - TITLE_H),
                "Burp Suite Professional  –  Temporary Project", 0, 0));
        windows.add(burp);

        caption = "Select a request anywhere in Burp: Proxy, Repeater, Logger, Target…";
        move(cell(history, 2, 3), 1.2);
        click();
        edt(() -> { history.setRowSelectionInterval(2, 2); return null; });
        hold(0.8);
        caption = "Right-click → Extensions → Snapshot → Snapshot request/response…";
        contextMenu(cell(history, 2, 3), "Snapshot request/response…", false);

        Exchange exchange = edt(() -> new Exchange(HttpText.parse(Samples.REQUEST, true), HttpText.parse(Samples.RESPONSE, false),
                "POST", "https://app.example.com/api/v2/users/1337/profile?debug=true&lang=en", "app.example.com", 200, "OK", 142));
        Settings settings = new Settings();
        settings.wrapColumns = 58;
        settings.showTime = true;
        openSnapshot(edt(() -> new ExchangeWindow(List.of(exchange), settings, store)), "Snapshot", 1480, 800);

        caption = "Sensitive headers and secrets are redacted automatically, last 4 chars kept";
        move(onImage(tokenValue(0, exchange.request, Token.Kind.COOKIE, "session")), 1.0);
        hold(0.9);
        move(onImage(tokenValue(1, exchange.response, Token.Kind.JSON_MEMBER, "apiToken")), 1.0);
        hold(1.4);

        caption = "Name the finding";
        JTextField title = edt(this::titleField);
        move(at(title), 0.9);
        click();
        typeInto(title, "IDOR + mass assignment: role escalated to admin", 1.6);
        hold(0.5);

        caption = "Click a parameter to box it, then describe it";
        markWithNote(exchange, 0, exchange.request, "role", 0, "Client-controlled role accepted by the API");
        clickButton(edt(() -> swatch(1)));
        markWithNote(exchange, 1, exchange.response, "email", 1, "Victim's PII returned for another user's ID");
        hold(0.9);

        caption = "Redact anything else with a click";
        clickButton(edt(() -> button("▒ Redact")));
        Point2D requestId = tokenValue(1, exchange.response, Token.Kind.HEADER, "X-Request-Id");
        move(onImage(requestId), 0.9);
        click();
        edt(() -> { window.click(requestId, 1); return null; });
        hold(1.1);

        caption = "Drag notes wherever they read best";
        dragCallout(0, 70, 40, 1.1);
        hold(0.6);

        caption = "Burp-matched light and dark themes";
        clickButton(edt(() -> button("Dark")));
        hold(1.6);

        caption = "Callouts or a numbered legend for reports";
        clickButton(edt(() -> button("Legend")));
        hold(1.8);
        clickButton(edt(() -> button("Callout")));
        hold(0.5);

        caption = "Tiled watermark with {host} and {date}";
        JTextField watermark = edt(this::watermarkField);
        move(at(watermark), 0.9);
        click();
        typeInto(watermark, "ACME PENTEST · {date}", 1.3);
        hold(1.4);

        caption = "Pinch or ⌘ + scroll to zoom";
        Point2D email = onImage(tokenValue(1, exchange.response, Token.Kind.JSON_MEMBER, "email"));
        move(email, 0.8);
        zoom(1.016, 30, email);
        hold(1.0);
        zoom(1 / 1.016, 30, email);
        hold(0.3);

        caption = "Copy to clipboard or save a crisp PNG";
        move(at(edt(() -> button("Copy to clipboard"))), 0.9);
        click();
        toast = "Copied to clipboard";
        hold(1.0);
        toast = null;
        BufferedImage exported = edt(() -> window.scene.toImage(2));
        caption = "";
        showImage(exported, 3.2);
        closeSnapshot();

        windows.remove(burp);
        windows.add(edt(() -> new Win(MockBurp.frame(MockBurp.mainWindow("Intruder",
                MockBurp.editor("Positions", INTRUDER_TEMPLATE, true)), W, H - TITLE_H),
                "Burp Suite Professional  –  Temporary Project", 0, 0)));
        JTable results = edt(this::resultsTable);
        Win attack = edt(() -> new Win(MockBurp.frame(MockBurp.attackResults(results), 1180, 560),
                "2. Intruder attack of https://app.example.com", 210, 120));
        windows.add(attack);
        caption = "Intruder: open the attack results";
        hold(1.4);
        caption = "Select the result rows (Shift-click for a range)";
        move(cell(results, 1, 1), 1.0);
        click();
        edt(() -> { results.setRowSelectionInterval(1, 1); return null; });
        move(cell(results, 5, 1), 0.9);
        click();
        edt(() -> { results.setRowSelectionInterval(1, 5); return null; });
        hold(0.6);
        caption = "Right-click → Extensions → Snapshot → Snapshot as results table…";
        contextMenu(cell(results, 4, 2), "Snapshot as results table…", true);

        prefs.put("burpss.zoom", "120%");
        openSnapshot(edt(this::resultsWindow), "Snapshot – results table", 1480, 780);
        caption = "Payloads are diffed automatically; mark the interesting row";
        Point2D row = onImage(rowCenter(5));
        move(row, 1.0);
        click();
        Mark mark = new Mark(new Anchor.Rows(5, 5), 3);
        mark.calloutOffset = new Point2D.Double(180, 50);
        edt(() -> { window.edit(() -> window.state().marks.add(mark)); return null; });
        typeNote(mark, "Only this credential pair redirects to the dashboard", 1.6);
        hold(2.2);
        closeSnapshot();

        caption = "";
        titleCard("Snapshot for Burp Suite", "Mark  ·  Redact  ·  Watermark  ·  Export", 3.0);
    }

    private JTable historyTable() {
        String[] columns = {"#", "Host", "Method", "URL", "Params", "Status code", "Length", "MIME type"};
        Object[][] rows = {
                {"41", "https://app.example.com", "GET", "/dashboard", "", "200", "4521", "HTML"},
                {"42", "https://app.example.com", "GET", "/api/v2/users/me", "", "200", "812", "JSON"},
                {"43", "https://app.example.com", "POST", "/api/v2/users/1337/profile?debug=true&lang=en", "✓", "200", "498", "JSON"},
                {"44", "https://app.example.com", "GET", "/api/v2/notifications", "", "200", "233", "JSON"},
                {"45", "https://app.example.com", "POST", "/login", "✓", "302", "1288", ""},
                {"46", "https://app.example.com", "GET", "/api/v2/orders?page=2", "✓", "200", "3307", "JSON"},
                {"47", "https://cdn.example.com", "GET", "/static/app.js", "", "200", "88213", "script"},
        };
        JTable table = MockBurp.table(columns, rows);
        int[] widths = {50, 220, 70, 460, 60, 100, 80, 90};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        return table;
    }

    private JTable resultsTable() {
        String[] columns = {"Request", "Payload 1", "Payload 2", "Status code", "Response received", "Error", "Timeout", "Length", "Comment"};
        Object[][] rows = {
                {"0", "", "", "401", "77", "", "", "512", ""},
                {"1", "admin", "admin", "401", "80", "", "", "512", ""},
                {"2", "root", "toor", "401", "93", "", "", "512", ""},
                {"3", "test", "test123", "401", "106", "", "", "512", ""},
                {"4", "guest", "guest", "401", "119", "", "", "512", ""},
                {"5", "administrator", "P@ssw0rd!", "302", "132", "", "", "1288", ""},
        };
        return MockBurp.table(columns, rows);
    }

    private TableWindow resultsWindow() {
        String[] users = {"admin", "root", "test", "guest", "administrator"};
        String[] passwords = {"admin", "toor", "test123", "guest", "P@ssw0rd!"};
        List<String> requests = new ArrayList<>();
        for (int i = 0; i < users.length; i++) {
            String body = "{\"username\":\"" + users[i] + "\",\"password\":\"" + passwords[i] + "\"}";
            requests.add("POST /login HTTP/1.1\r\nHost: app.example.com\r\nContent-Length: " + body.length() + "\r\n\r\n" + body);
        }
        List<String> payloads = PayloadDiff.labels(requests);
        int[] status = {401, 401, 401, 401, 302};
        int[] length = {512, 512, 512, 512, 1288};
        List<ResultRow> rows = new ArrayList<>();
        for (int i = 0; i < users.length; i++) {
            rows.add(new ResultRow(i + 1, payloads.get(i), status[i], length[i], 80 + i * 13,
                    status[i] == 302 ? "Location: /dashboard" : "Invalid credentials"));
        }
        Settings settings = new Settings();
        settings.load(store);
        settings.containsText = "dashboard";
        TableWindow t = new TableWindow(rows, "POST", "https://app.example.com/login", "app.example.com", settings, store);
        t.state().title = "Credential stuffing: valid admin password found";
        return t;
    }

    private void contextMenu(Point2D at, String action, boolean table) throws Exception {
        List<String> main = table
                ? List.of("Scan", "Send to Intruder", "Send to Repeater", "Send to Sequencer", "Send to Comparer (request)",
                "Send to Comparer (response)", "Show response in browser", SEPARATOR, "Extensions", SEPARATOR,
                "Add comment", "Highlight", "Copy URL", "Copy as curl command", "Save item")
                : List.of("Scan", "Do passive scan", "Do active scan", SEPARATOR, "Send to Intruder", "Send to Repeater",
                "Send to Sequencer", "Send to Comparer", "Send to Decoder", "Show response in browser", SEPARATOR,
                "Extensions", "Engagement tools", SEPARATOR, "Copy URL", "Copy as curl command", "Add notes", "Highlight");
        click();
        Menu root = new Menu(at.getX() + 4, Math.min(at.getY() + 2, H - 30 - 8 - main.size() * ITEM_H), main);
        menus.add(root);
        hold(0.5);
        Rectangle2D extensions = root.item("Extensions");
        move(new Point2D.Double(extensions.getX() + 60, extensions.getCenterY()), 0.7);
        Menu ext = new Menu(root.x + MENU_W - 6, extensions.getY() - 4, List.of("Snapshot"));
        menus.add(ext);
        hold(0.35);
        Rectangle2D snap = ext.item("Snapshot");
        move(new Point2D.Double(snap.getX() + 30, snap.getCenterY()), 0.5);
        move(new Point2D.Double(snap.getX() + 90, snap.getCenterY()), 0.25);
        List<String> items = table ? List.of("Snapshot request/response…", "Snapshot as results table…") : List.of("Snapshot request/response…");
        Menu sub = new Menu(ext.x + MENU_W - 6, snap.getY() - 4, items);
        menus.add(sub);
        hold(0.35);
        Rectangle2D target = sub.item(action);
        move(new Point2D.Double(target.getX() + 40, target.getCenterY()), 0.5);
        move(new Point2D.Double(target.getX() + 110, target.getCenterY()), 0.35);
        hold(0.4);
        click();
        menus.clear();
    }

    private void openSnapshot(EditorWindow w, String title, int width, int height) throws Exception {
        edt(() -> {
            window = w;
            w.addNotify();
            java.awt.Insets insets = w.getInsets();
            w.setSize(width + insets.left + insets.right, height + insets.top + insets.bottom);
            w.validate();
            w.syncTitleField();
            w.rebuild();
            w.validate();
            return null;
        });
        snapshot = new Win(w, title, (W - width) / 2.0, Math.max(10, (H - height - TITLE_H) / 2.0 - 8));
        snapshot.alpha = 0;
        windows.add(snapshot);
        for (int f = 1; f <= 8; f++) {
            snapshot.alpha = f / 8.0;
            emit();
        }
    }

    private void closeSnapshot() throws Exception {
        for (int f = 7; f >= 0; f--) {
            snapshot.alpha = f / 8.0;
            emit();
        }
        windows.remove(snapshot);
        edt(() -> { window.dispose(); return null; });
    }

    private void markWithNote(Exchange exchange, int pane, HttpText text, String name, int color, String note) throws Exception {
        Anchor anchor = Samples.tokenAnchor(pane, text, Token.Kind.JSON_MEMBER, name);
        Rectangle2D bounds = edt(() -> window.scene.contentRectInImage(window.scene.content().bounds(anchor)));
        move(onImage(new Point2D.Double(bounds.getCenterX(), bounds.getCenterY())), 0.9);
        click();
        Mark mark = new Mark(anchor, color);
        edt(() -> { window.edit(() -> exchange.state.marks.add(mark)); return null; });
        typeNote(mark, note, 1.4);
    }

    private void typeNote(Mark mark, String note, double seconds) throws Exception {
        int frames = (int) (seconds * FPS);
        for (int f = 1; f <= frames; f++) {
            String partial = note.substring(0, note.length() * f / frames);
            edt(() -> { mark.note = partial; window.rebuild(); return null; });
            emit();
        }
    }

    private void typeInto(JTextField field, String text, double seconds) throws Exception {
        int frames = (int) (seconds * FPS);
        for (int f = 1; f <= frames; f++) {
            String partial = text.substring(0, text.length() * f / frames);
            edt(() -> { field.setText(partial); return null; });
            emit();
        }
        Thread.sleep(450);
    }

    private void dragCallout(int index, double dx, double dy, double seconds) throws Exception {
        Point2D start = edt(() -> window.scene.calloutOffset(index));
        Rectangle2D callout = edt(() -> calloutRect(index));
        Point2D grab = onImage(new Point2D.Double(callout.getCenterX(), callout.getCenterY()));
        move(grab, 0.8);
        edt(() -> { window.calloutMoveStarted(); return null; });
        double zoom = edt(this::canvasZoom);
        int frames = (int) (seconds * FPS);
        for (int f = 1; f <= frames; f++) {
            double t = ease(f / (double) frames);
            Point2D offset = new Point2D.Double(start.getX() + dx * t, start.getY() + dy * t);
            edt(() -> { window.calloutMoved(index, offset); return null; });
            cursor = new Point2D.Double(grab.getX() + dx * t * zoom, grab.getY() + dy * t * zoom);
            emit();
        }
    }

    private Rectangle2D calloutRect(int index) throws ReflectiveOperationException {
        Field f = window.scene.getClass().getDeclaredField("callouts");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Rectangle2D> callouts = (List<Rectangle2D>) f.get(window.scene);
        Rectangle2D r = callouts.get(index);
        Point2D topLeft = window.scene.toScene(new Point2D.Double(0, 0));
        return new Rectangle2D.Double(r.getX() - topLeft.getX(), r.getY() - topLeft.getY(), r.getWidth(), r.getHeight());
    }

    private void zoom(double factor, int frames, Point2D at) throws Exception {
        Point canvasPoint = edt(() -> SwingUtilities.convertPoint(window.getRootPane(),
                (int) (at.getX() - snapshot.x), (int) (at.getY() - snapshot.y - TITLE_H), window.canvas));
        for (int f = 0; f < frames; f++) {
            edt(() -> {
                Method zoomBy = Canvas.class.getDeclaredMethod("zoomBy", double.class, Point.class);
                zoomBy.setAccessible(true);
                zoomBy.invoke(window.canvas, factor, canvasPoint);
                return null;
            });
            emit();
        }
    }

    private void clickButton(AbstractButton b) throws Exception {
        move(at(b), 0.8);
        click();
        edt(() -> { b.doClick(); return null; });
    }

    private void move(Point2D target, double seconds) throws Exception {
        Point2D from = cursor;
        int frames = Math.max(1, (int) (seconds * FPS));
        for (int f = 1; f <= frames; f++) {
            double t = ease(f / (double) frames);
            cursor = new Point2D.Double(from.getX() + (target.getX() - from.getX()) * t, from.getY() + (target.getY() - from.getY()) * t);
            emit();
        }
    }

    private void click() throws Exception {
        ripple = cursor;
        rippleAge = 0;
        for (int f = 0; f < 6; f++) emit();
    }

    private void hold(double seconds) throws Exception {
        for (int f = 0; f < (int) (seconds * FPS); f++) emit();
    }

    private static double ease(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    private void emit() throws Exception {
        edt(() -> {
            Graphics2D g = frame.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.scale(SCALE, SCALE);
            if (card != null) {
                card.accept(g);
            } else {
                g.setColor(new Color(0x2B2B2B));
                g.fillRect(0, 0, W, H);
                for (Win w : windows) paintWindow(g, w);
                for (Menu m : menus) paintMenu(g, m);
                paintOverlay(g);
            }
            g.dispose();
            return null;
        });
        video.write(((DataBufferByte) frame.getRaster().getDataBuffer()).getData());
        if (rippleAge < 1) rippleAge += 0.1;
    }

    private void paintWindow(Graphics2D g0, Win w) {
        if (w.alpha <= 0) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) w.alpha));
        boolean full = w.x == 0 && w.y == 0;
        Shape outline = full ? new Rectangle2D.Double(0, 0, w.width(), w.height())
                : new RoundRectangle2D.Double(w.x, w.y, w.width(), w.height(), 20, 20);
        if (!full) {
            for (int i = 1; i <= 14; i++) {
                g.setColor(new Color(0, 0, 0, 7));
                g.fill(new RoundRectangle2D.Double(w.x - i, w.y - i + 10, w.width() + 2 * i, w.height() + 2 * i, 20 + 2 * i, 20 + 2 * i));
            }
        }
        g.clip(outline);
        g.setColor(new Color(0xECECEC));
        g.fill(new Rectangle2D.Double(w.x, w.y, w.width(), TITLE_H));
        g.setColor(new Color(0xD0D0D0));
        g.fill(new Rectangle2D.Double(w.x, w.y + TITLE_H - 1, w.width(), 1));
        Color[] lights = {new Color(0xFF5F57), new Color(0xFEBC2E), new Color(0x28C840)};
        for (int i = 0; i < 3; i++) {
            g.setColor(lights[i]);
            g.fill(new Ellipse2D.Double(w.x + 12 + i * 20, w.y + 8, 12, 12));
        }
        Font font = Fonts.sans(13, true);
        g.setFont(font);
        g.setColor(new Color(0x4B4B4B));
        g.drawString(w.title, (float) (w.x + (w.width() - Fonts.width(font, w.title)) / 2), (float) (w.y + 19));
        g.translate(w.x, w.y + TITLE_H);
        w.frame.getRootPane().printAll(g);
        g.dispose();
    }

    private void paintMenu(Graphics2D g, Menu m) {
        RoundRectangle2D box = new RoundRectangle2D.Double(m.x, m.y, MENU_W, m.height(), 10, 10);
        for (int i = 1; i <= 8; i++) {
            g.setColor(new Color(0, 0, 0, 8));
            g.fill(new RoundRectangle2D.Double(m.x - i, m.y - i + 4, MENU_W + 2 * i, m.height() + 2 * i, 10 + 2 * i, 10 + 2 * i));
        }
        g.setColor(Color.WHITE);
        g.fill(box);
        g.setColor(new Color(0xC9C9C9));
        g.draw(box);
        Font font = MockBurp.menuFont();
        g.setFont(font);
        double top = m.y + 4;
        for (String item : m.items) {
            if (item.equals(SEPARATOR)) {
                g.setColor(new Color(0xE2E2E2));
                g.fill(new Rectangle2D.Double(m.x + 10, top + SEPARATOR_H / 2.0, MENU_W - 20, 1));
                top += SEPARATOR_H;
                continue;
            }
            Rectangle2D r = new Rectangle2D.Double(m.x + 4, top, MENU_W - 8, ITEM_H);
            boolean hover = r.contains(cursor) || isOpenParent(m, item);
            if (hover) {
                g.setColor(MockBurp.ORANGE);
                g.fill(new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 6, 6));
            }
            g.setColor(hover ? Color.WHITE : new Color(0x1A1A1A));
            g.drawString(item, (float) (r.getX() + 12), (float) (r.getCenterY() + Fonts.ascent(font) / 2 - 1));
            if (item.equals("Extensions") || item.equals("Snapshot") || item.equals("Highlight") || item.equals("Engagement tools")) {
                g.drawString("›", (float) (r.getMaxX() - 18), (float) (r.getCenterY() + Fonts.ascent(font) / 2 - 1));
            }
            top += ITEM_H;
        }
    }

    private boolean isOpenParent(Menu m, String item) {
        int index = menus.indexOf(m);
        return index >= 0 && index < menus.size() - 1 && (item.equals("Extensions") || item.equals("Snapshot"));
    }

    private void paintOverlay(Graphics2D g) {
        if (rippleAge < 0.6 && ripple != null) {
            double r = 8 + rippleAge * 50;
            g.setColor(new Color(255, 102, 51, (int) (180 * (1 - rippleAge / 0.6))));
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Ellipse2D.Double(ripple.getX() - r, ripple.getY() - r, 2 * r, 2 * r));
        }
        if (toast != null) pill(g, "✓  " + toast, W / 2.0, 90, new Color(0x30A46C), Color.WHITE, 16);
        if (!caption.isEmpty()) pill(g, caption, W / 2.0, H - 56, new Color(20, 20, 22, 225), Color.WHITE, 18);
        paintCursor(g);
    }

    private void paintCursor(Graphics2D g) {
        AffineTransform saved = g.getTransform();
        g.translate(cursor.getX(), cursor.getY());
        Polygon arrow = new Polygon(new int[]{0, 0, 4, 7, 9, 6, 11}, new int[]{0, 16, 12, 18, 17, 11, 11}, 7);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0, 0, 0, 60));
        g.translate(1, 1.5);
        g.fill(arrow);
        g.translate(-1, -1.5);
        g.setColor(Color.BLACK);
        g.fill(arrow);
        g.setColor(Color.WHITE);
        g.draw(arrow);
        g.setTransform(saved);
    }

    private static void pill(Graphics2D g, String text, double cx, double cy, Color bg, Color fg, float size) {
        Font font = Fonts.sans(size, true);
        double w = Fonts.width(font, text) + 40, h = size * 2.4;
        g.setColor(bg);
        g.fill(new RoundRectangle2D.Double(cx - w / 2, cy - h / 2, w, h, h, h));
        g.setColor(fg);
        g.setFont(font);
        g.drawString(text, (float) (cx - w / 2 + 20), (float) (cy + Fonts.ascent(font) / 2 - 1));
    }

    private void titleCard(String title, String subtitle, double seconds) throws Exception {
        int frames = (int) (seconds * FPS);
        for (int f = 0; f < frames; f++) {
            double fade = Math.min(1, Math.min(f, frames - f) / 12.0);
            card = g -> {
                g.setPaint(new GradientPaint(0, 0, new Color(0x2B2B2B), W, H, new Color(0x4A2A1C)));
                g.fillRect(0, 0, W, H);
                Font big = Fonts.sans(58, true), small = Fonts.sans(24, false);
                g.setColor(withAlpha(Color.WHITE, fade));
                g.setFont(big);
                g.drawString(title, (float) ((W - Fonts.width(big, title)) / 2), H / 2f - 10);
                g.setColor(withAlpha(MockBurp.ORANGE, fade));
                g.fill(new RoundRectangle2D.Double(W / 2.0 - 40, H / 2.0 + 16, 80, 4, 4, 4));
                g.setFont(small);
                g.setColor(withAlpha(new Color(0xCECECE), fade));
                g.drawString(subtitle, (float) ((W - Fonts.width(small, subtitle)) / 2), H / 2f + 66);
            };
            emit();
        }
        card = null;
    }

    private void showImage(BufferedImage image, double seconds) throws Exception {
        int frames = (int) (seconds * FPS);
        for (int f = 0; f < frames; f++) {
            double fade = Math.min(1, f / 10.0);
            card = g -> {
                g.setColor(new Color(0x1E1F22));
                g.fillRect(0, 0, W, H);
                double s = Math.min((W - 120.0) / image.getWidth(), (H - 160.0) / image.getHeight());
                double w = image.getWidth() * s, h = image.getHeight() * s;
                double x = (W - w) / 2, y = (H - h) / 2 - 20;
                g.setColor(new Color(0, 0, 0, 90));
                g.fill(new RoundRectangle2D.Double(x + 4, y + 8, w, h, 10, 10));
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) fade));
                g.drawImage(image, (int) x, (int) y, (int) w, (int) h, null);
                g.setComposite(AlphaComposite.SrcOver);
                pill(g, "The exported PNG", W / 2.0, H - 50, new Color(255, 102, 51, 230), Color.WHITE, 16);
            };
            emit();
        }
        card = null;
    }

    private static Color withAlpha(Color c, double alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (255 * Math.max(0, Math.min(1, alpha))));
    }

    private Point2D tokenValue(int pane, HttpText text, Token.Kind kind, String name) throws Exception {
        Token t = text.tokens().stream().filter(k -> k.kind() == kind && k.name().equals(name)).findFirst().orElseThrow();
        Anchor anchor = new Anchor.Text(pane, t.valueStart(), t.valueEnd());
        Rectangle2D r = edt(() -> window.scene.contentRectInImage(window.scene.content().bounds(anchor)));
        return new Point2D.Double(Math.min(r.getCenterX(), r.getX() + 120), r.getCenterY());
    }

    private Point2D rowCenter(int number) throws Exception {
        Rectangle2D r = edt(() -> window.scene.contentRectInImage(window.scene.content().bounds(new Anchor.Rows(number, number))));
        return new Point2D.Double(r.getX() + 160, r.getCenterY());
    }

    private Point2D onImage(Point2D imagePoint) throws Exception {
        return edt(() -> {
            Method originX = Canvas.class.getDeclaredMethod("originX");
            originX.setAccessible(true);
            double zoom = canvasZoom();
            double x = (double) originX.invoke(window.canvas) + imagePoint.getX() * zoom;
            double y = 24 + imagePoint.getY() * zoom;
            return point(window.canvas, x, y);
        });
    }

    private double canvasZoom() throws ReflectiveOperationException {
        Field zoom = Canvas.class.getDeclaredField("zoom");
        zoom.setAccessible(true);
        return (double) zoom.get(window.canvas);
    }

    private Point2D at(Component c) throws Exception {
        return edt(() -> point(c, c.getWidth() / 2.0, c.getHeight() / 2.0));
    }

    private Point2D cell(JTable table, int row, int column) throws Exception {
        return edt(() -> {
            Rectangle2D r = table.getCellRect(row, column, true);
            return point(table, r.getX() + Math.min(60, r.getWidth() / 2), r.getCenterY());
        });
    }

    private Point2D point(Component c, double x, double y) {
        Win win = windows.stream().filter(w -> SwingUtilities.isDescendingFrom(c, w.frame)).findFirst().orElseThrow();
        Point p = SwingUtilities.convertPoint(c, (int) x, (int) y, win.frame.getRootPane());
        return new Point2D.Double(win.x + p.x, win.y + TITLE_H + p.y);
    }

    private AbstractButton button(String text) {
        return find(window.getRootPane(), AbstractButton.class, b -> text.equals(b.getText()));
    }

    private AbstractButton swatch(int index) {
        return find(window.getRootPane(), AbstractButton.class, b -> Mark.PALETTE_NAMES[index].equals(b.getToolTipText()));
    }

    private JTextField titleField() {
        return find(window.getRootPane(), JTextField.class, f -> f.getColumns() == 40);
    }

    private JTextField watermarkField() {
        return find(window.getRootPane(), JTextField.class, f -> "Supports {date} and {host}".equals(f.getToolTipText()));
    }

    private static <T> T find(Container root, Class<T> type, Predicate<T> test) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && test.test(type.cast(c))) return type.cast(c);
            if (c instanceof Container child) {
                T found = find(child, type, test);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T edt(Callable<T> task) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Exception e) {
                error.set(e);
            }
        });
        if (error.get() != null) throw error.get();
        return result.get();
    }
}
