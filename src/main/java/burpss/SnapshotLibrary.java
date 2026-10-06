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
import burpss.core.Template;
import burpss.render.Content;
import burpss.render.ExchangeContent;
import burpss.render.HeaderInfo;
import burpss.render.Scene;
import burpss.render.TableContent;
import burpss.render.Theme;
import burpss.ui.EditorWindow;
import burpss.ui.SnapshotsTab;
import burpss.ui.TemplateStore;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class SnapshotLibrary implements SnapshotsTab.Source, TemplateStore {

    static final int MAX_DRAFTS = 20;
    static final int MAX_MESSAGE_BYTES = 1 << 20;
    private static final String EXCHANGE = "exchange", TABLE = "table", PREFIX = "snapshot.", TEMPLATE = "template.";

    record Table(List<ResultRow> rows, String method, String url, String host, EditState state, Settings settings) {
    }

    private final PersistedObject project;
    private final Supplier<Settings> defaults;
    private Consumer<String> opener = id -> { };
    private Runnable onChange = () -> { };

    SnapshotLibrary(PersistedObject projectData, Supplier<Settings> defaults) {
        this.project = projectData;
        this.defaults = defaults;
    }

    private PersistedObject entry(String id) {
        return project.getChildObject(PREFIX + id);
    }

    void onOpen(Consumer<String> opener) {
        this.opener = opener;
    }

    void onChange(Runnable listener) {
        this.onChange = listener;
    }

    @Override
    public List<Template> templates() {
        List<Template> out = new ArrayList<>();
        for (String key : project.childObjectKeys()) {
            if (!key.startsWith(TEMPLATE)) continue;
            String data = project.getChildObject(key).getString("data");
            if (data == null) continue;
            try {
                out.add(Template.decode(data));
            } catch (RuntimeException ignored) {
            }
        }
        return out;
    }

    @Override
    public void saveTemplate(Template template) {
        PersistedObject entry = PersistedObject.persistedObject();
        entry.setString("data", template.encode());
        project.setChildObject(TEMPLATE + template.id, entry);
    }

    @Override
    public void deleteTemplate(String id) {
        project.deleteChildObject(TEMPLATE + id);
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
        PersistedObject entry = entry(id);
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
        entry.setString("settings", update.settings().encode());
        entry.setLong("updated", now);
        project.setChildObject(PREFIX + id, entry);
        pruneDrafts();
        onChange.run();
    }

    private void pruneDrafts() {
        List<SnapshotsTab.Entry> drafts = entries().stream().filter(e -> !e.exported()).toList();
        for (int i = MAX_DRAFTS; i < drafts.size(); i++) project.deleteChildObject(PREFIX + drafts.get(i).id());
    }

    boolean isTable(String id) {
        PersistedObject entry = entry(id);
        return entry != null && TABLE.equals(entry.getString("kind"));
    }

    HttpRequestResponse item(String id) {
        return entry(id).getHttpRequestResponse("item");
    }

    List<HttpRequestResponse> items(String id) {
        return new ArrayList<>(entry(id).getHttpRequestResponseList("items"));
    }

    List<Long> times(String id) {
        return new ArrayList<>(entry(id).getLongList("times"));
    }

    Settings settings(String id) {
        return Settings.decode(entry(id).getString("settings"), defaults.get());
    }

    Exchange exchange(String id) {
        PersistedObject entry = entry(id);
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
        PersistedObject entry = entry(id);
        return new Table(rows(items(id), times(id)), entry.getString("method"), entry.getString("url"), entry.getString("host"),
                state(entry), settings(id));
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
        int kept = keptLength(bytes);
        if (kept == bytes.length) return DecodedText.decode(bytes).text();
        return DecodedText.decode(Arrays.copyOf(bytes, kept)).text()
                + String.format("\n… %,d more bytes not shown", bytes.length - kept);
    }

    static int keptLength(byte[] bytes) {
        if (bytes.length <= MAX_MESSAGE_BYTES) return bytes.length;
        int cut = MAX_MESSAGE_BYTES;
        while (cut > 0 && (bytes[cut] & 0xC0) == 0x80) cut--;
        return cut;
    }

    private static EditState state(PersistedObject entry) {
        String encoded = entry.getString("state");
        return encoded == null ? new EditState() : StateCodec.decode(Base64.getDecoder().decode(encoded));
    }

    @Override
    public List<SnapshotsTab.Entry> entries() {
        List<SnapshotsTab.Entry> out = new ArrayList<>();
        for (String key : project.childObjectKeys()) {
            if (!key.startsWith(PREFIX)) continue;
            String id = key.substring(PREFIX.length());
            PersistedObject entry = project.getChildObject(key);
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
        if (entry(id) == null) return null;
        Settings s = settings(id);
        return isTable(id) ? render(table(id), s, 1) : render(exchange(id), s, 1);
    }

    static BufferedImage render(Exchange e, Settings s, int scale) {
        HeaderInfo header = new HeaderInfo(e.state.title, e.state.caption, e.method, e.url, e.host, e.status, e.reason, e.timeMs);
        return new Scene(new ExchangeContent(e, s, Theme.of(s)), header, e.state.marks, s, logo(s)).toImage(scale);
    }

    static BufferedImage render(Table t, Settings s, int scale) {
        Content content = new TableContent(t.rows(), t.state(), s, Theme.of(s));
        HeaderInfo header = new HeaderInfo(t.state().title, t.state().caption, t.method(), t.url(), t.host(), 0, "", -1);
        return new Scene(content, header, t.state().marks, s, logo(s)).toImage(scale);
    }

    private static Image logo(Settings s) {
        try {
            return s.watermarkLogo.isBlank() ? null : ImageIO.read(new File(s.watermarkLogo));
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void open(String id) {
        opener.accept(id);
    }

    @Override
    public void delete(String id) {
        project.deleteChildObject(PREFIX + id);
        onChange.run();
    }
}
