package burpss;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.persistence.PersistedList;
import burp.api.montoya.persistence.PersistedObject;
import burpss.core.DecodedText;
import burpss.core.EditState;
import burpss.core.Exchange;
import burpss.core.HttpText;
import burpss.core.PayloadDiff;
import burpss.core.ResultRow;
import burpss.core.Settings;
import burpss.core.StateCodec;
import burpss.render.Content;
import burpss.render.ExchangeContent;
import burpss.render.HeaderInfo;
import burpss.render.Scene;
import burpss.render.TableContent;
import burpss.render.Theme;
import burpss.ui.EditorWindow;
import burpss.ui.SnapshotsTab;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class SnapshotLibrary implements SnapshotsTab.Source {

    static final int MAX_DRAFTS = 20;
    private static final String EXCHANGE = "exchange", TABLE = "table";

    record Table(List<ResultRow> rows, String method, String url, String host, EditState state) {
    }

    private final PersistedObject root;
    private final Supplier<Settings> settings;
    private Consumer<String> opener = id -> { };
    private Runnable onChange = () -> { };

    SnapshotLibrary(PersistedObject projectData, Supplier<Settings> settings) {
        if (projectData.getChildObject("snapshots") == null) projectData.setChildObject("snapshots", PersistedObject.persistedObject());
        this.root = projectData.getChildObject("snapshots");
        this.settings = settings;
    }

    void onOpen(Consumer<String> opener) {
        this.opener = opener;
    }

    void onChange(Runnable listener) {
        this.onChange = listener;
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    void saveExchange(String id, HttpRequestResponse item, Exchange exchange, EditorWindow.Update update) {
        save(id, update, entry -> {
            entry.setString("kind", EXCHANGE);
            entry.setHttpRequestResponse("item", item);
            entry.setString("method", exchange.method);
            entry.setString("url", exchange.url);
            entry.setString("host", exchange.host);
            entry.setInteger("statusCode", exchange.status);
            entry.setString("reason", exchange.reason);
            entry.setLong("timeMs", exchange.timeMs);
        });
    }

    void saveTable(String id, List<HttpRequestResponse> items, List<Long> times, String method, String url, String host,
                   EditorWindow.Update update) {
        save(id, update, entry -> {
            PersistedList<HttpRequestResponse> stored = PersistedList.persistedHttpRequestResponseList();
            stored.addAll(items);
            PersistedList<Long> storedTimes = PersistedList.persistedLongList();
            storedTimes.addAll(times);
            entry.setString("kind", TABLE);
            entry.setHttpRequestResponseList("items", stored);
            entry.setLongList("times", storedTimes);
            entry.setString("method", method);
            entry.setString("url", url);
            entry.setString("host", host);
        });
    }

    private void save(String id, EditorWindow.Update update, Consumer<PersistedObject> content) {
        PersistedObject entry = root.getChildObject(id);
        long now = System.currentTimeMillis();
        if (entry == null) {
            entry = PersistedObject.persistedObject();
            content.accept(entry);
            entry.setLong("created", now);
            entry.setString("status", "draft");
        }
        if (update.exported()) entry.setString("status", "exported");
        entry.setString("title", update.state().title);
        entry.setString("state", Base64.getEncoder().encodeToString(StateCodec.encode(update.state())));
        entry.setLong("updated", now);
        root.setChildObject(id, entry);
        pruneDrafts();
        onChange.run();
    }

    private void pruneDrafts() {
        List<SnapshotsTab.Entry> drafts = entries().stream().filter(e -> !e.exported()).toList();
        for (int i = MAX_DRAFTS; i < drafts.size(); i++) root.deleteChildObject(drafts.get(i).id());
    }

    boolean isTable(String id) {
        PersistedObject entry = root.getChildObject(id);
        return entry != null && TABLE.equals(entry.getString("kind"));
    }

    HttpRequestResponse item(String id) {
        return root.getChildObject(id).getHttpRequestResponse("item");
    }

    List<HttpRequestResponse> items(String id) {
        return new ArrayList<>(root.getChildObject(id).getHttpRequestResponseList("items"));
    }

    List<Long> times(String id) {
        return new ArrayList<>(root.getChildObject(id).getLongList("times"));
    }

    Exchange exchange(String id) {
        PersistedObject entry = root.getChildObject(id);
        HttpRequestResponse item = entry.getHttpRequestResponse("item");
        HttpResponse response = item.response();
        Exchange exchange = new Exchange(
                HttpText.parse(decode(item.request().toByteArray().getBytes()), true),
                response == null ? null : HttpText.parse(decode(response.toByteArray().getBytes()), false),
                entry.getString("method"), entry.getString("url"), entry.getString("host"),
                entry.getInteger("statusCode"), entry.getString("reason"), entry.getLong("timeMs"));
        exchange.state.restore(state(entry));
        return exchange;
    }

    Table table(String id) {
        PersistedObject entry = root.getChildObject(id);
        return new Table(rows(items(id), times(id)), entry.getString("method"), entry.getString("url"), entry.getString("host"), state(entry));
    }

    static List<ResultRow> rows(List<HttpRequestResponse> items, List<Long> times) {
        List<String> payloads = PayloadDiff.labels(items.stream().map(rr -> decode(rr.request().toByteArray().getBytes())).toList());
        List<ResultRow> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            HttpResponse response = items.get(i).response();
            rows.add(new ResultRow(i + 1, payloads.get(i),
                    response == null ? 0 : response.statusCode(),
                    response == null ? 0 : response.toByteArray().length(),
                    times.get(i),
                    response == null ? "" : decode(response.toByteArray().getBytes())));
        }
        return rows;
    }

    static String decode(byte[] bytes) {
        return DecodedText.decode(bytes).text();
    }

    private static EditState state(PersistedObject entry) {
        String encoded = entry.getString("state");
        return encoded == null ? new EditState() : StateCodec.decode(Base64.getDecoder().decode(encoded));
    }

    @Override
    public List<SnapshotsTab.Entry> entries() {
        List<SnapshotsTab.Entry> out = new ArrayList<>();
        for (String id : root.childObjectKeys()) {
            PersistedObject entry = root.getChildObject(id);
            String detail = entry.getString("method") + " " + entry.getString("url");
            if (TABLE.equals(entry.getString("kind"))) detail = "Results table · " + detail;
            Long updated = entry.getLong("updated");
            out.add(new SnapshotsTab.Entry(id, String.valueOf(entry.getString("title")), detail,
                    updated == null ? 0 : updated, "exported".equals(entry.getString("status"))));
        }
        out.sort(Comparator.comparingLong(SnapshotsTab.Entry::updated).reversed());
        return out;
    }

    @Override
    public BufferedImage preview(String id) {
        if (root.getChildObject(id) == null) return null;
        Settings s = settings.get();
        Theme theme = Theme.of(s);
        if (isTable(id)) {
            Table t = table(id);
            Content content = new TableContent(t.rows(), t.state(), s, theme);
            return new Scene(content, new HeaderInfo(t.state().title, t.method(), t.url(), t.host(), 0, "", -1), t.state().marks, s, null).toImage(1);
        }
        Exchange e = exchange(id);
        HeaderInfo header = new HeaderInfo(e.state.title, e.method, e.url, e.host, e.status, e.reason, e.timeMs);
        return new Scene(new ExchangeContent(e, s, theme), header, e.state.marks, s, null).toImage(1);
    }

    @Override
    public void open(String id) {
        opener.accept(id);
    }

    @Override
    public void delete(String id) {
        root.deleteChildObject(id);
        onChange.run();
    }
}
