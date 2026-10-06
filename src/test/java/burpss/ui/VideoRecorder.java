package burpss.ui;

import burpss.ai.AiMarkup;
import burpss.core.Anchor;
import burpss.core.EditState;
import burpss.core.Exchange;
import burpss.core.HttpText;
import burpss.core.Mark;
import burpss.core.ResultRow;
import burpss.core.Settings;
import burpss.render.ExchangeContent;
import burpss.render.HeaderInfo;
import burpss.render.Scene;
import burpss.render.TableContent;
import burpss.render.Theme;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;

public final class VideoRecorder {

    private static final int W = 1920, H = 1080, FPS = 30;
    private static final Color ORANGE = new Color(0xFF6633), PURPLE = new Color(0x8B5CF6), PINK = new Color(0xD16BF5),
            GREEN = new Color(0x2EA043), BLUE = new Color(0x2F81F7), INK = new Color(0xF5F7FA), MUTED = new Color(0x9AA4B2),
            CARD = new Color(0x1B1F2A);

    interface Painter {
        void paint(Graphics2D g, double t, double d);
    }

    private record Shot(double seconds, double speed, Color glow, Painter painter) {
    }

    private final BufferedImage frame = new BufferedImage(W, H, BufferedImage.TYPE_3BYTE_BGR);
    private final BufferedImage layer = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
    private final Process ffmpeg;
    private final OutputStream video;
    private final Font display = new Font("Avenir Next", Font.BOLD, 72);
    private final Font body = new Font("Avenir Next", Font.PLAIN, 30);
    private final Font mono = new Font("Menlo", Font.PLAIN, 19);
    private final Font keys;
    private double clock;
    private boolean still;

    private BufferedImage[] states;
    private Rectangle[] changes;
    private BufferedImage finished, dialog, aiButton, toast, captionCopied, sse;
    private BufferedImage light, stacked, showcase, solid, dots, table, library;
    private Rectangle[] boxes;
    private Rectangle dialogText, dialogGenerate, dialogCancel;

