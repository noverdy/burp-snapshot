package burpss.ui;

import burpss.Samples;

import burpss.core.Exchange;
import burpss.core.HttpText;
import burpss.core.Mark;
import burpss.core.PayloadDiff;
import burpss.core.ResultRow;
import burpss.core.Settings;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WindowPreview {

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        Map<String, String> prefs = new HashMap<>();
        Settings.Store store = new Settings.Store() {
            public String get(String key) { return prefs.get(key); }
            public void set(String key, String value) { prefs.put(key, value); }
        };
        SwingUtilities.invokeAndWait(() -> {
            for (Object key : java.util.Collections.list(javax.swing.UIManager.getDefaults().keys())) {
                if (javax.swing.UIManager.get(key) instanceof java.awt.Font f) javax.swing.UIManager.put(key, f.deriveFont(15f));
            }
            Exchange ex = new Exchange(HttpText.parse(Samples.REQUEST, true), HttpText.parse(Samples.RESPONSE, false),
                    "POST", "https://app.example.com/api/v2/users/1337/profile?debug=true&lang=en", "app.example.com", 200, "OK", 142);
            Mark m = new Mark(Samples.tokenAnchor(1, ex.response, burpss.core.Token.Kind.JSON_MEMBER, "email"), 0);
            m.note = "PII of another user";
            ex.state.marks.add(m);
            ExchangeWindow w = new ExchangeWindow(null, List.of(ex, ex), new Settings(), store);
            w.open(null);
            w.setSize(1000, 820);
            w.validate();
            expandSection(w, "Watermark style");
            capture(w, new File(out, "window-exchange.png"));

            List<String> requests = new ArrayList<>();
            String[] users = {"admin", "root", "test", "guest", "administrator"};
            String[] passwords = {"admin", "toor", "test123", "guest", "P@ssw0rd!"};
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
            Settings ts = new Settings();
            ts.containsText = "dashboard";
            TableWindow t = new TableWindow(null, rows, "POST", "https://app.example.com/login", "app.example.com", ts, store);
            t.open(null);
            t.state().marks.add(new Mark(new burpss.core.Anchor.Rows(5, 5), 3));
            t.state().marks.get(0).note = "Only this credential pair redirects to the dashboard";
            t.state().title = "Credential stuffing: valid admin password";
            t.syncTitleField();
            t.rebuild();
            capture(t, new File(out, "window-table.png"));
            w.dispose();
            t.dispose();
        });
        System.exit(0);
    }

    static void expandSection(java.awt.Container root, String title) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.JButton b && b.getText().endsWith(title)) b.doClick();
            else if (c instanceof java.awt.Container child) expandSection(child, title);
        }
    }

    static void capture(EditorWindow frame, File file) {
        frame.validate();
        BufferedImage img = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        frame.getRootPane().paintAll(img.createGraphics());
        try {
            ImageIO.write(img, "png", file);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
