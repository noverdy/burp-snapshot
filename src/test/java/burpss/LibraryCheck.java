package burpss;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.internal.MontoyaObjectFactory;
import burp.api.montoya.internal.ObjectFactoryLocator;
import burp.api.montoya.persistence.PersistedList;
import burp.api.montoya.persistence.PersistedObject;
import burpss.ai.AiMarkup;
import burpss.core.Anchor;
import burpss.core.AutoRedactor;
import burpss.core.EditState;
import burpss.core.Exchange;
import burpss.core.HttpText;
import burpss.core.Mark;
import burpss.core.ResultRow;
import burpss.core.Settings;
import burpss.core.Shortcuts;
import burpss.core.StateCodec;
import burpss.core.TextRange;
import burpss.render.DisplayText;
import burpss.render.ExchangeContent;
import burpss.render.TableContent;
import burpss.render.Theme;
import burpss.ui.EditorWindow;
import burpss.ui.ExchangeWindow;
import burpss.ui.PreferencesDialog;
import burpss.ui.SnapshotsTab;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Label;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LibraryCheck {

    public static void main(String[] args) throws Exception {
        ObjectFactoryLocator.FACTORY = proxy(MontoyaObjectFactory.class, (name, a) ->
                name.equals("persistedObject") ? persistedObject() : persistedList());

        EditState state = new EditState();
        state.title = "IDOR";
        Mark mark = new Mark(new Anchor.Text(1, 10, 20), 2);
        mark.note = "note | with \"chars\"";
        mark.calloutOffset = new Point2D.Double(12.5, -3);
        state.marks.add(mark);
        state.marks.add(new Mark(new Anchor.Rows(2, 4), 0));
        state.manualRedactions.get(0).add(new TextRange(3, 9));
        state.suppressedAuto.get(1).add("5:9");
        state.toggledHeaders.get(0).add(42);
        state.payloadOverrides.put(3, "clean label");
        state.hiddenRows.add(2);
        EditState back = StateCodec.decode(StateCodec.encode(state));
        check("codec round trip", back.title.equals("IDOR") && back.marks.size() == 2
                && back.marks.get(0).note.equals(mark.note) && back.marks.get(0).calloutOffset.equals(mark.calloutOffset)
                && back.marks.get(1).anchor.equals(new Anchor.Rows(2, 4)) && back.manualRedactions.get(0).get(0).equals(new TextRange(3, 9))
                && back.suppressedAuto.get(1).contains("5:9") && back.toggledHeaders.get(0).contains(42)
                && back.payloadOverrides.get(3).equals("clean label") && back.hiddenRows.contains(2));

        PersistedObject project = persistedObject();
        int[] changes = {0};
        SnapshotLibrary library = new SnapshotLibrary(project, Settings::new);
        library.onChange(() -> changes[0]++);

        HttpRequestResponse item = exchangeItem(Samples.REQUEST, Samples.RESPONSE);
        Exchange exchange = new Exchange(HttpText.parse(Samples.REQUEST, true), HttpText.parse(Samples.RESPONSE, false),
                "POST", "https://app.example.com/x", "app.example.com", 200, "OK", 142);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            String id = SnapshotLibrary.newId();
            ids.add(id);
            EditState s = new EditState();
            s.title = "draft " + i;
            library.saveExchange(id, item, exchange, new EditorWindow.Update(0, s, new Settings(), false));
            Thread.sleep(3);
        }
        List<SnapshotsTab.Entry> entries = library.entries();
        check("drafts capped at " + SnapshotLibrary.MAX_DRAFTS, entries.size() == SnapshotLibrary.MAX_DRAFTS
                && entries.stream().noneMatch(e -> e.title().equals("draft 0")) && entries.get(0).title().equals("draft 22"));

        String kept = ids.get(22);
        EditState exported = state.copy();
        library.saveExchange(kept, item, exchange, new EditorWindow.Update(0, exported, new Settings(), true));
        exported.title = "IDOR edited";
        Thread.sleep(3);
        library.saveExchange(kept, item, exchange, new EditorWindow.Update(0, exported, new Settings(), false));
        SnapshotsTab.Entry entry = library.entries().stream().filter(e -> e.id().equals(kept)).findFirst().orElseThrow();
        check("exported stays exported after a later edit, updated in place", entry.exported() && entry.title().equals("IDOR edited")
                && library.entries().size() == SnapshotLibrary.MAX_DRAFTS);

        for (int i = 0; i < 25; i++) {
            EditState s = new EditState();
            s.title = "more " + i;
            library.saveExchange(SnapshotLibrary.newId(), item, exchange, new EditorWindow.Update(0, s, new Settings(), false));
            Thread.sleep(2);
        }
        check("exported entries are never pruned", library.entries().stream().anyMatch(e -> e.id().equals(kept))
                && library.entries().stream().filter(e -> !e.exported()).count() == SnapshotLibrary.MAX_DRAFTS);

        Exchange reopened = library.exchange(kept);
        check("reopen restores request, meta and state", reopened.request.text().equals(Samples.REQUEST)
                && reopened.timeMs == 142 && reopened.state.title.equals("IDOR edited") && reopened.state.marks.size() == 2);
        check("exchange preview renders", library.preview(kept).getWidth() > 500);

        List<HttpRequestResponse> items = new ArrayList<>();
        for (String user : List.of("admin", "root", "guest")) {
            items.add(exchangeItem("POST /login HTTP/1.1\r\nHost: h\r\n\r\nuser=" + user + "&x=1", "HTTP/1.1 401 Unauthorized\r\n\r\nno"));
        }
        String tableId = SnapshotLibrary.newId();
        EditState tableState = new EditState();
        tableState.payloadOverrides.put(2, "renamed");
        library.saveTable(tableId, items, List.of(10L, 20L, 30L), "POST", "https://h/login", "h", new EditorWindow.Update(0, tableState, new Settings(), true));
        SnapshotLibrary.Table table = library.table(tableId);
        check("table reopen keeps rows, diffed payloads and state", library.isTable(tableId) && table.rows().size() == 3
                && table.rows().get(0).payload().equals("admin") && table.rows().get(2).timeMs() == 30
                && table.state().payloadOverrides.get(2).equals("renamed"));
        check("table preview renders", library.preview(tableId).getWidth() > 300);

        Settings dark = new Settings();
        dark.theme = Settings.ThemeName.DARK;
        dark.layout = Settings.Layout.STACKED;
        String darkId = SnapshotLibrary.newId();
        library.saveExchange(darkId, item, exchange, new EditorWindow.Update(0, new EditState(), dark, true));
        Settings light = new Settings();
        library.saveExchange(SnapshotLibrary.newId(), item, exchange, new EditorWindow.Update(0, new EditState(), light, false));
        java.awt.image.BufferedImage darkPreview = library.preview(darkId);
        check("each entry keeps its own settings", library.settings(darkId).theme == Settings.ThemeName.DARK
                && library.settings(darkId).layout == Settings.Layout.STACKED
                && new Color(darkPreview.getRGB(5, darkPreview.getHeight() / 2)).getRed() < 80);

        List<EditorWindow.Update> updates = new ArrayList<>();
        Map<String, String> prefs = new HashMap<>();
        Settings.Store store = new Settings.Store() {
            public String get(String key) { return prefs.get(key); }
            public void set(String key, String value) { prefs.put(key, value); }
        };
        ExchangeWindow[] window = new ExchangeWindow[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            window[0] = new ExchangeWindow(List.of(exchange), new Settings(), store);
            window[0].onUpdate(updates::add);
            window[0].open(null);
            click(window[0], "Purple");
            click(window[0], "▸  Content");
        });
        Thread.sleep(1200);
        check("mark color and sidebar sections don't create a draft", updates.isEmpty());
        javax.swing.SwingUtilities.invokeAndWait(() -> click(window[0], "Dark"));
        Thread.sleep(1200);
        check("changing only a setting autosaves a draft with that setting", updates.size() == 1
                && !updates.get(0).exported() && updates.get(0).settings().theme == Settings.ThemeName.DARK);
        javax.swing.SwingUtilities.invokeAndWait(() -> click(window[0], "Light"));
        javax.swing.SwingUtilities.invokeAndWait(() -> window[0].dispose());
        Thread.sleep(200);
        check("closing the window saves pending edits right away", updates.size() == 2
                && updates.get(1).settings().theme == Settings.ThemeName.LIGHT);

        capture(library);
        library.delete(kept);
        check("delete removes the entry", library.entries().stream().noneMatch(e -> e.id().equals(kept)) && changes[0] > 0);
        String json = "POST /api HTTP/1.1\r\nHost: x\r\nContent-Type: application/json\r\n\r\n"
                + "{\"tokens\":[\"aaaa1111\",\"bbbb2222\"],\"session\":{\"id\":\"cccc3333\",\"n\":42},\"user\":\"bob\"}";
        HttpText parsed = HttpText.parse(json, true);
        List<String> redacted = AutoRedactor.find(parsed, new Settings()).stream()
                .map(r -> json.substring(r.start(), r.end())).toList();
        check("auto-redaction covers values inside arrays and objects under sensitive keys",
                redacted.equals(List.of("aaaa1111", "bbbb2222", "cccc3333", "42")));

        Shortcuts keys = new Shortcuts();
        check("quick copy title template expands", keys.title("POST", "/api/login", "x", 200).equals("POST /api/login"));
        check("Burp default hotkeys are detected regardless of modifier order", Shortcuts.usedByBurp("Shift+Ctrl+R")
                && !Shortcuts.usedByBurp(Shortcuts.QUICK_COPY)
                && !Shortcuts.usedByBurp(Shortcuts.OPEN));
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        KeyEvent press = new KeyEvent(new Label(), KeyEvent.KEY_PRESSED, 0,
                menu | InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_C, 'C');
        check("recorded key press uses Burp's hotkey format", "Ctrl+Shift+C".equals(PreferencesDialog.record(press)));
        EditState captioned = new EditState();
        captioned.caption = "User B read user A's project.";
        captioned.aiContext = "IDOR";
        EditState captionBack = StateCodec.decode(StateCodec.encode(captioned));
        byte[] v1 = StateCodec.encode(new EditState());
        v1 = Arrays.copyOf(v1, v1.length - 4);
        v1[3] = 1;
        check("caption and AI context survive the codec, v1 states still load",
                captionBack.caption.equals(captioned.caption) && captionBack.aiContext.equals("IDOR")
                        && StateCodec.decode(v1).caption.isEmpty());

        String secret = "eyJhbGciOiJIUzI1NiJ9.c2VjcmV0LXNlc3Npb24tdG9rZW4";
        String aiRequest = "GET /api/projects/1337 HTTP/1.1\r\nHost: app.example\r\nAuthorization: Bearer " + secret + "\r\n\r\n";
        String aiResponse = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n"
                + "{\"id\":1337,\"owner\":{\"email\":\"alice@example.com\"},\"name\":\"Secret project\"}";
        Exchange aiExchange = new Exchange(HttpText.parse(aiRequest, true), HttpText.parse(aiResponse, false),
                "GET", "https://app.example/api/projects/1337", "app.example", 200, "OK", 120);
        Settings aiSettings = new Settings();
        ExchangeContent aiContent = new ExchangeContent(aiExchange, aiSettings, Theme.of(aiSettings));
        String prompt = AiMarkup.exchangePrompt("IDOR via project ID", aiExchange, aiContent);
        check("AI prompt hides redacted values", !prompt.contains(secret.substring(0, 20)) && prompt.contains("‹redacted›")
                && prompt.contains("/api/projects/1337") && prompt.contains("alice@example.com"));

        String reply = "```json\n{\"title\": \"IDOR on project endpoint\", \"caption\": \"User B retrieved project 1337.\", \"marks\": ["
                + "{\"pane\": \"request\", \"target\": {\"header\": \"authorization\"}, \"note\": \"User B's session\"},"
                + "{\"pane\": \"request\", \"target\": {\"text\": \"/projects/1337\"}, \"note\": \"User A's project\"},"
                + "{\"pane\": \"response\", \"target\": {\"json\": \"owner.email\"}, \"note\": \"Owner's email leaked\"},"
                + "{\"pane\": \"response\", \"target\": {\"status\": true}, \"note\": \"Allowed\"},"
                + "{\"pane\": \"response\", \"target\": {\"header\": \"X-Missing\"}, \"note\": \"nope\"}]}\n```";
        AiMarkup.Result result = AiMarkup.exchangeResult(reply, aiExchange, aiContent, 2);
        List<String> marked = result.marks().stream().map(m -> {
            Anchor.Text t = (Anchor.Text) m.anchor;
            return (t.pane() == 0 ? aiRequest : aiResponse).substring(t.start(), t.end());
        }).toList();
        check("AI reply maps to header, path text, JSON member and status marks",
                result.title().equals("IDOR on project endpoint") && result.caption().startsWith("User B")
                        && result.unmatched() == 1 && marked.size() == 4
                        && marked.get(0).startsWith("Authorization: Bearer") && marked.get(1).equals("/projects/1337")
                        && marked.get(2).equals("\"email\":\"alice@example.com\"") && marked.get(3).startsWith("HTTP/1.1 200")
                        && result.marks().get(0).note.equals("User B's session") && result.marks().get(0).color == 2);

        EditState aiTableState = new EditState();
        TableContent tableContent = new TableContent(List.of(
                new ResultRow(1, "1001", 403, 120, 10, ""),
                new ResultRow(2, "1002", 200, 950, 12, ""),
                new ResultRow(3, "1003", 200, 940, 11, "")), aiTableState, aiSettings, Theme.of(aiSettings));
        AiMarkup.Result rows = AiMarkup.tableResult(
                "{\"title\":\"t\",\"caption\":\"c\",\"marks\":[{\"rows\":[2,3],\"note\":\"Bypass\"},{\"rows\":[9,9]}]}", tableContent, 0);
        check("AI table reply marks row ranges and skips unknown rows",
                rows.marks().size() == 1 && rows.marks().get(0).anchor.equals(new Anchor.Rows(2, 3)) && rows.unmatched() == 1
                        && AiMarkup.tablePrompt("", "GET", "/x", tableContent).contains("Payload: 1002"));
        String sse = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n"
                + "event: message\ndata: {\"id\":1,\"token\":\"abcdefgh12345678\"}\n\ndata: [DONE]\n\n";
        HttpText sseText = HttpText.parse(sse, false);
        DisplayText sseDisplay = DisplayText.build(sseText, new Settings(), Set.of());
        StringBuilder shown = new StringBuilder();
        for (int i = 0; i < sseDisplay.length(); i++) shown.append(sseDisplay.charAt(i));
        check("SSE data lines with JSON are pretty-printed and auto-redacted",
                sseText.bodyKind() == HttpText.BodyKind.SSE
                        && shown.toString().contains("event: message\ndata: {\n  \"id\": 1,\n  \"token\": \"abcdefgh12345678\"\n}\n\ndata: [DONE]")
                        && AutoRedactor.find(sseText, new Settings()).stream()
                                .anyMatch(r -> sse.substring(r.start(), r.end()).equals("abcdefgh12345678")));
        String lineRequest = "GET /api/activities?limit=200&merchantId=66a95a09-bbe5&order=1 HTTP/1.1\r\nHost: x\r\n\r\n";
        Exchange lineExchange = new Exchange(HttpText.parse(lineRequest, true), null, "GET", "https://x/api/activities", "x", 0, "", -1);
        ExchangeContent lineContent = new ExchangeContent(lineExchange, aiSettings, Theme.of(aiSettings));
        AiMarkup.Result lineResult = AiMarkup.exchangeResult(
                "{\"marks\":[{\"pane\":\"request\",\"target\":{\"text\":\"GET /api/activities?limit=200&merchantId=66a95a09-bbe5&order=1 HTTP/1.1\"},"
                        + "\"note\":\"Targeted merchantId parameter for IDOR test\"}]}", lineExchange, lineContent, 0);
        Anchor.Text lineMark = (Anchor.Text) lineResult.marks().get(0).anchor;
        check("a whole-line AI text target narrows to the parameter named in the note",
                lineRequest.substring(lineMark.start(), lineMark.end()).equals("merchantId=66a95a09-bbe5"));
        String wrapped = "GET /api/v1/msys/portal/internal/merchant/activities?limit=200&page=1&merchantId=66a95a09-bbe5-4a4d-87df-5617906e97a5&order=1 HTTP/1.1\r\nHost: x\r\n\r\n";
        Exchange wrapExchange = new Exchange(HttpText.parse(wrapped, true), null, "GET", "https://x/", "x", 0, "", -1);
        ExchangeContent wrapContent = new ExchangeContent(wrapExchange, aiSettings, Theme.of(aiSettings));
        wrapContent.layout(500);
        int at = wrapped.indexOf("merchantId=");
        List<Rectangle2D> parts = wrapContent.segments(new Anchor.Text(0, at, wrapped.indexOf("&order")));
        check("a value that wraps is outlined per visual line, not as one box over both lines",
                parts.size() == 2 && parts.get(1).getX() < parts.get(0).getX());
        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void capture(SnapshotLibrary library) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            SnapshotsTab tab = new SnapshotsTab(library, () -> { });
            javax.swing.JFrame frame = new javax.swing.JFrame();
            frame.setContentPane(tab);
            frame.addNotify();
            frame.setSize(1400, 760);
            frame.validate();
            tab.refresh();
            frame.validate();
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1400, 760, java.awt.image.BufferedImage.TYPE_INT_RGB);
            tab.printAll(img.createGraphics());
            try {
                javax.imageio.ImageIO.write(img, "png", new java.io.File("build/samples/snapshots-tab.png"));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            frame.dispose();
        });
    }

    private static void click(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof javax.swing.AbstractButton b && (text.equals(b.getText()) || text.equals(b.getToolTipText()))) {
                b.doClick();
                return;
            }
            if (c instanceof Container child) click(child, text);
        }
    }

    private static int failures;

    private static void check(String name, boolean ok) {
        if (!ok) failures++;
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
    }

    interface Handler {
        Object call(String name, Object[] args);
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(LibraryCheck.class.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            case "toString" -> type.getSimpleName();
            default -> handler.call(m.getName(), a == null ? new Object[0] : a);
        });
    }

    static PersistedObject persistedObject() {
        Map<String, Map<String, Object>> byType = new HashMap<>();
        return proxy(PersistedObject.class, (name, a) -> {
            for (String verb : List.of("get", "set", "delete")) {
                if (name.startsWith(verb) && a.length > 0) {
                    Map<String, Object> values = byType.computeIfAbsent(name.substring(verb.length()), k -> new HashMap<>());
                    return switch (verb) {
                        case "get" -> values.get((String) a[0]);
                        case "set" -> values.put((String) a[0], a[1]) == null ? null : null;
                        default -> values.remove((String) a[0]) == null ? null : null;
                    };
                }
            }
            if (name.endsWith("Keys")) {
                String type = Character.toUpperCase(name.charAt(0)) + name.substring(1, name.length() - 4);
                Set<String> keys = new LinkedHashSet<>(byType.getOrDefault(type, Map.of()).keySet());
                return keys;
            }
            throw new UnsupportedOperationException(name);
        });
    }

    @SuppressWarnings("unchecked")
    static <T> PersistedList<T> persistedList() {
        List<T> backing = new ArrayList<>();
        return (PersistedList<T>) Proxy.newProxyInstance(LibraryCheck.class.getClassLoader(), new Class<?>[]{PersistedList.class},
                (p, m, a) -> m.invoke(backing, a));
    }

    static HttpRequestResponse exchangeItem(String request, String response) {
        HttpRequest req = proxy(HttpRequest.class, (name, a) -> switch (name) {
            case "toByteArray" -> bytes(request);
            default -> throw new UnsupportedOperationException(name);
        });
        HttpResponse res = proxy(HttpResponse.class, (name, a) -> switch (name) {
            case "toByteArray" -> bytes(response);
            case "statusCode" -> (short) Integer.parseInt(response.split(" ")[1]);
            default -> throw new UnsupportedOperationException(name);
        });
        return proxy(HttpRequestResponse.class, (name, a) -> switch (name) {
            case "request" -> req;
            case "response" -> res;
            case "timingData" -> Optional.empty();
            default -> throw new UnsupportedOperationException(name);
        });
    }

    static ByteArray bytes(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        return proxy(ByteArray.class, (name, a) -> switch (name) {
            case "getBytes" -> data;
            case "length" -> data.length;
            default -> throw new UnsupportedOperationException(name);
        });
    }
}
