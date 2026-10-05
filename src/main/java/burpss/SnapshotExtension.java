package burpss;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.persistence.Preferences;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burpss.core.Anchor;
import burpss.core.DecodedText;
import burpss.core.Exchange;
import burpss.core.HttpText;
import burpss.core.Mark;
import burpss.core.Settings;
import burpss.render.Fonts;
import burpss.ui.EditorWindow;
import burpss.ui.ExchangeWindow;
import burpss.ui.SnapshotsTab;
import burpss.ui.TableWindow;

import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotExtension implements BurpExtension {

    private MontoyaApi api;
    private SnapshotLibrary library;
    private final ResponseTimer timer = new ResponseTimer();

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Snapshot");
        Fonts.use(api.userInterface().currentEditorFont(), api.userInterface().currentDisplayFont());
        api.http().registerHttpHandler(timer);
        library = new SnapshotLibrary(api.persistence().extensionData(), this::settings);
        SnapshotsTab tab = new SnapshotsTab(library);
        library.onChange(tab::refresh);
        library.onOpen(this::reopen);
        api.userInterface().registerSuiteTab("Snapshot", tab);
        api.userInterface().registerContextMenuItemsProvider(new ContextMenuItemsProvider() {
            @Override
            public List<Component> provideMenuItems(ContextMenuEvent event) {
                return menuItems(event);
            }
        });
    }

    private List<Component> menuItems(ContextMenuEvent event) {
        List<Component> items = new ArrayList<>();
        List<HttpRequestResponse> selected = event.selectedRequestResponses();
        if (event.messageEditorRequestResponse().isPresent() || !selected.isEmpty()) {
            items.add(item("Snapshot request/response…", () -> openExchanges(event)));
        }
        if (selected.size() >= 2) {
            items.add(item("Snapshot as results table…", () -> openTable(selected)));
        }
        return items;
    }

    private static JMenuItem item(String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        return item;
    }

    private void openExchanges(ContextMenuEvent event) {
        List<HttpRequestResponse> items = new ArrayList<>();
        List<Exchange> exchanges = new ArrayList<>();
        event.messageEditorRequestResponse().ifPresentOrElse(editor -> {
            items.add(editor.requestResponse());
            exchanges.add(withSelection(toExchange(editor.requestResponse(), event.selectedRequestResponses()), editor));
        }, () -> event.selectedRequestResponses().forEach(rr -> {
            items.add(rr);
            exchanges.add(toExchange(rr, List.of()));
        }));
        List<String> ids = items.stream().map(rr -> SnapshotLibrary.newId()).toList();
        ExchangeWindow window = new ExchangeWindow(exchanges, settings(), store());
        window.onUpdate(u -> library.saveExchange(ids.get(u.item()), items.get(u.item()), exchanges.get(u.item()), u));
        show(window);
    }

    private void openTable(List<HttpRequestResponse> selected) {
        List<Long> times = selected.stream().map(rr -> timeMs(rr, List.of())).toList();
        HttpRequest first = selected.get(0).request();
        String id = SnapshotLibrary.newId();
        TableWindow window = new TableWindow(SnapshotLibrary.rows(selected, times), first.method(), first.url(),
                first.httpService().host(), settings(), store());
        window.onUpdate(u -> library.saveTable(id, selected, times, first.method(), first.url(), first.httpService().host(), u));
        show(window);
    }

    private void reopen(String id) {
        if (library.isTable(id)) {
            SnapshotLibrary.Table t = library.table(id);
            List<HttpRequestResponse> items = library.items(id);
            List<Long> times = library.times(id);
            TableWindow window = new TableWindow(t.rows(), t.method(), t.url(), t.host(), settings(), store());
            window.restore(t.state());
            window.onUpdate(u -> library.saveTable(id, items, times, t.method(), t.url(), t.host(), u));
            show(window);
            return;
        }
        Exchange exchange = library.exchange(id);
        HttpRequestResponse item = library.item(id);
        ExchangeWindow window = new ExchangeWindow(List.of(exchange), settings(), store());
        window.onUpdate(u -> library.saveExchange(id, item, exchange, u));
        show(window);
    }

    private void show(EditorWindow window) {
        api.userInterface().applyThemeToComponent(window);
        window.open(api.userInterface().swingUtils().suiteFrame());
    }

    private Exchange toExchange(HttpRequestResponse rr, List<HttpRequestResponse> alternatives) {
        HttpRequest request = rr.request();
        HttpResponse response = rr.response();
        return new Exchange(
                HttpText.parse(decode(request.toByteArray().getBytes()), true),
                response == null ? null : HttpText.parse(decode(response.toByteArray().getBytes()), false),
                request.method(), request.url(), request.httpService().host(),
                response == null ? 0 : response.statusCode(),
                response == null ? "" : response.reasonPhrase(),
                timeMs(rr, alternatives));
    }

    private static Exchange withSelection(Exchange exchange, MessageEditorHttpRequestResponse editor) {
        editor.selectionOffsets().filter(r -> r.endIndexExclusive() > r.startIndexInclusive()).ifPresent(range -> {
            boolean isRequest = editor.selectionContext() == MessageEditorHttpRequestResponse.SelectionContext.REQUEST;
            HttpRequestResponse rr = editor.requestResponse();
            byte[] bytes = isRequest ? rr.request().toByteArray().getBytes() : rr.response().toByteArray().getBytes();
            DecodedText text = DecodedText.decode(bytes);
            exchange.state.marks.add(new Mark(new Anchor.Text(isRequest ? 0 : 1,
                    text.charOffset(range.startIndexInclusive()), text.charOffset(range.endIndexExclusive())), 0));
        });
        return exchange;
    }

    private long timeMs(HttpRequestResponse rr, List<HttpRequestResponse> alternatives) {
        List<HttpRequestResponse> candidates = new ArrayList<>(List.of(rr));
        candidates.addAll(alternatives);
        for (HttpRequestResponse candidate : candidates) {
            long ms = candidate.timingData()
                    .map(t -> t.timeBetweenRequestSentAndEndOfResponse() != null
                            ? t.timeBetweenRequestSentAndEndOfResponse() : t.timeBetweenRequestSentAndStartOfResponse())
                    .map(d -> d == null ? -1L : d.toMillis()).orElse(-1L);
            if (ms >= 0) return ms;
        }
        return timer.elapsedMs(rr.response());
    }

    private static String decode(byte[] bytes) {
        return SnapshotLibrary.decode(bytes);
    }

    private Settings settings() {
        Settings settings = new Settings();
        settings.load(store());
        return settings;
    }

    private Settings.Store store() {
        Preferences prefs = api.persistence().preferences();
        return new Settings.Store() {
            public String get(String key) { return prefs.getString(key); }
            public void set(String key, String value) { prefs.setString(key, value); }
        };
    }
}
