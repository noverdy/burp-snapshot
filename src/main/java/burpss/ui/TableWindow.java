package burpss.ui;

import burpss.ai.AiMarkup;
import burpss.core.EditState;
import burpss.core.History;
import burpss.core.ResultRow;
import burpss.core.Settings;
import burpss.render.Content;
import burpss.render.HeaderInfo;
import burpss.render.TableContent;
import burpss.render.Theme;

import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import java.awt.Window;
import java.awt.geom.Point2D;
import java.util.List;

public final class TableWindow extends EditorWindow {

    private final List<ResultRow> rows;
    private final String method, url, host;
    private final EditState state = new EditState();
    private final History history = new History(state);
    private TableContent content;

    public TableWindow(Window owner, List<ResultRow> rows, String method, String url, String host, Settings settings, Settings.Store store) {
        super(owner, "Snapshot – results table", settings, store, true);
        this.rows = rows;
        this.method = method;
        this.url = url;
        this.host = host;
    }

    @Override EditState state() { return state; }

    public void restore(EditState saved) {
        state.restore(saved);
    }
    @Override History history() { return history; }

    @Override
    Content buildContent(Theme theme) {
        content = new TableContent(rows, state, settings, theme);
        return content;
    }

    @Override
    HeaderInfo header() {
        return new HeaderInfo(state.title, state.caption, method, url, host, 0, "", -1);
    }

    @Override
    String aiSystem() {
        return AiMarkup.TABLE_SYSTEM;
    }

    @Override
    String aiPrompt(String context) {
        return AiMarkup.tablePrompt(context, method, url, content);
    }

    @Override
    AiMarkup.Result aiResult(String reply) {
        return AiMarkup.tableResult(reply, content, settings.markColor);
    }

    @Override
    boolean timeAvailable() {
        return rows.stream().anyMatch(r -> r.timeMs() >= 0);
    }

    @Override
    String fileName() {
        return ImageExport.fileName(host, method, "results");
    }

    @Override
    void addContentMenuItems(JPopupMenu menu, Point2D cp) {
        int number = content.rowAt(cp);
        if (number > 0) {
            ResultRow row = rows.get(number - 1);
            menu.add(item("Edit payload…", () -> {
                Object value = JOptionPane.showInputDialog(this, "Payload label for row " + number, "Edit payload",
                        JOptionPane.PLAIN_MESSAGE, null, null, content.payload(row));
                if (value != null) edit(() -> state.payloadOverrides.put(number, value.toString()));
            }));
            menu.add(item("Hide row", () -> edit(() -> state.hiddenRows.add(number))));
        }
        if (!state.hiddenRows.isEmpty()) {
            menu.add(item("Show " + state.hiddenRows.size() + " hidden row(s)", () -> edit(state.hiddenRows::clear)));
        }
    }
}
