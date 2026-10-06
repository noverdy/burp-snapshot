package burpss.render;

import burpss.core.Anchor;
import burpss.core.EditState;
import burpss.core.ResultRow;
import burpss.core.Settings;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TableContent implements Content {

    private static final double ROW_H = 30, HEAD_H = 34, PAD = 14, MAX_PAYLOAD = 460;
    private static final int PAYLOAD = 1;

    private final List<ResultRow> rows = new ArrayList<>();
    private final EditState state;
    private final Theme theme;
    private final String contains;
    private final Font mono, head;
    private final List<String> columns = new ArrayList<>();
    private double[] widths;
    private double width;

    public TableContent(List<ResultRow> all, EditState state, Settings settings, Theme theme) {
        this.state = state;
        this.theme = theme;
        this.contains = settings.containsText.strip();
        this.mono = Fonts.mono(settings.fontSize);
        this.head = Fonts.sans(11, true);
        for (ResultRow r : all) if (!state.hiddenRows.contains(r.number())) rows.add(r);
        columns.addAll(List.of("#", "Payload", "Status", "Length"));
        if (settings.showTime && all.stream().anyMatch(r -> r.timeMs() >= 0)) columns.add("Time");
        if (!contains.isEmpty()) columns.add("Contains “" + contains + "”");
    }

    public int rowAt(Point2D p) {
        int i = (int) Math.floor((p.getY() - HEAD_H) / ROW_H);
        return p.getY() < HEAD_H || i < 0 || i >= rows.size() || p.getX() < 0 || p.getX() > width ? -1 : rows.get(i).number();
    }

    public List<Integer> rowNumbers() {
        return rows.stream().map(ResultRow::number).toList();
    }

    public List<String> describeRows() {
        List<String> out = new ArrayList<>();
        for (ResultRow r : rows) {
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < columns.size(); c++) {
                line.append(c == 0 ? "" : " | ").append(columns.get(c)).append(": ").append(cell(r, c));
            }
            out.add(line.toString());
        }
        return out;
    }

    public String payload(ResultRow row) {
        return state.payloadOverrides.getOrDefault(row.number(), row.payload());
    }

    private String cell(ResultRow r, int col) {
        return switch (columns.get(col)) {
            case "#" -> String.valueOf(r.number());
            case "Payload" -> payload(r).isEmpty() ? "—" : payload(r).replace("\r", "").replace("\n", "⏎");
            case "Status" -> r.status() > 0 ? String.valueOf(r.status()) : "—";
            case "Length" -> String.valueOf(r.length());
            case "Time" -> r.timeMs() >= 0 ? r.timeMs() + " ms" : "—";
            default -> r.response().toLowerCase(Locale.ROOT).contains(contains.toLowerCase(Locale.ROOT)) ? "✓" : "✗";
        };
    }

    private boolean rightAligned(int col) {
        return col != PAYLOAD && col > 0;
    }

    @Override
    public void layout(double minWidth) {
        widths = new double[columns.size()];
        for (int c = 0; c < columns.size(); c++) {
            double w = Fonts.width(head, columns.get(c).toUpperCase(Locale.ROOT));
            for (ResultRow r : rows) w = Math.max(w, Fonts.width(mono, cell(r, c)));
            widths[c] = (c == PAYLOAD ? Math.min(MAX_PAYLOAD, Math.max(w, 160)) : w) + 2 * PAD;
        }
        double natural = 0;
        for (double w : widths) natural += w;
        widths[PAYLOAD] += Math.max(0, minWidth - natural);
        width = Math.max(natural, minWidth);
    }

    @Override public double width() { return width; }
    @Override public double height() { return HEAD_H + rows.size() * ROW_H + 8; }

    @Override
    public void paint(Graphics2D g) {
        g.setColor(theme.headerBar);
        g.fill(new Rectangle2D.Double(0, 0, width, HEAD_H));
        g.setColor(theme.divider);
        g.draw(new Line2D.Double(0, HEAD_H - 0.5, width, HEAD_H - 0.5));
        double x = 0;
        g.setFont(head);
        g.setColor(theme.muted);
        for (int c = 0; c < columns.size(); c++) {
            String label = columns.get(c).toUpperCase(Locale.ROOT);
            drawAligned(g, head, label, x, widths[c], HEAD_H / 2, rightAligned(c));
            x += widths[c];
        }
        for (int i = 0; i < rows.size(); i++) {
            ResultRow r = rows.get(i);
            double top = HEAD_H + i * ROW_H;
            if (i % 2 == 1) {
                g.setColor(withAlpha(theme.headerBar, 160));
                g.fill(new Rectangle2D.Double(0, top, width, ROW_H));
            }
            x = 0;
            for (int c = 0; c < columns.size(); c++) {
                String text = Scene.ellipsize(cell(r, c), mono, widths[c] - 2 * PAD);
                g.setFont(mono);
                g.setColor(cellColor(r, c, text));
                drawAligned(g, mono, text, x, widths[c], top + ROW_H / 2, rightAligned(c));
                x += widths[c];
            }
        }
    }

    private Color cellColor(ResultRow r, int col, String text) {
        return switch (columns.get(col)) {
            case "#" , "Time" -> theme.muted;
            case "Status" -> r.status() > 0 ? Theme.statusColor(r.status()) : theme.muted;
            case "Payload" -> theme.color(burpss.core.Style.STRING);
            case "Length" -> theme.text;
            default -> text.equals("✓") ? Theme.statusColor(200) : theme.muted;
        };
    }

    private static void drawAligned(Graphics2D g, Font f, String text, double x, double w, double mid, boolean right) {
        double tx = right ? x + w - PAD - Fonts.width(f, text) : x + PAD;
        g.drawString(text, (float) tx, (float) (mid + Fonts.ascent(f) / 2 - 1));
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private int displayIndex(int number) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).number() == number) return i;
        return -1;
    }

    @Override
    public Rectangle2D bounds(Anchor anchor) {
        if (!(anchor instanceof Anchor.Rows r)) return null;
        int first = -1, last = -1;
        for (int i = 0; i < rows.size(); i++) {
            int n = rows.get(i).number();
            if (n >= r.first() && n <= r.last()) {
                if (first < 0) first = i;
                last = i;
            }
        }
        if (first < 0) return null;
        return new Rectangle2D.Double(4, HEAD_H + first * ROW_H + 2, width - 8, (last - first + 1) * ROW_H - 4);
    }

    @Override
    public Anchor anchorAt(Point2D p) {
        int n = rowAt(p);
        return n < 0 ? null : new Anchor.Rows(n, n);
    }

    @Override
    public Anchor anchorBetween(Point2D from, Point2D to) {
        int a = rowAt(from), b = rowAt(new Point2D.Double(Math.max(0, Math.min(to.getX(), width)), to.getY()));
        if (a < 0 || b < 0) return null;
        int ia = displayIndex(a), ib = displayIndex(b);
        return new Anchor.Rows(rows.get(Math.min(ia, ib)).number(), rows.get(Math.max(ia, ib)).number());
    }

    @Override
    public int ink(Rectangle2D area) {
        int count = area.intersects(0, 0, width, HEAD_H) ? 20 : 0;
        for (int i = 0; i < rows.size(); i++) {
            double top = HEAD_H + i * ROW_H;
            if (top + ROW_H < area.getY() || top > area.getMaxY()) continue;
            double x = 0;
            for (int c = 0; c < columns.size(); c++) {
                String text = cell(rows.get(i), c);
                double tw = Fonts.width(mono, text);
                double tx = rightAligned(c) ? x + widths[c] - PAD - tw : x + PAD;
                if (area.intersects(tx, top, tw, ROW_H)) count += text.length();
                x += widths[c];
            }
        }
        return count;
    }
}
