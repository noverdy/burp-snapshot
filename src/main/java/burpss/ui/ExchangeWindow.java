package burpss.ui;

import burpss.ai.AiMarkup;
import burpss.core.EditState;
import burpss.core.Exchange;
import burpss.core.History;
import burpss.core.HttpText;
import burpss.core.Names;
import burpss.core.Settings;
import burpss.core.TextRange;
import burpss.core.Token;
import burpss.render.Content;
import burpss.render.DisplayText;
import burpss.render.ExchangeContent;
import burpss.render.HeaderInfo;
import burpss.render.Theme;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.net.URI;
import java.util.List;
import java.util.Set;

public final class ExchangeWindow extends EditorWindow {

    private final List<Exchange> exchanges;
    private Exchange current;
    private final List<Settings> itemSettings = new java.util.ArrayList<>();
    private ExchangeContent content;

    public ExchangeWindow(List<Exchange> exchanges, Settings settings, Settings.Store store) {
        super("Snapshot", settings, store, false);
        this.exchanges = exchanges;
        this.current = exchanges.get(0);
        exchanges.forEach(e -> itemSettings.add(settings.copy()));
        if (exchanges.size() > 1) {
            JComboBox<String> picker = new JComboBox<>(exchanges.stream().map(Exchange::label).toArray(String[]::new));
            picker.setMaximumSize(new java.awt.Dimension(520, picker.getPreferredSize().height));
            picker.addActionListener(e -> {
                flush();
                itemSettings.set(item(), settings.copy());
                current = exchanges.get(picker.getSelectedIndex());
                settings.copyFrom(itemSettings.get(item()));
                reloadSettings();
                syncTitleField();
                rebuild();
            });
            toolbar.add(new JLabel("Item "));
            toolbar.add(picker);
        }
    }

    @Override EditState state() { return current.state; }
    @Override int item() { return exchanges.indexOf(current); }
    @Override History history() { return current.history; }

    @Override
    Content buildContent(Theme theme) {
        content = new ExchangeContent(current, settings, theme);
        return content;
    }

    @Override
    HeaderInfo header() {
        return new HeaderInfo(current.state.title, current.state.caption, current.method, current.url, current.host,
                current.status, current.reason, current.timeMs);
    }

    @Override
    String aiSystem() {
        return AiMarkup.EXCHANGE_SYSTEM;
    }

    @Override
    String aiPrompt(String context) {
        return AiMarkup.exchangePrompt(context, current, content);
    }

    @Override
    AiMarkup.Result aiResult(String reply) {
        return AiMarkup.exchangeResult(reply, current, content, settings.markColor);
    }

    @Override
    String fileName() {
        String path;
        try {
            path = URI.create(current.url).getPath();
        } catch (IllegalArgumentException e) {
            path = "";
        }
        return ImageExport.fileName(current.host, current.method, path == null ? "" : path);
    }

    @Override
    void redactClick(Point2D cp) {
        ExchangeContent.Hit hit = content.hitAt(cp);
        if (hit == null || hit.origin() < 0) return;
        int pane = hit.pane();
        EditState s = current.state;
        for (TextRange r : s.manualRedactions.get(pane)) {
            if (r.contains(hit.origin())) {
                edit(() -> s.manualRedactions.get(pane).remove(r));
                return;
            }
        }
        for (TextRange r : content.redactions(pane)) {
            if (r.contains(hit.origin())) {
                edit(() -> s.suppressedAuto.get(pane).add(r.key()));
                return;
            }
        }
        Token token = current.pane(pane).tokenAt(hit.origin());
        if (token == null || !token.hasValue()) return;
        TextRange value = new TextRange(token.valueStart(), token.valueEnd());
        if (s.suppressedAuto.get(pane).contains(value.key())) {
            edit(() -> s.suppressedAuto.get(pane).remove(value.key()));
        } else {
            edit(() -> s.manualRedactions.get(pane).add(value));
        }
    }

    @Override
    void redactDrag(Point2D from, Point2D to) {
        TextRange range = content.rangeBetween(from, to);
        ExchangeContent.Hit hit = content.hitAt(from);
        if (range != null) edit(() -> current.state.manualRedactions.get(hit.pane()).add(range));
    }

    @Override
    void addContentMenuItems(JPopupMenu menu, Point2D cp) {
        ExchangeContent.Hit hit = content.hitAt(cp);
        if (hit != null && hit.origin() >= 0) {
            HttpText text = current.pane(hit.pane());
            HttpText.HeaderLine header = text.headerAt(hit.origin());
            if (header != null) {
                menu.add(item("Hide header “" + header.name() + "”",
                        () -> edit(() -> current.state.toggledHeaders.get(hit.pane()).add(header.lineStart()))));
            }
        }
        for (int pane = 0; pane < 2; pane++) {
            HttpText text = current.pane(pane);
            if (text == null) continue;
            int p = pane;
            long hidden = text.headers().stream()
                    .filter(h -> DisplayText.isHidden(h, Names.split(settings.hideHeaders), current.state.toggledHeaders.get(p)))
                    .count();
            if (hidden > 0) {
                menu.add(item("Show " + hidden + " hidden " + (pane == 0 ? "request" : "response") + " header" + (hidden == 1 ? "" : "s"),
                        () -> edit(() -> showAllHeaders(p))));
            }
        }
    }

    private void showAllHeaders(int pane) {
        List<String> patterns = Names.split(settings.hideHeaders);
        Set<Integer> toggled = current.state.toggledHeaders.get(pane);
        toggled.clear();
        for (HttpText.HeaderLine h : current.pane(pane).headers()) {
            if (Names.globMatches(patterns, h.name())) toggled.add(h.lineStart());
        }
    }
}
