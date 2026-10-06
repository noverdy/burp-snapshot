package burpss;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.EnhancedCapability;
import burp.api.montoya.ai.chat.Message;
import burp.api.montoya.ai.chat.PromptOptions;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;
import burp.api.montoya.http.handler.TimingData;
import burp.api.montoya.http.message.HttpMessage;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.persistence.Preferences;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.ui.hotkey.HotKey;
import burp.api.montoya.ui.hotkey.HotKeyEvent;
import burpss.ai.AiMarkup;
import burpss.core.Anchor;
import burpss.core.DecodedText;
import burpss.core.EditState;
import burpss.core.Exchange;
import burpss.core.HttpText;
import burpss.core.Mark;
import burpss.core.Settings;
import burpss.core.Shortcuts;
import burpss.render.Fonts;
import burpss.ui.EditorWindow;
import burpss.ui.ExchangeWindow;
import burpss.ui.ImageExport;
import burpss.ui.PreferencesDialog;
import burpss.ui.SnapshotsTab;
import burpss.ui.TableWindow;
import burpss.ui.Toast;

import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class SnapshotExtension implements BurpExtension {

    private MontoyaApi api;
    private SnapshotLibrary library;
    private final ResponseTimer timer = new ResponseTimer();
    private Shortcuts shortcuts;
    private SnapshotsTab tab;
    private final List<Registration> hotkeys = new ArrayList<>();
    private final AiMarkup.Model ai = new AiMarkup.Model() {
        public boolean available() {
            try {
                return api.ai().isEnabled();
            } catch (RuntimeException e) {
                return false;
            }
        }

        public String ask(String system, String user) {
            return api.ai().prompt().execute(PromptOptions.promptOptions().withTemperature(0.2),
                    Message.systemMessage(system), Message.userMessage(user)).content();
        }
    };

    @Override
    public Set<EnhancedCapability> enhancedCapabilities() {
        return Set.of(EnhancedCapability.AI_FEATURES);
    }

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Snapshot");
        Fonts.use(api.userInterface().currentEditorFont(), api.userInterface().currentDisplayFont());
        api.http().registerHttpHandler(timer);
        library = new SnapshotLibrary(api.persistence().extensionData(), this::settings);
        shortcuts = Shortcuts.load(store());
        String error = registerHotkeys(shortcuts);
        if (error != null) api.logging().logToError(error);
        tab = new SnapshotsTab(library, this::editPreferences);
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

    private String registerHotkeys(Shortcuts keys) {
        hotkeys.forEach(Registration::deregister);
        hotkeys.clear();
        try {
            hotkey("Snapshot: quick copy", keys.quickCopy, this::quickCopy);
            hotkey("Snapshot: open", keys.open, this::openFromHotkey);
            return null;
        } catch (RuntimeException e) {
            return "Could not register hotkey: " + e.getMessage();
        }
    }

    private void hotkey(String name, String keys, Consumer<HotKeyEvent> action) {
        if (keys.isBlank()) return;
        hotkeys.add(api.userInterface().registerHotKeyHandler(HotKey.hotKey(name, keys),
                event -> SwingUtilities.invokeLater(() -> action.accept(event))));
    }

    private void editPreferences() {
        PreferencesDialog.show(tab, shortcuts, edited -> {
            String error = registerHotkeys(edited);
            if (error != null) {
                registerHotkeys(shortcuts);
                return error;
            }
            shortcuts = edited;
            shortcuts.save(store());
            return null;
        });
    }

    private void openFromHotkey(HotKeyEvent event) {
        List<HttpRequestResponse> selected = event.selectedRequestResponses();
        if (event.messageEditorRequestResponse().isEmpty() && selected.size() >= 2) openTable(selected);
        else if (event.messageEditorRequestResponse().isPresent() || !selected.isEmpty())
            openExchanges(event.messageEditorRequestResponse(), selected, event.inputEvent());
    }

    private void quickCopy(HotKeyEvent event) {
        Optional<MessageEditorHttpRequestResponse> editor = event.messageEditorRequestResponse();
        List<HttpRequestResponse> selected = event.selectedRequestResponses();
        Settings settings = settings();
        String id = SnapshotLibrary.newId();
        BufferedImage image;
        String detail;
        if (editor.isEmpty() && selected.size() >= 2) {
            List<Long> times = selected.stream().map(rr -> timeMs(rr, List.of(), null)).toList();
            HttpRequest first = selected.get(0).request();
            String host = first.httpService().host();
            EditState state = new EditState();
            state.title = shortcuts.title(first.method(), first.pathWithoutQuery(), host, 0);
            SnapshotLibrary.Table table = new SnapshotLibrary.Table(SnapshotLibrary.rows(selected, times),
                    first.method(), first.url(), host, state, settings);
            library.saveTable(id, selected, times, first.method(), first.url(), host, new EditorWindow.Update(0, state, settings, false));
            image = SnapshotLibrary.render(table, settings, settings.exportScale);
            detail = selected.size() + " results · " + first.method() + " " + first.pathWithoutQuery();
        } else {
            HttpRequestResponse rr = editor.map(MessageEditorHttpRequestResponse::requestResponse)
                    .orElse(selected.isEmpty() ? null : selected.get(0));
            if (rr == null) return;
            Exchange exchange = editor.isPresent()
                    ? withSelection(toExchange(rr, selected, event.inputEvent()), editor.get()) : toExchange(rr, List.of(), null);
            HttpRequest request = rr.request();
            exchange.state.title = shortcuts.title(request.method(), request.pathWithoutQuery(), request.httpService().host(), exchange.status);
            library.saveExchange(id, rr, exchange, new EditorWindow.Update(0, exchange.state.copy(), settings, false));
            image = SnapshotLibrary.render(exchange, settings, settings.exportScale);
            detail = request.method() + " " + request.pathWithoutQuery();
        }
        java.awt.Frame owner = api.userInterface().swingUtils().suiteFrame();
        try {
            ImageExport.copy(image);
            toast(new Toast(owner, image, "Snapshot copied", detail, () -> reopen(id)));
        } catch (IOException | IllegalStateException e) {
            toast(new Toast(owner, null, "Copy failed", e.getMessage(), null));
        }
    }

    private void toast(Toast toast) {
        api.userInterface().applyThemeToComponent(toast.getContentPane());
        toast.popup();
    }

    private void openExchanges(ContextMenuEvent event) {
        openExchanges(event.messageEditorRequestResponse(), event.selectedRequestResponses(), event.inputEvent());
    }

    private void openExchanges(Optional<MessageEditorHttpRequestResponse> editorItem, List<HttpRequestResponse> selected,
                               InputEvent source) {
        List<HttpRequestResponse> items = new ArrayList<>();
        List<Exchange> exchanges = new ArrayList<>();
        editorItem.ifPresentOrElse(editor -> {
            items.add(editor.requestResponse());
            exchanges.add(withSelection(toExchange(editor.requestResponse(), selected, source), editor));
        }, () -> selected.forEach(rr -> {
            items.add(rr);
            exchanges.add(toExchange(rr, List.of(), null));
        }));
        List<String> ids = items.stream().map(rr -> SnapshotLibrary.newId()).toList();
        ExchangeWindow window = new ExchangeWindow(exchanges, settings(), store());
        window.onUpdate(u -> library.saveExchange(ids.get(u.item()), items.get(u.item()), exchanges.get(u.item()), u));
        show(window);
    }

    private void openTable(List<HttpRequestResponse> selected) {
        List<Long> times = selected.stream().map(rr -> timeMs(rr, List.of(), null)).toList();
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
            TableWindow window = new TableWindow(t.rows(), t.method(), t.url(), t.host(), t.settings(), store());
            window.restore(t.state());
            window.onUpdate(u -> library.saveTable(id, items, times, t.method(), t.url(), t.host(), u));
            show(window);
            return;
        }
        Exchange exchange = library.exchange(id);
        HttpRequestResponse item = library.item(id);
        ExchangeWindow window = new ExchangeWindow(List.of(exchange), library.settings(id), store());
        window.onUpdate(u -> library.saveExchange(id, item, exchange, u));
        show(window);
    }

    private void show(EditorWindow window) {
        window.useAi(ai);
        api.userInterface().applyThemeToComponent(window);
        window.open(api.userInterface().swingUtils().suiteFrame());
    }

    private Exchange toExchange(HttpRequestResponse rr, List<HttpRequestResponse> alternatives, InputEvent source) {
        HttpRequest request = rr.request();
        HttpResponse response = rr.response();
        return new Exchange(
                HttpText.parse(decode(request.toByteArray().getBytes()), true),
                response == null ? null : HttpText.parse(decode(response.toByteArray().getBytes()), false),
                request.method(), request.url(), request.httpService().host(),
                response == null ? 0 : response.statusCode(),
                response == null ? "" : response.reasonPhrase(),
                timeMs(rr, alternatives, source));
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

    private long timeMs(HttpRequestResponse rr, List<HttpRequestResponse> alternatives, InputEvent source) {
        List<HttpRequestResponse> candidates = new ArrayList<>(List.of(rr));
        candidates.addAll(alternatives);
        for (HttpRequestResponse candidate : candidates) {
            long ms = candidate.timingData().map(SnapshotExtension::millis).orElse(-1L);
            if (ms >= 0) return ms;
        }
        long shown = rr.response() == null ? -1 : StatusBarTime.millis(source, rr.response().toByteArray().length());
        if (shown >= 0) return shown;
        long proxied = proxyHistoryTime(rr);
        return proxied >= 0 ? proxied : timer.elapsedMs(rr.response());
    }

    private long proxyHistoryTime(HttpRequestResponse rr) {
        if (rr.response() == null) return -1;
        byte[] request = rr.request().toByteArray().getBytes();
        byte[] response = rr.response().toByteArray().getBytes();
        return api.proxy().history(h -> h.hasResponse()
                        && (same(h.response(), response) || same(h.originalResponse(), response))
                        && (same(h.finalRequest(), request) || same(h.request(), request)))
                .stream().map(h -> millis(h.timingData())).filter(ms -> ms >= 0).findFirst().orElse(-1L);
    }

    private static boolean same(HttpMessage message, byte[] bytes) {
        return message != null && message.toByteArray().length() == bytes.length && Arrays.equals(message.toByteArray().getBytes(), bytes);
    }

    private static long millis(TimingData timing) {
        if (timing == null) return -1;
        Duration d = timing.timeBetweenRequestSentAndEndOfResponse() != null
                ? timing.timeBetweenRequestSentAndEndOfResponse() : timing.timeBetweenRequestSentAndStartOfResponse();
        return d == null ? -1 : d.toMillis();
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
