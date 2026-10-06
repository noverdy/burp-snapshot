package burpss;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.internal.MontoyaObjectFactory;
import burp.api.montoya.internal.ObjectFactoryLocator;
import burp.api.montoya.persistence.PersistedList;
import burp.api.montoya.persistence.PersistedObject;
import burpss.core.Anchor;
import burpss.core.EditState;
import burpss.core.Exchange;
import burpss.core.HttpText;
import burpss.core.Mark;
import burpss.core.Settings;
import burpss.core.StateCodec;
import burpss.core.TextRange;
import burpss.ui.EditorWindow;
import burpss.ui.SnapshotsTab;

import java.awt.geom.Point2D;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
                && new java.awt.Color(darkPreview.getRGB(5, darkPreview.getHeight() / 2)).getRed() < 80);

        List<EditorWindow.Update> updates = new ArrayList<>();
        java.util.Map<String, String> prefs = new HashMap<>();
        Settings.Store store = new Settings.Store() {
            public String get(String key) { return prefs.get(key); }
            public void set(String key, String value) { prefs.put(key, value); }
        };
        burpss.ui.ExchangeWindow[] window = new burpss.ui.ExchangeWindow[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            window[0] = new burpss.ui.ExchangeWindow(List.of(exchange), new Settings(), store);
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
        List<String> redacted = burpss.core.AutoRedactor.find(parsed, new Settings()).stream()
                .map(r -> json.substring(r.start(), r.end())).toList();
        check("auto-redaction covers values inside arrays and objects under sensitive keys",
                redacted.equals(List.of("aaaa1111", "bbbb2222", "cccc3333", "42")));

        burpss.core.Shortcuts keys = new burpss.core.Shortcuts();
        check("quick copy title template expands", keys.title("POST", "/api/login", "x", 200).equals("POST /api/login"));
        check("Burp default hotkeys are detected regardless of modifier order", burpss.core.Shortcuts.usedByBurp("Shift+Ctrl+R")
                && !burpss.core.Shortcuts.usedByBurp(burpss.core.Shortcuts.QUICK_COPY)
                && !burpss.core.Shortcuts.usedByBurp(burpss.core.Shortcuts.OPEN));
        int menu = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        java.awt.event.KeyEvent press = new java.awt.event.KeyEvent(new java.awt.Label(), java.awt.event.KeyEvent.KEY_PRESSED, 0,
                menu | java.awt.event.InputEvent.SHIFT_DOWN_MASK, java.awt.event.KeyEvent.VK_C, 'C');
        check("recorded key press uses Burp's hotkey format", "Ctrl+Shift+C".equals(burpss.ui.PreferencesDialog.record(press)));
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

    private static void click(java.awt.Container root, String text) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.AbstractButton b && (text.equals(b.getText()) || text.equals(b.getToolTipText()))) {
                b.doClick();
                return;
            }
            if (c instanceof java.awt.Container child) click(child, text);
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
