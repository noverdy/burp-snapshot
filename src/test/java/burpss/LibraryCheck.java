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
            library.saveExchange(id, item, exchange, new EditorWindow.Update(0, s, false));
            Thread.sleep(3);
        }
        List<SnapshotsTab.Entry> entries = library.entries();
        check("drafts capped at " + SnapshotLibrary.MAX_DRAFTS, entries.size() == SnapshotLibrary.MAX_DRAFTS
                && entries.stream().noneMatch(e -> e.title().equals("draft 0")) && entries.get(0).title().equals("draft 22"));

        String kept = ids.get(22);
        EditState exported = state.copy();
        library.saveExchange(kept, item, exchange, new EditorWindow.Update(0, exported, true));
        exported.title = "IDOR edited";
        Thread.sleep(3);
        library.saveExchange(kept, item, exchange, new EditorWindow.Update(0, exported, false));
        SnapshotsTab.Entry entry = library.entries().stream().filter(e -> e.id().equals(kept)).findFirst().orElseThrow();
        check("exported stays exported after a later edit, updated in place", entry.exported() && entry.title().equals("IDOR edited")
                && library.entries().size() == SnapshotLibrary.MAX_DRAFTS);

        for (int i = 0; i < 25; i++) {
            EditState s = new EditState();
            s.title = "more " + i;
            library.saveExchange(SnapshotLibrary.newId(), item, exchange, new EditorWindow.Update(0, s, false));
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
        library.saveTable(tableId, items, List.of(10L, 20L, 30L), "POST", "https://h/login", "h", new EditorWindow.Update(0, tableState, true));
        SnapshotLibrary.Table table = library.table(tableId);
        check("table reopen keeps rows, diffed payloads and state", library.isTable(tableId) && table.rows().size() == 3
                && table.rows().get(0).payload().equals("admin") && table.rows().get(2).timeMs() == 30
                && table.state().payloadOverrides.get(2).equals("renamed"));
        check("table preview renders", library.preview(tableId).getWidth() > 300);

        capture(library);
        library.delete(kept);
        check("delete removes the entry", library.entries().stream().noneMatch(e -> e.id().equals(kept)) && changes[0] > 0);
        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void capture(SnapshotLibrary library) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            SnapshotsTab tab = new SnapshotsTab(library);
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