    private VideoRecorder(String output) throws Exception {
        Font sans = new Font("SansSerif", Font.BOLD, 58);
        keys = new Font("Avenir Next", Font.BOLD, 58).canDisplay('⌘') ? new Font("Avenir Next", Font.BOLD, 58) : sans;
        ffmpeg = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "bgr24",
                "-s", W + "x" + H, "-r", String.valueOf(FPS), "-i", "-",
                "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart", output)
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start();
        video = ffmpeg.getOutputStream();
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf");
        new java.io.File(args[1]).getParentFile().mkdirs();
        VideoRecorder promo = new VideoRecorder(args[1]);
        SwingUtilities.invokeAndWait(() -> {
            try {
                promo.prepare();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        promo.still = args[0].endsWith("still");
        if (args[0].startsWith("full")) promo.full();
        else promo.whatsNew();
        promo.video.close();
        System.exit(promo.ffmpeg.waitFor());
    }

    private void prepare() throws Exception {
        String request = "GET /api/projects/1337 HTTP/1.1\r\nHost: app.example.com\r\n"
                + "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.dXNlci1iLXNlc3Npb24tdG9rZW4.k3Jd9Qx\r\nAccept: application/json\r\n\r\n";
        String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n"
                + "{\"id\":1337,\"name\":\"Q3 Roadmap\",\"owner\":{\"id\":1001,\"email\":\"alice@example.com\"},\"members\":4}";
        String reply = "{\"title\":\"IDOR on GET /api/projects/{id}\",\"caption\":\"User B's session retrieved project 1337, "
                + "which belongs to user A, including user A's email address.\",\"marks\":["
                + "{\"pane\":\"request\",\"target\":{\"header\":\"Authorization\"},\"note\":\"User B's session\"},"
                + "{\"pane\":\"request\",\"target\":{\"text\":\"/projects/1337\"},\"note\":\"User A's project ID\"},"
                + "{\"pane\":\"response\",\"target\":{\"json\":\"owner.email\"},\"note\":\"User A's email leaked\"}]}";
        Settings settings = dark();
        Exchange probe = exchange(request, response, 200, "OK", 184);
        AiMarkup.Result result = AiMarkup.exchangeResult(reply, probe, new ExchangeContent(probe, settings, Theme.of(settings)), 0);
        int[] palette = {0, 4, 3};
        states = new BufferedImage[4];
        for (int k = 0; k <= 3; k++) {
            Exchange e = exchange(request, response, 200, "OK", 184);
            for (int i = 0; i < k; i++) {
                Mark m = result.marks().get(i).copy();
                m.color = palette[i];
                e.state.marks.add(m);
            }
            states[k] = render(e, settings, 2);
            if (k == 3) {
                e.state.title = result.title();
                e.state.caption = result.caption();
                finished = render(e, settings, 2);
            }
        }
        int w = 0, h = 0;
        for (BufferedImage s : states) {
            w = Math.max(w, s.getWidth());
            h = Math.max(h, s.getHeight());
        }
        changes = new Rectangle[4];
        for (int k = 0; k < 4; k++) {
            states[k] = pad(states[k], w, h);
            if (k > 0) changes[k] = diff(states[k - 1], states[k]);
        }

        String sseRequest = "GET /v1/chat/stream HTTP/1.1\r\nHost: api.example.com\r\nAccept: text/event-stream\r\n\r\n";
        String sseResponse = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n"
                + "event: delta\ndata: {\"id\":\"evt_1\",\"delta\":{\"text\":\"Hello\"},\"index\":0}\n\n"
                + "event: delta\ndata: {\"id\":\"evt_2\",\"delta\":{\"text\":\" world\"},\"session\":\"s3ss10n-9f8e7d6c5b4a\"}\n\n"
                + "data: [DONE]\n\n";
        sse = render(exchange(sseRequest, sseResponse, 200, "OK", 412), dark(), 2);

        JFrame owner = new JFrame();
        AiDialog ai = new AiDialog(owner, "", false, null, "", c -> "", (r, c, x) -> null);
        ai.pack();
        layout(ai.getRootPane());
        JComponent textScroll = (JComponent) ((JTextArea) field(ai, "context")).getParent().getParent();
        JButton generate = (JButton) field(ai, "generate");
        dialogText = SwingUtilities.convertRectangle(textScroll.getParent(), textScroll.getBounds(), ai.getRootPane());
        dialogGenerate = SwingUtilities.convertRectangle(generate.getParent(), generate.getBounds(), ai.getRootPane());
        JButton cancel = (JButton) java.util.Arrays.stream(generate.getParent().getComponents())
                .filter(b -> b instanceof JButton j && j.getText().equals("Cancel")).findFirst().orElseThrow();
        dialogCancel = SwingUtilities.convertRectangle(cancel.getParent(), cancel.getBounds(), ai.getRootPane());
        dialog = capture(ai.getRootPane(), 2);

        JPanel holder = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        holder.setBackground(new Color(0x2B2D30));
        holder.setOpaque(false);
        AiButton button = new AiButton("AI markup");
        holder.add(button);
        holder.setSize(holder.getPreferredSize());
        layout(holder);
        aiButton = capture(holder, 5);

        Toast t = new Toast(owner, states[0], "Snapshot copied", "GET /api/projects/1337", () -> { });
        t.pack();
        layout(t.getRootPane());
        toast = capture(t.getRootPane(), 2);

        captionCopied = notice("Caption copied", Notice.Kind.SUCCESS);

        boxes = new Rectangle[4];
        for (int k = 1; k <= 3; k++) {
            Exchange e = exchange(request, response, 200, "OK", 184);
            for (int i = 0; i < k; i++) {
                Mark m = result.marks().get(i).copy();
                m.color = palette[i];
                if (i == k - 1) m.note = "";
                e.state.marks.add(m);
            }
            boxes[k] = diff(states[k - 1], pad(render(e, settings, 2), w, h));
        }
        Settings blur = dark();
        blur.redactStyle = Settings.RedactStyle.SOLID;
        solid = pad(render(exchange(request, response, 200, "OK", 184), blur, 2), w, h);
        blur.redactStyle = Settings.RedactStyle.DOTS;
        dots = pad(render(exchange(request, response, 200, "OK", 184), blur, 2), w, h);

        Settings lightSettings = new Settings();
        light = render(marked(request, response, result, palette), lightSettings, 2);
        Settings stackedSettings = dark();
        stackedSettings.layout = Settings.Layout.STACKED;
        stacked = render(marked(request, response, result, palette), stackedSettings, 2);
        Settings showcaseSettings = new Settings();
        showcaseSettings.frame = Settings.Frame.SHOWCASE;
        showcase = render(marked(request, response, result, palette), showcaseSettings, 2);

        List<ResultRow> rows = List.of(
                new ResultRow(1, "1335", 403, 188, 41, "{\"error\":\"forbidden\"}"),
                new ResultRow(2, "1336", 200, 512, 63, "{\"owner\":{\"email\":\"carol@example.com\"}}"),
                new ResultRow(3, "1337", 200, 498, 58, "{\"owner\":{\"email\":\"alice@example.com\"}}"),
                new ResultRow(4, "1338", 403, 188, 40, "{\"error\":\"forbidden\"}"),
                new ResultRow(5, "1339", 404, 96, 37, "{\"error\":\"not found\"}"),
                new ResultRow(6, "1340", 403, 188, 39, "{\"error\":\"forbidden\"}"));
        EditState tableState = new EditState();
        tableState.title = "Project ID enumeration";
        Mark rowMark = new Mark(new Anchor.Rows(2, 3), 2);
        rowMark.note = "Other users' projects readable";
        tableState.marks.add(rowMark);
        Settings tableSettings = dark();
        tableSettings.containsText = "email";
        tableSettings.showTime = true;
        tableSettings.notes = Settings.Notes.LEGEND;
        table = new Scene(new TableContent(rows, tableState, tableSettings, Theme.of(tableSettings)),
                new HeaderInfo(tableState.title, "", "GET", "https://app.example.com/api/projects/§1335§", "app.example.com", 0, "", -1),
                tableState.marks, tableSettings, null).toImage(2);

        long now = System.currentTimeMillis();
        List<SnapshotsTab.Entry> entries = List.of(
                new SnapshotsTab.Entry("1", "IDOR on GET /api/projects/{id}", "GET https://app.example.com/api/projects/1337", now - 40_000, false),
                new SnapshotsTab.Entry("2", "", "POST https://app.example.com/api/projects/1337/share", now - 600_000, false),
                new SnapshotsTab.Entry("3", "Project ID enumeration", "Results table · GET https://app.example.com/api/projects/1335", now - 3_600_000, false),
                new SnapshotsTab.Entry("4", "Missing rate limit on login", "POST https://app.example.com/login", now - 86_400_000, true));
        BufferedImage preview = finished;
        SnapshotsTab tab = new SnapshotsTab(new SnapshotsTab.Source() {
            public List<SnapshotsTab.Entry> entries() { return entries; }
            public BufferedImage preview(String id) { return preview; }
            public void open(String id) { }
            public void delete(String id) { }
        }, () -> { });
        JFrame frameHost = new JFrame();
        frameHost.setContentPane(tab);
        frameHost.pack();
        frameHost.setSize(1100, 560);
        tab.setSize(1100, 560);
        tab.refresh();
        layout(tab);
        library = capture(tab, 2);
    }

    private static Exchange marked(String request, String response, AiMarkup.Result result, int[] palette) {
        Exchange e = exchange(request, response, 200, "OK", 184);
        for (int i = 0; i < 3; i++) {
            Mark m = result.marks().get(i).copy();
            m.color = palette[i];
            e.state.marks.add(m);
        }
        e.state.title = result.title();
        return e;
    }

    private void whatsNew() throws Exception {
        play(List.of(new Shot(1.8, 1.1, ORANGE, this::intro), new Shot(3.2, 1.3, ORANGE, this::quickCopy),
                new Shot(1.8, 1.2, PURPLE, this::aiIntro), new Shot(2.4, 1.5, PURPLE, this::aiDialog),
                new Shot(3.4, 1.4, PURPLE, this::aiMarks), new Shot(2.0, 1.3, GREEN, this::caption),
                new Shot(2.4, 1.1, BLUE, this::sseScene), new Shot(2.8, 1.2, ORANGE, this::outro)), 0);
    }

    private void full() throws Exception {
        play(List.of(new Shot(2.6, 1, ORANGE, this::fullIntro), new Shot(3.2, 1, ORANGE, this::open),
                new Shot(3.4, 1, BLUE, this::marking), new Shot(2.8, 1, GREEN, this::redaction),
                new Shot(3.6, 1, PURPLE, this::aiFull), new Shot(2.8, 1, BLUE, this::looks),
                new Shot(2.6, 1, ORANGE, this::intruder), new Shot(2.8, 1, GREEN, this::export),
                new Shot(2.4, 1, BLUE, this::drafts), new Shot(2.8, 1.2, ORANGE, this::outro)), 0.5);
    }

    private void intro(Graphics2D g, double t, double d) {
        double p = seg(t, 0.1, 0.8);
        sparkles(g, W / 2.0, H / 2.0 - 40, 440, t, ORANGE, 18, p, 0.5);
        Paint brand = new GradientPaint(W / 2f - 400, 0, INK, W / 2f + 400, 0, new Color(0xFFB199));
        words(g, "Snapshot", display.deriveFont(170f), W / 2.0, H / 2.0 + 10, brand, t, 0.1, true);
        words(g, "for Burp Suite", body.deriveFont(38f), W / 2.0, H / 2.0 + 90, MUTED, t, 0.55, true);
        chip(g, "WHAT'S NEW IN 1.2 + 1.3", ORANGE, W / 2.0, H / 2.0 + 190, seg(t, 1.2, 1.6), true);
    }


    private void quickCopy(Graphics2D g, double t, double d) {
        heading(g, t, "NEW IN 1.2", ORANGE, "Quick copy", "Select a request and press the keys. The PNG lands on your clipboard.");
        double in = easeOutCubic(seg(t, 0.15, 0.7));
        double press = seg(t, 1.9, 2.15);
        proxyTable(g, 150 - (1 - in) * 300, 360, 1000, alphaOf(in), seg(t, 2.1, 2.6));
        double keysIn = seg(t, 0.9, 1.4);
        double down = press > 0 && press < 1 ? Math.sin(press * Math.PI) : 0;
        String[] labels = {"⌘", "⇧", "C"};
        for (int i = 0; i < 3; i++) keycap(g, labels[i], 1330 + i * 150, 520, 124, down, seg(t, 0.9 + i * 0.1, 1.4 + i * 0.1));
        if (keysIn > 0) {
            g.setFont(body.deriveFont(24f));
            alpha(g, keysIn, () -> center(g, "Ctrl + Shift + C on Windows and Linux", 1480, 640, MUTED));
        }
        double toastIn = easeOutBack(seg(t, 2.3, 2.75));
        if (toastIn > 0) {
            double tw = 640, th = tw * toast.getHeight() / toast.getWidth();
            double x = W - tw - 90 + (1 - toastIn) * 420, y = H - th - 90;
            glow(g, new Rectangle2D.Double(x, y, tw, th), GREEN, 0.5 * seg(t, 2.4, 3.2));
            draw(g, toast, x, y, tw, th, 1);
        }
        if (t > 2.15) ripple(g, 560, 512, seg(t, 2.15, 2.9), ORANGE);
    }


    private void aiIntro(Graphics2D g, double t, double d) {
        double p = seg(t, 0.0, 0.6);
        sparkles(g, W / 2.0, H / 2.0 - 80, 360, t, PURPLE, 22, p, 0.32);
        double s = easeOutBack(seg(t, 0.05, 0.55));
        if (s > 0) {
            double w = 520 * s, h = w * aiButton.getHeight() / aiButton.getWidth();
            double x = W / 2.0 - w / 2, y = H / 2.0 - 80 - h / 2;
            glow(g, new Rectangle2D.Double(x + 10, y + 10, w - 20, h - 20), PURPLE, 0.6 + 0.2 * Math.sin(t * 6));
            draw(g, aiButton, x, y, w, h, alphaOf(seg(t, 0.05, 0.3)));
        }
        Paint grad = new GradientPaint(W / 2f - 300, 0, new Color(0xC4A7FF), W / 2f + 300, 0, PINK);
        words(g, "Describe the finding.", display.deriveFont(70f), W / 2.0, H / 2.0 + 110, INK, t, 0.3, true, 0.04);
        words(g, "Burp AI marks the evidence.", display.deriveFont(70f), W / 2.0, H / 2.0 + 200, grad, t, 0.65, true, 0.04);
    }

    private void aiDialog(Graphics2D g, double t, double d) {
        heading(g, t, "NEW IN 1.3", PURPLE, "AI markup", "Burp AI sees what the image shows. Redacted values stay hidden.");
        double pop = easeOutBack(seg(t, 0.1, 0.55));
        double w = 1100 * (0.85 + 0.15 * pop), h = w * dialog.getHeight() / dialog.getWidth();
        double x = W / 2.0 - w / 2, y = 610 - h / 2 + (1 - pop) * 40;
        double k = w / (dialog.getWidth() / 2.0);
        shadow(g, x, y, w, h);
        draw(g, dialog, x, y, w, h, alphaOf(seg(t, 0.1, 0.3)));
        String context = "IDOR: user B reads user A's project by changing the project ID";
        int typed = (int) (context.length() * seg(t, 0.7, 2.2));
        Graphics2D c = (Graphics2D) g.create();
        c.setFont(burpss.render.Fonts.sans(13, false).deriveFont((float) (13 * k)));
        c.setColor(new Color(0xDFE1E5));
        double tx = x + (dialogText.x + 10) * k, ty = y + (dialogText.y + 22) * k;
        String shown = context.substring(0, typed);
        c.drawString(shown, (float) tx, (float) ty);
        if ((int) (t * 3) % 2 == 0 && t < 2.6) {
            double cx = tx + c.getFontMetrics().stringWidth(shown) + 2;
            c.fill(new Rectangle2D.Double(cx, ty - 13 * k, 2, 16 * k));
        }
        c.dispose();
        Rectangle2D gen = new Rectangle2D.Double(x + dialogGenerate.x * k, y + dialogGenerate.y * k, dialogGenerate.width * k, dialogGenerate.height * k);
        if (t > 2.5) ripple(g, gen.getCenterX(), gen.getCenterY(), seg(t, 2.5, 3.2), PURPLE);
        if (t > 2.7) {
            double bar = seg(t, 2.7, 3.6);
            double left = x + 16 * k, right = x + (dialogCancel.x - 12) * k;
            Rectangle2D track = new Rectangle2D.Double(left, gen.getCenterY() - 3, right - left, 6);
            g.setColor(new Color(255, 255, 255, 30));
            g.fill(new RoundRectangle2D.Double(track.getX(), track.getY(), track.getWidth(), 6, 6, 6));
            g.setPaint(new GradientPaint((float) track.getX(), 0, PURPLE, (float) track.getMaxX(), 0, PINK));
            g.fill(new RoundRectangle2D.Double(track.getX(), track.getY(), track.getWidth() * easeOutCubic(bar), 6, 6, 6));
        }
    }

    private void aiMarks(Graphics2D g, double t, double d) {
        heading(g, t, "NEW IN 1.3", PURPLE, "Boxes, notes, title and caption", "One click. Everything stays editable and one undo removes it.");
        double[] at = {0.5, 1.4, 2.3};
        int k = 0;
        for (int i = 0; i < 3; i++) if (t >= at[i]) k = i + 1;
        boolean done = t >= 3.6;
        BufferedImage img = done ? finished : states[k];
        double maxW = 1640, maxH = 760;
        double scale = Math.min(maxW / img.getWidth(), maxH / img.getHeight());
        double punch = done ? 1 + 0.05 * (1 - easeOutCubic(seg(t, 3.6, 3.95))) : 1;
        double w = img.getWidth() * scale * punch, h = img.getHeight() * scale * punch;
        double x = W / 2.0 - w / 2, y = 640 - h / 2;
        double in = easeOutCubic(seg(t, 0, 0.4));
        shadow(g, x, y + (1 - in) * 60, w, h);
        draw(g, img, x, y + (1 - in) * 60, w, h, alphaOf(in));
        if (k > 0 && !done) {
            double p = seg(t, at[k - 1], at[k - 1] + 0.7);
            Rectangle r = changes[k];
            Rectangle2D box = new Rectangle2D.Double(x + r.x * scale, y + r.y * scale, r.width * scale, r.height * scale);
            glow(g, box, Mark.PALETTE[new int[]{0, 4, 3}[k - 1]], 0.9 * (1 - p));
        }
        if (done) {
            double flash = 1 - seg(t, 3.6, 3.9);
            g.setColor(new Color(255, 255, 255, (int) (60 * flash)));
            g.fillRect(0, 0, W, H);
            chip(g, "TITLE + CAPTION FILLED IN", GREEN, W - 330, 130, seg(t, 3.9, 4.3), true);
        }
    }

    private void caption(Graphics2D g, double t, double d) {
        heading(g, t, "NEW IN 1.3", GREEN, "Evidence captions", "Drawn under the screenshot, and one click away as text for your report.");
        double zoom = 1 + 0.55 * easeInOut(seg(t, 0.3, 1.6));
        viewport(g, finished, new Rectangle2D.Double(160, 330, 1600, 620), zoom, 0.0, 1.0, 1);
        double n = easeOutBack(seg(t, 1.9, 2.3));
        if (n > 0) {
            double w = 440, h = w * captionCopied.getHeight() / captionCopied.getWidth();
            draw(g, captionCopied, W / 2.0 - w / 2, 975 + (1 - n) * 60, w, h, alphaOf(seg(t, 1.9, 2.1)));
        }
    }

    private void sseScene(Graphics2D g, double t, double d) {
        heading(g, t, "NEW IN 1.3", BLUE, "Streaming responses, pretty-printed", "Every JSON data: line in a server-sent event stream gets formatted.");
        double in = easeOutCubic(seg(t, 0.1, 0.6));
        rawStream(g, 90 - (1 - in) * 200, 370, 720, alphaOf(in));
        double arrow = seg(t, 0.9, 1.3);
        if (arrow > 0) {
            g.setPaint(new GradientPaint(860, 0, new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 60), 960, 0, BLUE));
            g.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double len = 100 * easeOutCubic(arrow);
            g.draw(new java.awt.geom.Line2D.Double(840, 640, 840 + len * 0.8, 640));
            Path2D head = new Path2D.Double();
            head.moveTo(840 + len * 0.8 - 22, 618);
            head.lineTo(840 + len * 0.8, 640);
            head.lineTo(840 + len * 0.8 - 22, 662);
            g.draw(head);
        }
        double pop = easeOutBack(seg(t, 1.2, 1.7));
        if (pop > 0) {
            double maxW = 940, maxH = 700;
            double scale = Math.min(maxW / sse.getWidth(), maxH / sse.getHeight()) * (0.85 + 0.15 * pop);
            double w = sse.getWidth() * scale, h = sse.getHeight() * scale;
            double x = 1410 - w / 2, y = 660 - h / 2;
            shadow(g, x, y, w, h);
            draw(g, sse, x, y, w, h, alphaOf(seg(t, 1.2, 1.4)));
        }
    }



    private void outro(Graphics2D g, double t, double d) {
        sparkles(g, W / 2.0, H / 2.0 - 90, 440, t, ORANGE, 16, seg(t, 0, 0.6), 0.42);
        Paint brand = new GradientPaint(W / 2f - 400, 0, INK, W / 2f + 400, 0, new Color(0xFFB199));
        words(g, "Snapshot", display.deriveFont(150f), W / 2.0, H / 2.0 - 20, brand, t, 0, true);
        chip(g, "v1.3.0", ORANGE, W / 2.0, H / 2.0 + 70, seg(t, 0.4, 0.8), true);
        words(g, "github.com/noverdy/burp-snapshot", body.deriveFont(Font.BOLD, 40f), W / 2.0, H / 2.0 + 190, INK, t, 0.5, true);
        words(g, "Free · Burp Suite Pro and Community", body.deriveFont(26f), W / 2.0, H / 2.0 + 250, MUTED, t, 0.7, true, 0.03);
        double fade = seg(t, d - 0.5, d);
        if (fade > 0) {
            g.setColor(new Color(0, 0, 0, (int) (255 * fade)));
            g.fillRect(-200, -200, W + 400, H + 400);
        }
    }

    private void fullIntro(Graphics2D g, double t, double d) {
        sparkles(g, W / 2.0, H / 2.0 - 60, 440, t, ORANGE, 18, seg(t, 0.1, 0.8), 0.5);
        Paint brand = new GradientPaint(W / 2f - 400, 0, INK, W / 2f + 400, 0, new Color(0xFFB199));
        words(g, "Snapshot", display.deriveFont(170f), W / 2.0, H / 2.0 - 10, brand, t, 0.1, true);
        words(g, "Report-ready screenshots from Burp Suite", body.deriveFont(40f), W / 2.0, H / 2.0 + 75, MUTED, t, 0.45, true, 0.04);
        String[] tags = {"MARK", "REDACT", "ANNOTATE", "EXPORT"};
        Color[] colors = {BLUE, GREEN, PURPLE, ORANGE};
        for (int i = 0; i < tags.length; i++) chip(g, tags[i], colors[i], W / 2.0 + (i - 1.5) * 210, H / 2.0 + 170, seg(t, 0.9 + i * 0.1, 1.3 + i * 0.1), true);
    }

    private void open(Graphics2D g, double t, double d) {
        heading(g, t, "OPEN", ORANGE, "Right-click any request", "Proxy, Repeater, Logger, Intruder and more. Or press ⌘⇧X.");
        double in = easeOutCubic(seg(t, 0.05, 0.5));
        double away = easeInOut(seg(t, 1.7, 2.2));
        Graphics2D c = (Graphics2D) g.create();
        c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1 - 0.65 * away)));
        proxyTable(c, 160 - (1 - in) * 200, 370, 940, alphaOf(in), 0);
        Point2D click = contextMenu(c, 620, 590, seg(t, 0.55, 0.8), seg(t, 0.95, 1.15), t >= 1.35);
        c.dispose();
        if (t >= 1.35) ripple(g, click.getX(), click.getY(), seg(t, 1.35, 1.9), ORANGE);
        double pop = seg(t, 1.6, 2.3);
        if (pop > 0) {
            double e = easeOutBack(pop);
            BufferedImage img = states[0];
            double scale = Math.min(1500.0 / img.getWidth(), 640.0 / img.getHeight());
            double w = img.getWidth() * scale, h = img.getHeight() * scale;
            double cx = click.getX() + (W / 2.0 - click.getX()) * easeOutCubic(pop), cy = click.getY() + (660 - click.getY()) * easeOutCubic(pop);
            double s = 0.15 + 0.85 * e;
            shadow(g, cx - w * s / 2, cy - h * s / 2, w * s, h * s);
            draw(g, img, cx - w * s / 2, cy - h * s / 2, w * s, h * s, alphaOf(pop));
        }
    }

    private Point2D contextMenu(Graphics2D g, double x, double y, double open, double sub, boolean pressed) {
        if (open <= 0) return new Point2D.Double(x, y);
        String[] main = {"Send to Repeater", "Send to Intruder", "Copy URL", "Extensions"};
        String[] keys = {"⌘R", "⌘I", "", "›"};
        double w = 330, ih = 46, e = easeOutCubic(open);
        Graphics2D c = (Graphics2D) g.create();
        c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alphaOf(open)));
        c.translate(x, y);
        c.scale(0.92 + 0.08 * e, 0.92 + 0.08 * e);
        shadow(c, 0, 0, w, ih * main.length + 16);
        c.setColor(new Color(0x2A2F3B));
        c.fill(new RoundRectangle2D.Double(0, 0, w, ih * main.length + 16, 14, 14));
        c.setFont(body.deriveFont(24f));
        for (int i = 0; i < main.length; i++) {
            double iy = 8 + i * ih;
            if (i == 3 && sub > 0) {
                c.setColor(new Color(0x2F81F7).darker());
                c.fill(new RoundRectangle2D.Double(6, iy, w - 12, ih, 10, 10));
            }
            c.setColor(INK);
            c.drawString(main[i], 22, (float) (iy + 31));
            c.setColor(MUTED);
            c.drawString(keys[i], (float) (w - 22 - c.getFontMetrics().stringWidth(keys[i])), (float) (iy + 31));
        }
        c.dispose();
        Point2D click = new Point2D.Double(x + w + 200, y + 8 + 3 * ih + ih / 2);
        if (sub <= 0) return click;
        String[] items = {"Snapshot request/response…", "Snapshot as results table…"};
        double sw = 400, sx = x + w - 6, sy = y + 8 + 3 * ih - 8;
        Graphics2D m = (Graphics2D) g.create();
        m.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alphaOf(sub)));
        m.translate((1 - easeOutCubic(sub)) * -20, 0);
        shadow(m, sx, sy, sw, ih * 2 + 16);
        m.setColor(new Color(0x2A2F3B));
        m.fill(new RoundRectangle2D.Double(sx, sy, sw, ih * 2 + 16, 14, 14));
        m.setFont(body.deriveFont(24f));
        for (int i = 0; i < 2; i++) {
            double iy = sy + 8 + i * ih;
            if (i == 0) {
                m.setColor(pressed ? ORANGE : new Color(0x2F81F7).darker());
                m.fill(new RoundRectangle2D.Double(sx + 6, iy, sw - 12, ih, 10, 10));
            }
            m.setColor(INK);
            m.drawString(items[i], (float) (sx + 22), (float) (iy + 31));
        }
        m.dispose();
        return new Point2D.Double(sx + sw / 2, sy + 8 + ih / 2);
    }

    private void marking(Graphics2D g, double t, double d) {
        heading(g, t, "MARK", BLUE, "Click to box it, type a note", "Parameters, headers, cookies and JSON keys box the whole key and value.");
        double[] clicks = {0.7, 1.5, 2.3};
        int k = 0;
        for (int i = 0; i < 3; i++) if (t >= clicks[i] + 0.12) k = i + 1;
        BufferedImage img = states[k];
        double scale = Math.min(1640.0 / img.getWidth(), 700.0 / img.getHeight());
        double w = img.getWidth() * scale, h = img.getHeight() * scale, x = W / 2.0 - w / 2, y = 655 - h / 2;
        double in = easeOutCubic(seg(t, 0, 0.4));
        shadow(g, x, y, w, h);
        draw(g, img, x, y + (1 - in) * 40, w, h, alphaOf(in));
        if (k > 0) {
            Rectangle r = boxes[k];
            glow(g, new Rectangle2D.Double(x + r.x * scale, y + r.y * scale, r.width * scale, r.height * scale),
                    Mark.PALETTE[new int[]{0, 4, 3}[k - 1]], 0.9 * (1 - seg(t, clicks[k - 1] + 0.12, clicks[k - 1] + 0.8)));
        }
        Point2D from = new Point2D.Double(W * 0.62, H - 80);
        Point2D cursor = from;
        for (int i = 0; i < 3; i++) {
            Rectangle r = boxes[i + 1];
            Point2D to = new Point2D.Double(x + (r.x + r.width * 0.3) * scale, y + (r.y + r.height / 2.0) * scale);
            double start = i == 0 ? 0.25 : clicks[i - 1] + 0.15;
            double p = easeInOut(seg(t, start, clicks[i] - 0.05));
            if (t >= start) cursor = new Point2D.Double(from.getX() + (to.getX() - from.getX()) * p, from.getY() + (to.getY() - from.getY()) * p);
            if (t >= clicks[i]) {
                ripple(g, to.getX(), to.getY(), seg(t, clicks[i], clicks[i] + 0.5), Mark.PALETTE[new int[]{0, 4, 3}[i]]);
                from = to;
            }
        }
        pointer(g, cursor.getX(), cursor.getY(), alphaOf(seg(t, 0.2, 0.4)));
    }

    private void redaction(Graphics2D g, double t, double d) {
        heading(g, t, "REDACT", GREEN, "Secrets blurred automatically", "Auth headers, cookies and tokens. The last 4 characters stay so you can tell them apart.");
        BufferedImage img = t < 1.35 ? states[0] : t < 2.0 ? solid : dots;
        Rectangle r = boxes[1];
        double fx = (r.x + r.width * 0.4) / (double) img.getWidth(), fy = (r.y + r.height / 2.0) / img.getHeight();
        double zoom = 1 + 0.75 * easeInOut(seg(t, 0.25, 1.1));
        viewport(g, img, new Rectangle2D.Double(160, 340, 1600, 580), zoom, fx, fy, alphaOf(seg(t, 0, 0.3)));
        String[] styles = {"Blur", "Solid", "Dots"};
        int active = t < 1.35 ? 0 : t < 2.0 ? 1 : 2;
        for (int i = 0; i < 3; i++) chip(g, styles[i], i == active ? GREEN : MUTED, W / 2.0 + (i - 1) * 170, 985, seg(t, 0.5 + i * 0.08, 0.9 + i * 0.08), true);
    }

    private void aiFull(Graphics2D g, double t, double d) {
        heading(g, t, "AI MARKUP · PRO", PURPLE, "Describe the finding. Burp AI marks it.", "Notes, title and evidence caption filled in. Redacted values stay hidden.");
        double out = easeInOut(seg(t, 1.55, 1.95));
        if (out < 1) {
            Graphics2D c = (Graphics2D) g.create();
            c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1 - out)));
            double pop = easeOutBack(seg(t, 0.1, 0.5));
            double w = 1000 * (0.85 + 0.15 * pop) * (1 - 0.1 * out), h = w * dialog.getHeight() / dialog.getWidth();
            double x = W / 2.0 - w / 2, y = 640 - h / 2;
            double k = w / (dialog.getWidth() / 2.0);
            shadow(c, x, y, w, h);
            draw(c, dialog, x, y, w, h, alphaOf(seg(t, 0.1, 0.3)));
            String context = "IDOR: user B reads user A's project";
            String shown = context.substring(0, (int) (context.length() * seg(t, 0.35, 1.05)));
            c.setFont(burpss.render.Fonts.sans(13, false).deriveFont((float) (13 * k)));
            c.setColor(new Color(0xDFE1E5));
            c.drawString(shown, (float) (x + (dialogText.x + 10) * k), (float) (y + (dialogText.y + 22) * k));
            Rectangle2D gen = new Rectangle2D.Double(x + dialogGenerate.x * k, y + dialogGenerate.y * k, dialogGenerate.width * k, dialogGenerate.height * k);
            if (t > 1.15) ripple(c, gen.getCenterX(), gen.getCenterY(), seg(t, 1.15, 1.7), PURPLE);
            if (t > 1.2) {
                double left = x + 16 * k, right = x + (dialogCancel.x - 12) * k;
                c.setColor(new Color(255, 255, 255, 30));
                c.fill(new RoundRectangle2D.Double(left, gen.getCenterY() - 3, right - left, 6, 6, 6));
                c.setPaint(new GradientPaint((float) left, 0, PURPLE, (float) right, 0, PINK));
                c.fill(new RoundRectangle2D.Double(left, gen.getCenterY() - 3, (right - left) * easeOutCubic(seg(t, 1.2, 1.6)), 6, 6, 6));
            }
            c.dispose();
        }
        double reveal = seg(t, 1.6, 2.1);
        if (reveal > 0) {
            double scale = Math.min(1640.0 / finished.getWidth(), 700.0 / finished.getHeight()) * (0.94 + 0.06 * easeOutBack(reveal));
            double w = finished.getWidth() * scale, h = finished.getHeight() * scale;
            double x = W / 2.0 - w / 2, y = 660 - h / 2;
            shadow(g, x, y, w, h);
            draw(g, finished, x, y, w, h, alphaOf(reveal));
            glow(g, new Rectangle2D.Double(x, y, w, h), PURPLE, 0.7 * (1 - seg(t, 1.8, 2.8)));
        }
        double bw = 300, bh = bw * aiButton.getHeight() / aiButton.getWidth();
        draw(g, aiButton, W - 160 - bw, 190, bw, bh, alphaOf(seg(t, 0.15, 0.4)));
    }

    private void looks(Graphics2D g, double t, double d) {
        heading(g, t, "STYLE", BLUE, "Burp's own colors, your layout", "Light or dark, side by side or stacked, framed, with a tiled watermark.");
        BufferedImage[] cards = {light, stacked, showcase};
        String[] labels = {"Light", "Dark · stacked", "Showcase frame"};
        double[] angles = {-4, 0, 4};
        for (int i = 0; i < 3; i++) {
            double p = seg(t, 0.2 + i * 0.18, 0.75 + i * 0.18);
            if (p <= 0) continue;
            double e = easeOutBack(p);
            BufferedImage img = cards[i];
            double scale = Math.min(560.0 / img.getWidth(), 560.0 / img.getHeight());
            double w = img.getWidth() * scale, h = img.getHeight() * scale;
            double cx = 400 + i * 560, cy = 660 + (1 - e) * 160;
            Graphics2D c = (Graphics2D) g.create();
            c.rotate(Math.toRadians(angles[i] * e), cx, cy);
            shadow(c, cx - w / 2, cy - h / 2, w, h);
            draw(c, img, cx - w / 2, cy - h / 2, w, h, alphaOf(p));
            c.dispose();
            chip(g, labels[i], BLUE, cx, 990, seg(t, 0.6 + i * 0.18, 1.0 + i * 0.18), true);
        }
    }

    private void intruder(Graphics2D g, double t, double d) {
        heading(g, t, "INTRUDER", ORANGE, "Attack results as a table", "Payloads worked out for you, a grep column, and rows you can highlight.");
        double p = easeOutBack(seg(t, 0.15, 0.65));
        double scale = Math.min(1500.0 / table.getWidth(), 640.0 / table.getHeight()) * (0.9 + 0.1 * p);
        double w = table.getWidth() * scale, h = table.getHeight() * scale;
        double x = W / 2.0 - w / 2, y = 660 - h / 2 + (1 - p) * 40;
        shadow(g, x, y, w, h);
        draw(g, table, x, y, w, h, alphaOf(seg(t, 0.15, 0.4)));
    }

    private void export(Graphics2D g, double t, double d) {
        heading(g, t, "EXPORT", GREEN, "Copy, save or quick copy", "PNG at 1x, 2x or 3x. Press ⌘⇧C anywhere in Burp for an instant copy.");
        double p = easeOutCubic(seg(t, 0.1, 0.5));
        double scale = Math.min(1000.0 / finished.getWidth(), 600.0 / finished.getHeight());
        double w = finished.getWidth() * scale, h = finished.getHeight() * scale;
        double x = 130 - (1 - p) * 120, y = 660 - h / 2;
        shadow(g, x, y, w, h);
        draw(g, finished, x, y, w, h, alphaOf(p));
        String[] scales = {"1x", "2x", "3x"};
        int active = Math.min(2, (int) Math.max(0, (t - 0.5) / 0.35));
        for (int i = 0; i < 3; i++) chip(g, scales[i], i == active ? GREEN : MUTED, 1440 + (i - 1) * 120, 440, seg(t, 0.3 + i * 0.08, 0.7 + i * 0.08), true);
        double press = seg(t, 1.5, 1.75);
        double down = press > 0 && press < 1 ? Math.sin(press * Math.PI) : 0;
        String[] labels = {"⌘", "⇧", "C"};
        for (int i = 0; i < 3; i++) keycap(g, labels[i], 1310 + i * 130, 610, 108, down, seg(t, 0.7 + i * 0.08, 1.15 + i * 0.08));
        double toastIn = easeOutBack(seg(t, 1.85, 2.3));
        if (toastIn > 0) {
            double tw = 560, th = tw * toast.getHeight() / toast.getWidth();
            double tx = W - tw - 90 + (1 - toastIn) * 380, ty = 800;
            glow(g, new Rectangle2D.Double(tx, ty, tw, th), GREEN, 0.5 * seg(t, 1.9, 2.6));
            draw(g, toast, tx, ty, tw, th, 1);
        }
    }

    private void drafts(Graphics2D g, double t, double d) {
        heading(g, t, "LIBRARY", BLUE, "Drafts and exported, in your project", "Snapshot saves as you edit. Reopen and change anything later.");
        double p = easeOutBack(seg(t, 0.15, 0.65));
        double scale = Math.min(1500.0 / library.getWidth(), 640.0 / library.getHeight()) * (0.9 + 0.1 * p);
        double w = library.getWidth() * scale, h = library.getHeight() * scale;
        double x = W / 2.0 - w / 2, y = 665 - h / 2 + (1 - p) * 40;
        shadow(g, x, y, w, h);
        Graphics2D c = (Graphics2D) g.create();
        c.clip(new RoundRectangle2D.Double(x, y, w, h, 18, 18));
        draw(c, library, x, y, w, h, alphaOf(seg(t, 0.15, 0.4)));
        c.dispose();
    }

    private static void pointer(Graphics2D g, double x, double y, double alpha) {
        if (alpha <= 0) return;
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(0, 0);
        arrow.lineTo(0, 34);
        arrow.lineTo(9, 26);
        arrow.lineTo(16, 41);
        arrow.lineTo(22, 38);
        arrow.lineTo(15, 24);
        arrow.lineTo(27, 24);
        arrow.closePath();
        Graphics2D c = (Graphics2D) g.create();
        c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        c.translate(x, y);
        c.setColor(new Color(0, 0, 0, 90));
        c.translate(2, 3);
        c.fill(arrow);
        c.translate(-2, -3);
        c.setColor(Color.WHITE);
        c.fill(arrow);
        c.setColor(Color.BLACK);
        c.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        c.draw(arrow);
        c.dispose();
    }

    private void play(List<Shot> shots, double overlap) throws Exception {
        double[] start = new double[shots.size()];
        for (int i = 1; i < shots.size(); i++) start[i] = start[i - 1] + shots.get(i - 1).seconds() - overlap;
        double total = start[shots.size() - 1] + shots.get(shots.size() - 1).seconds();
        int frames = (int) Math.round(total * FPS);
        for (int f = 0; f < frames; f++) {
            double now = f / (double) FPS;
            int i = 0;
            while (i + 1 < shots.size() && now >= start[i + 1] - 1e-9) i++;
            Shot shot = shots.get(i);
            double t = now - start[i];
            boolean blending = overlap > 0 && i > 0 && t < overlap;
            double mix = blending ? easeInOut(t / overlap) : 1;
            Graphics2D g = frame.createGraphics();
            hints(g);
            background(g, blending ? blend(shots.get(i - 1).glow(), shot.glow(), mix) : shot.glow());
            if (overlap > 0) {
                if (blending) composite(g, shots.get(i - 1), now - start[i - 1], 1 - mix, 1 + 0.05 * mix, -28 * mix);
                composite(g, shot, t, mix, blending ? 0.95 + 0.05 * mix : 1, blending ? 28 * (1 - mix) : 0);
            } else {
                double punch = 1 + 0.06 * (1 - easeOutCubic(seg(t, 0, 0.22)));
                double drift = 1 + 0.018 * t / shot.seconds();
                g.translate(W / 2.0, H / 2.0);
                g.scale(punch * drift, punch * drift);
                g.translate(-W / 2.0, -H / 2.0);
                shot.painter().paint(g, t * shot.speed(), shot.seconds() * shot.speed());
            }
            g.dispose();
            int local = (int) Math.round(t * FPS);
            if (overlap == 0 && local < 2) flash(0.18 * (2 - local));
            video.write(((DataBufferByte) frame.getRaster().getDataBuffer()).getData());
            clock += 1.0 / FPS;
        }
    }

    private void composite(Graphics2D g, Shot shot, double t, double alpha, double scale, double dy) {
        Graphics2D l = layer.createGraphics();
        l.setComposite(AlphaComposite.Clear);
        l.fillRect(0, 0, W, H);
        l.setComposite(AlphaComposite.SrcOver);
        hints(l);
        double drift = still ? 1 : 1 + 0.015 * t / shot.seconds();
        l.translate(W / 2.0, H / 2.0);
        l.scale(drift, drift);
        l.translate(-W / 2.0, -H / 2.0);
        shot.painter().paint(l, t * shot.speed(), shot.seconds() * shot.speed());
        l.dispose();
        Graphics2D c = (Graphics2D) g.create();
        c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, alpha))));
        c.translate(W / 2.0, H / 2.0 + dy);
        c.scale(scale, scale);
        c.translate(-W / 2.0, -H / 2.0);
        c.drawImage(layer, 0, 0, null);
        c.dispose();
    }

    private static Color blend(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private void flash(double amount) {
        Graphics2D g = frame.createGraphics();
        g.setColor(new Color(255, 255, 255, (int) (255 * amount)));
        g.fillRect(0, 0, W, H);
        g.dispose();
    }

    private void background(Graphics2D g, Color glow) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x0A0D13), 0, H, new Color(0x131726)));
        g.fillRect(0, 0, W, H);
        double a = still ? 0 : clock * 0.35;
        blob(g, W * (0.22 + 0.06 * Math.sin(a)), H * (0.25 + 0.05 * Math.cos(a * 1.3)), 900, glow, 70);
        blob(g, W * (0.82 + 0.05 * Math.cos(a * 0.8)), H * (0.8 + 0.06 * Math.sin(a)), 1000, PURPLE, 45);
        g.setColor(new Color(255, 255, 255, 9));
        for (int x = 40; x < W; x += 48) for (int y = 40; y < H; y += 48) g.fillRect(x, y, 2, 2);
    }

    private static void blob(Graphics2D g, double x, double y, double r, Color c, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) r, new float[]{0f, 1f},
                new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Rectangle2D.Double(x - r, y - r, 2 * r, 2 * r));
    }

    private void heading(Graphics2D g, double t, String tag, Color color, String title, String sub) {
        chip(g, tag, color, 160, 130, seg(t, 0, 0.25), false);
        words(g, title, display.deriveFont(66f), 160, 235, INK, t, 0.05, false);
        words(g, sub, body.deriveFont(28f), 160, 290, MUTED, t, 0.2, false, 0.012);
    }

    private void words(Graphics2D g, String text, Font font, double x, double y, Paint paint, double t, double start, boolean centered) {
        words(g, text, font, x, y, paint, t, start, centered, 0.06);
    }

    private void words(Graphics2D g, String text, Font font, double x, double y, Paint paint, double t, double start, boolean centered, double stagger) {
        String[] parts = text.split(" ");
        FontMetrics fm = g.getFontMetrics(font);
        double space = fm.stringWidth(" ");
        double total = -space;
        for (String w : parts) total += fm.stringWidth(w) + space;
        double cx = centered ? x - total / 2 : x;
        for (int i = 0; i < parts.length; i++) {
            double ww = fm.stringWidth(parts[i]);
            double p = seg(t, start + i * stagger, start + i * stagger + 0.42);
            if (p > 0) {
                double e = easeOutBack(p);
                Graphics2D w = (Graphics2D) g.create();
                w.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alphaOf(p)));
                w.translate(cx + ww / 2, y + (1 - e) * 34);
                double s = 0.75 + 0.25 * e;
                w.scale(s, s);
                w.setFont(font);
                w.setPaint(paint instanceof GradientPaint gp ? shift(gp, -(cx + ww / 2)) : paint);
                w.drawString(parts[i], (float) (-ww / 2), 0f);
                w.dispose();
            }
            cx += ww + space;
        }
    }

    private static GradientPaint shift(GradientPaint gp, double dx) {
        return new GradientPaint((float) (gp.getPoint1().getX() + dx), 0, gp.getColor1(), (float) (gp.getPoint2().getX() + dx), 0, gp.getColor2());
    }


    private void chip(Graphics2D g, String text, Color color, double x, double y, double p, boolean centered) {
        if (p <= 0) return;
        Font f = body.deriveFont(Font.BOLD, 22f);
        FontMetrics fm = g.getFontMetrics(f);
        double w = fm.stringWidth(text) + 40, h = 42;
        double left = centered ? x - w / 2 : x;
        double e = easeOutBack(p);
        Graphics2D c = (Graphics2D) g.create();
        c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alphaOf(p)));
        c.translate(left + w / 2, y);
        c.scale(0.7 + 0.3 * e, 0.7 + 0.3 * e);
        c.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
        c.fill(new RoundRectangle2D.Double(-w / 2, -h / 2, w, h, h, h));
        c.setColor(color);
        c.setStroke(new BasicStroke(1.6f));
        c.draw(new RoundRectangle2D.Double(-w / 2, -h / 2, w, h, h, h));
        c.setFont(f);
        c.drawString(text, (float) (-w / 2 + 20), (float) (fm.getAscent() / 2.0 - 3));
        c.dispose();
    }

    private void keycap(Graphics2D g, String label, double cx, double cy, double size, double down, double appear) {
        if (appear <= 0) return;
        double e = easeOutBack(appear);
        double press = down * 10;
        Graphics2D k = (Graphics2D) g.create();
        k.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alphaOf(appear)));
        k.translate(cx, cy - (1 - e) * 80);
        double s = size / 2;
        k.setColor(new Color(0x0C0F15));
        k.fill(new RoundRectangle2D.Double(-s, -s + 14, size, size, 28, 28));
        k.setPaint(new GradientPaint(0, (float) (-s + press), new Color(0x3A4152), 0, (float) (s + press), new Color(0x252A36)));
        k.fill(new RoundRectangle2D.Double(-s, -s + press, size, size - 4, 28, 28));
        k.setColor(down > 0.2 ? ORANGE : new Color(0x4A5266));
        k.setStroke(new BasicStroke(2f));
        k.draw(new RoundRectangle2D.Double(-s, -s + press, size, size - 4, 28, 28));
        k.setFont(keys);
        k.setColor(INK);
        FontMetrics fm = k.getFontMetrics();
        k.drawString(label, (float) (-fm.stringWidth(label) / 2.0), (float) (press + fm.getAscent() / 2.0 - 8));
        k.dispose();
    }

    private void proxyTable(Graphics2D g, double x, double y, double w, double alpha, double flashRow) {
        String[][] rows = {
                {"118", "app.example.com", "GET", "/api/me", "200", "1,204"},
                {"119", "app.example.com", "GET", "/api/projects", "200", "3,881"},
                {"120", "app.example.com", "GET", "/api/projects/1337", "200", "512"},
                {"121", "app.example.com", "POST", "/api/projects/1337/share", "403", "188"},
                {"122", "cdn.example.com", "GET", "/assets/app.js", "200", "88,120"},
                {"123", "app.example.com", "GET", "/api/notifications", "200", "642"}};
        double[] cols = {0, 80, 330, 420};
        Graphics2D c = (Graphics2D) g.create();
        c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        double h = 70 + rows.length * 56;
        shadow(c, x, y, w, h);
        c.setColor(CARD);
        c.fill(new RoundRectangle2D.Double(x, y, w, h, 20, 20));
        c.setFont(body.deriveFont(Font.BOLD, 20f));
        c.setColor(MUTED);
        c.drawString("Proxy  ›  HTTP history", (float) (x + 28), (float) (y + 44));
        for (int r = 0; r < rows.length; r++) {
            double ry = y + 70 + r * 56;
            if (r == 2) {
                c.setColor(new Color(0x2F81F7).darker());
                c.fill(new RoundRectangle2D.Double(x + 12, ry + 4, w - 24, 48, 12, 12));
                if (flashRow > 0 && flashRow < 1) {
                    c.setColor(new Color(255, 102, 51, (int) (160 * (1 - flashRow))));
                    c.fill(new RoundRectangle2D.Double(x + 12, ry + 4, w - 24, 48, 12, 12));
                }
            }
            c.setFont(mono.deriveFont(20f));
            FontMetrics fm = c.getFontMetrics();
            for (int i = 0; i < rows[r].length; i++) {
                c.setColor(i == 2 ? ORANGE : i == 4 && rows[r][4].startsWith("4") ? new Color(0xE5534B) : r == 2 ? Color.WHITE : new Color(0xC9D1D9));
                double left = i < cols.length ? x + 30 + cols[i] : x + w - (i == 4 ? 150 : 34) - fm.stringWidth(rows[r][i]);
                c.drawString(rows[r][i], (float) left, (float) (ry + 36));
            }
        }
        c.dispose();
    }

    private void rawStream(Graphics2D g, double x, double y, double w, double alpha) {
        String[] lines = {"HTTP/1.1 200 OK", "Content-Type: text/event-stream", "", "event: delta",
                "data: {\"id\":\"evt_1\",\"delta\":{\"text\":\"Hel", "lo\"},\"index\":0}", "", "event: delta",
                "data: {\"id\":\"evt_2\",\"delta\":{\"text\":\" w", "orld\"},\"session\":\"s3ss10n-9f8e7d6c5b4a\"}", "", "data: [DONE]"};
        Graphics2D c = (Graphics2D) g.create();
        c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        double h = 90 + lines.length * 34;
        shadow(c, x, y, w, h);
        c.setColor(CARD);
        c.fill(new RoundRectangle2D.Double(x, y, w, h, 20, 20));
        c.setFont(body.deriveFont(Font.BOLD, 22f));
        c.setColor(MUTED);
        c.drawString("Before: raw stream", (float) (x + 28), (float) (y + 48));
        c.setFont(mono.deriveFont(19f));
        for (int i = 0; i < lines.length; i++) {
            c.setColor(lines[i].startsWith("data") || lines[i].startsWith("lo") || lines[i].startsWith("orld") ? new Color(0x93C763) : new Color(0xC9D1D9));
            c.drawString(lines[i], (float) (x + 28), (float) (y + 96 + i * 34));
        }
        c.dispose();
    }

    private void viewport(Graphics2D g, BufferedImage img, Rectangle2D view, double zoom, double fx, double fy, double alpha) {
        double base = Math.min(view.getWidth() / img.getWidth(), view.getHeight() / img.getHeight());
        double s = base * zoom;
        double w = img.getWidth() * s, h = img.getHeight() * s;
        double fitW = img.getWidth() * base, fitH = img.getHeight() * base;
        Rectangle2D frame = new Rectangle2D.Double(view.getCenterX() - fitW / 2, view.getCenterY() - fitH / 2, fitW, fitH);
        double x = frame.getCenterX() - fx * w + (fx - 0.5) * fitW, y = frame.getCenterY() - fy * h + (fy - 0.5) * fitH;
        x = Math.min(frame.getX(), Math.max(frame.getMaxX() - w, x));
        y = Math.min(frame.getY(), Math.max(frame.getMaxY() - h, y));
        shadow(g, frame.getX(), frame.getY(), fitW, fitH);
        Graphics2D c = (Graphics2D) g.create();
        c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        c.clip(new RoundRectangle2D.Double(frame.getX(), frame.getY(), fitW, fitH, 18, 18));
        c.drawImage(img, (int) x, (int) y, (int) w, (int) h, null);
        c.dispose();
    }

    private static void draw(Graphics2D g, BufferedImage img, double x, double y, double w, double h, double alpha) {
        Graphics2D c = (Graphics2D) g.create();
        c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, alpha))));
        c.drawImage(img, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
        c.dispose();
    }

    private static void shadow(Graphics2D g, double x, double y, double w, double h) {
        for (int i = 1; i <= 6; i++) {
            g.setColor(new Color(0, 0, 0, 22));
            g.fill(new RoundRectangle2D.Double(x - i * 2, y + i * 3, w + i * 4, h + i * 2, 24 + i * 4, 24 + i * 4));
        }
    }

    private static void glow(Graphics2D g, Rectangle2D r, Color c, double strength) {
        if (strength <= 0) return;
        for (int i = 8; i >= 1; i--) {
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.min(255, 26 * strength * (9 - i) / 8.0)));
            g.setStroke(new BasicStroke(i * 3f));
            g.draw(new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 16, 16));
        }
    }

    private static void ripple(Graphics2D g, double cx, double cy, double p, Color c) {
        if (p <= 0 || p >= 1) return;
        double r = 20 + 120 * easeOutCubic(p);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (200 * (1 - p))));
        g.setStroke(new BasicStroke(4f));
        g.draw(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    private void sparkles(Graphics2D g, double cx, double cy, double radius, double t, Color color, int count, double appear) {
        sparkles(g, cx, cy, radius, t, color, count, appear, 0.75);
    }

    private void sparkles(Graphics2D g, double cx, double cy, double radius, double t, Color color, int count, double appear, double squash) {
        if (appear <= 0) return;
        Random random = new Random(7);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2 + t * (0.15 + random.nextDouble() * 0.25);
            double dist = radius * (0.55 + random.nextDouble() * 0.6) * (0.6 + 0.4 * easeOutCubic(appear));
            double size = (6 + random.nextDouble() * 14) * (0.6 + 0.4 * Math.sin(t * 3 + i));
            Color c = i % 3 == 0 ? PINK : color;
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (200 * alphaOf(appear))));
            g.fill(star(cx + Math.cos(angle) * dist * 1.5, cy + Math.sin(angle) * dist * squash, Math.max(1, size)));
        }
    }

    private static Shape star(double cx, double cy, double r) {
        double k = r * 0.22;
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - r);
        p.quadTo(cx + k, cy - k, cx + r, cy);
        p.quadTo(cx + k, cy + k, cx, cy + r);
        p.quadTo(cx - k, cy + k, cx - r, cy);
        p.quadTo(cx - k, cy - k, cx, cy - r);
        p.closePath();
        return p;
    }


    private static void alpha(Graphics2D g, double a, Runnable draw) {
        java.awt.Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alphaOf(a)));
        draw.run();
        g.setComposite(old);
    }

    private static void center(Graphics2D g, String s, double cx, double y, Color color) {
        g.setColor(color);
        g.drawString(s, (float) (cx - g.getFontMetrics().stringWidth(s) / 2.0), (float) y);
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    private static double seg(double t, double a, double b) {
        return Math.max(0, Math.min(1, (t - a) / (b - a)));
    }

    private static double alphaOf(double p) {
        return Math.max(0, Math.min(1, p * 2));
    }

    private static double easeOutCubic(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    private static double easeInOut(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    private static double easeOutBack(double t) {
        if (t <= 0) return 0;
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    private static Settings dark() {
        Settings s = new Settings();
        s.theme = Settings.ThemeName.DARK;
        return s;
    }

    private static Exchange exchange(String request, String response, int status, String reason, long ms) {
        String path = request.substring(request.indexOf(' ') + 1, request.indexOf(" HTTP/"));
        return new Exchange(HttpText.parse(request, true), HttpText.parse(response, false),
                request.substring(0, request.indexOf(' ')), "https://app.example.com" + path.replaceAll("\\?.*", ""),
                "app.example.com", status, reason, ms);
    }

    private static BufferedImage render(Exchange e, Settings s, double scale) {
        HeaderInfo header = new HeaderInfo(e.state.title, e.state.caption, e.method, e.url, e.host, e.status, e.reason, e.timeMs);
        return new Scene(new ExchangeContent(e, s, Theme.of(s)), header, e.state.marks, s, null).toImage(scale);
    }

    private static BufferedImage pad(BufferedImage img, int w, int h) {
        if (img.getWidth() == w && img.getHeight() == h) return img;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(img.getRGB(0, 0)));
        g.fillRect(0, 0, w, h);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }

    private static Rectangle diff(BufferedImage a, BufferedImage b) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? new Rectangle(0, 0, 1, 1) : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static BufferedImage capture(JComponent c, double scale) {
        BufferedImage img = new BufferedImage((int) Math.ceil(c.getWidth() * scale), (int) Math.ceil(c.getHeight() * scale), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.scale(scale, scale);
        c.printAll(g);
        g.dispose();
        return img;
    }

    private BufferedImage notice(String message, Notice.Kind kind) {
        JLayeredPane layer = new JLayeredPane();
        layer.setSize(900, 200);
        Notice.show(layer, new Rectangle(0, 0, 900, 200), message, kind);
        Component n = layer.getComponentsInLayer(JLayeredPane.POPUP_LAYER)[0];
        BufferedImage img = new BufferedImage(n.getWidth() * 3, n.getHeight() * 3, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.scale(3, 3);
        n.paint(g);
        g.dispose();
        return img;
    }

    private static void layout(Component c) {
        if (c.getWidth() == 0) c.setSize(c.getPreferredSize());
        c.doLayout();
        if (c instanceof Container k) for (Component child : k.getComponents()) layout(child);
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
