package burpss.render;

import burpss.core.Anchor;
import burpss.core.AutoRedactor;
import burpss.core.EditState;
import burpss.core.Exchange;
import burpss.core.HttpText;
import burpss.core.Settings;
import burpss.core.Style;
import burpss.core.TextRange;
import burpss.core.Token;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

public final class ExchangeContent implements Content {

    private static final double PAD_X = 12, TITLE_H = 40, PAD_TOP = 8, PAD_BOTTOM = 12, GUTTER_PAD = 8, MIN_PANE = 240;

    public record Hit(int pane, int displayIndex, int origin) {
    }

    private final Exchange exchange;
    private final Settings settings;
    private final Theme theme;
    private final Font font, titleFont, italic;
    private final double cw, lh, ascent;
    private final List<Pane> panes = new ArrayList<>();
    private double width, height;

    private final class Pane {
        final int index;
        final HttpText source;
        final DisplayText display;
        final PaneLayout layout;
        final List<TextRange> autoRedactions;
        boolean[] hidden;
        double x, y, w, h;

        Pane(int index, HttpText source) {
            this.index = index;
            this.source = source;
            EditState state = exchange.state;
            display = DisplayText.build(source, settings, state.toggledHeaders.get(index));
            layout = PaneLayout.build(display, settings.wrapColumns, settings.maxBodyLines);
            autoRedactions = settings.autoRedact ? AutoRedactor.find(source, settings) : List.of();
            computeHidden();
        }

        double gutterWidth() {
            return settings.lineNumbers ? String.valueOf(layout.lastNumber()).length() * cw + 2 * GUTTER_PAD : 0;
        }

        double naturalWidth() {
            return Math.max(MIN_PANE, gutterWidth() + layout.columns() * cw + 2 * PAD_X);
        }

        double naturalHeight() {
            return TITLE_H + PAD_TOP + layout.lines().size() * lh + PAD_BOTTOM;
        }

        double textX() { return x + gutterWidth() + PAD_X; }
        double textY() { return y + TITLE_H + PAD_TOP; }

        List<TextRange> redactions() {
            List<TextRange> all = new ArrayList<>();
            for (TextRange r : autoRedactions) {
                if (!exchange.state.suppressedAuto.get(index).contains(r.key())) all.add(r);
            }
            all.addAll(exchange.state.manualRedactions.get(index));
            return all;
        }

        void computeHidden() {
            hidden = new boolean[display.length()];
            for (TextRange r : autoRedactions) {
                if (!exchange.state.suppressedAuto.get(index).contains(r.key())) hide(r, settings.revealChars);
            }
            for (TextRange r : exchange.state.manualRedactions.get(index)) hide(r, 0);
        }

        void hide(TextRange range, int revealChars) {
            List<Integer> covered = new ArrayList<>();
            for (int i = 0; i < display.length(); i++) {
                int o = display.origin(i);
                if (o >= 0 && range.contains(o)) covered.add(i);
            }
            int reveal = revealChars > 0 && covered.size() >= revealChars * 3 ? revealChars : 0;
            for (int k = 0; k < covered.size() - reveal; k++) hidden[covered.get(k)] = true;
        }
    }

    public ExchangeContent(Exchange exchange, Settings settings, Theme theme) {
        this.exchange = exchange;
        this.settings = settings;
        this.theme = theme;
        this.font = Fonts.mono(settings.fontSize);
        this.italic = font.deriveFont(Font.ITALIC);
        this.titleFont = Fonts.sans(15, true);
        this.cw = Fonts.width(font, "M");
        this.lh = Math.round(Fonts.height(font) * 1.45);
        this.ascent = Fonts.ascent(font);
        panes.add(new Pane(0, exchange.request));
        if (exchange.response != null) panes.add(new Pane(1, exchange.response));
    }

    @Override
    public void layout(double minWidth) {
        boolean sideBySide = settings.layout == Settings.Layout.SIDE_BY_SIDE;
        double natural = sideBySide
                ? panes.stream().mapToDouble(Pane::naturalWidth).sum()
                : panes.stream().mapToDouble(Pane::naturalWidth).max().orElse(MIN_PANE);
        width = Math.max(natural, minWidth);
        double extra = width - natural;
        double x = 0, y = 0;
        double tallest = panes.stream().mapToDouble(Pane::naturalHeight).max().orElse(0);
        for (Pane p : panes) {
            p.x = sideBySide ? x : 0;
            p.y = sideBySide ? 0 : y;
            p.w = sideBySide ? p.naturalWidth() + extra / panes.size() : width;
            p.h = sideBySide ? tallest : p.naturalHeight();
            x += p.w;
            y += p.h;
        }
        height = sideBySide ? tallest : y;
    }

    @Override public double width() { return width; }
    @Override public double height() { return height; }

    @Override
    public void paint(Graphics2D g) {
        for (Pane p : panes) {
            if (p.index > 0) {
                g.setColor(theme.divider);
                g.setStroke(new BasicStroke(1f));
                boolean side = settings.layout == Settings.Layout.SIDE_BY_SIDE;
                g.draw(side ? new Line2D.Double(p.x, p.y, p.x, p.y + p.h) : new Line2D.Double(p.x, p.y, p.x + p.w, p.y));
            }
            paintFrame(g, p);
            paintLines(g, p);
            paintRedactions(g, p);
        }
    }

    private void paintFrame(Graphics2D g, Pane p) {
        g.setFont(titleFont);
        g.setColor(theme.paneTitle);
        g.drawString(p.index == 0 ? "Request" : "Response", (float) (p.x + PAD_X + 4), (float) (p.y + TITLE_H / 2 + Fonts.ascent(titleFont) / 2));
        g.setColor(theme.divider);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(p.x, p.y + TITLE_H - 0.5, p.x + p.w, p.y + TITLE_H - 0.5));
        double gw = p.gutterWidth();
        if (gw == 0) return;
        g.setColor(theme.gutter);
        g.fill(new Rectangle2D.Double(p.x, p.y + TITLE_H, gw, p.h - TITLE_H));
        g.setColor(theme.divider);
        g.draw(new Line2D.Double(p.x + gw - 0.5, p.y + TITLE_H, p.x + gw - 0.5, p.y + p.h));
        g.setFont(font);
        g.setColor(theme.gutterText);
        List<PaneLayout.Line> lines = p.layout.lines();
        for (int row = 0; row < lines.size(); row++) {
            int number = lines.get(row).number();
            if (number == 0) continue;
            String label = String.valueOf(number);
            g.drawString(label, (float) (p.x + gw - GUTTER_PAD - label.length() * cw), baseline(p, row));
        }
    }

    private float baseline(Pane p, int row) {
        return (float) (p.textY() + row * lh + (lh - Fonts.height(font)) / 2 + ascent);
    }

    private void paintLines(Graphics2D g, Pane p) {
        List<PaneLayout.Line> lines = p.layout.lines();
        for (int row = 0; row < lines.size(); row++) {
            PaneLayout.Line line = lines.get(row);
            float baseline = baseline(p, row);
            if (line.synthetic() != null) {
                g.setFont(italic);
                g.setColor(theme.muted);
                g.drawString(line.synthetic(), (float) p.textX(), baseline);
                continue;
            }
            g.setFont(font);
            int i = line.start();
            while (i < line.end()) {
                Style style = p.display.style(i);
                boolean hide = p.hidden[i];
                int j = i + 1;
                while (j < line.end() && p.display.style(j) == style && p.hidden[j] == hide) j++;
                if (!hide) {
                    g.setColor(theme.color(style));
                    StringBuilder run = new StringBuilder();
                    for (int k = i; k < j; k++) run.append(p.display.charAt(k));
                    g.drawString(run.toString(), (float) (p.textX() + (i - line.start()) * cw), baseline);
                }
                i = j;
            }
        }
    }

    private void paintRedactions(Graphics2D g, Pane p) {
        List<PaneLayout.Line> lines = p.layout.lines();
        for (int row = 0; row < lines.size(); row++) {
            PaneLayout.Line line = lines.get(row);
            int i = line.start();
            while (i < line.end()) {
                if (!p.hidden[i]) {
                    i++;
                    continue;
                }
                Style style = p.display.style(i);
                int j = i;
                while (j < line.end() && p.hidden[j] && p.display.style(j) == style) j++;
                Rectangle2D r = new Rectangle2D.Double(p.textX() + (i - line.start()) * cw, p.textY() + row * lh,
                        (j - i) * cw, lh);
                paintRedaction(g, r, j - i, theme.color(style), p.index * 1_000_003L + i);
                i = j;
            }
        }
    }

    private void paintRedaction(Graphics2D g, Rectangle2D r, int chars, Color color, long seed) {
        RoundRectangle2D bar = new RoundRectangle2D.Double(r.getX() - 1, r.getY() + 2, r.getWidth() + 2, r.getHeight() - 4, 6, 6);
        switch (settings.redactStyle) {
            case SOLID -> {
                g.setColor(theme.redactSolid);
                g.fill(bar);
            }
            case DOTS -> {
                g.setFont(font);
                g.setColor(color);
                double baseline = (lh - Fonts.height(font)) / 2 + ascent;
                g.drawString("•".repeat(chars), (float) r.getX(), (float) (r.getY() + baseline));
            }
            case BLUR -> {
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 22));
                g.fill(bar);
                Blur.decoyText(g, r, chars, font, (lh - Fonts.height(font)) / 2 + ascent, color, seed);
            }
        }
    }

    @Override
    public Rectangle2D bounds(Anchor anchor) {
        if (!(anchor instanceof Anchor.Text t) || t.pane() >= panes.size()) return null;
        Pane p = panes.get(t.pane());
        Rectangle2D box = null;
        for (int i = 0; i < p.display.length(); i++) {
            int o = p.display.origin(i);
            if (o < t.start() || o >= t.end() || p.display.charAt(i) == '\n') continue;
            int row = p.layout.lineOf(i);
            if (row < 0) continue;
            int col = i - p.layout.lines().get(row).start();
            Rectangle2D cell = new Rectangle2D.Double(p.textX() + col * cw, p.textY() + row * lh, cw, lh);
            if (box == null) box = cell;
            else box.add(cell);
        }
        return box;
    }

    @Override
    public int ink(Rectangle2D area) {
        int count = 0;
        for (Pane p : panes) {
            List<PaneLayout.Line> lines = p.layout.lines();
            int firstRow = Math.max(0, (int) Math.floor((area.getY() - p.textY()) / lh));
            int lastRow = Math.min(lines.size() - 1, (int) Math.floor((area.getMaxY() - p.textY()) / lh));
            int firstCol = (int) Math.floor((area.getX() - p.textX()) / cw);
            int lastCol = (int) Math.ceil((Math.min(area.getMaxX(), p.x + p.w) - p.textX()) / cw);
            if (area.getMaxX() < p.x || area.getX() > p.x + p.w) continue;
            for (int row = firstRow; row <= lastRow; row++) {
                PaneLayout.Line line = lines.get(row);
                for (int col = Math.max(0, firstCol); col < Math.min(line.length(), lastCol); col++) {
                    if (line.synthetic() != null || !Character.isWhitespace(p.display.charAt(line.start() + col))) count++;
                }
            }
        }
        return count;
    }

    public Hit hitAt(Point2D pt) {
        for (Pane p : panes) {
            if (pt.getX() < p.x || pt.getX() >= p.x + p.w || pt.getY() < p.y || pt.getY() >= p.y + p.h) continue;
            int row = (int) Math.floor((pt.getY() - p.textY()) / lh);
            int col = (int) Math.floor((pt.getX() - p.textX()) / cw);
            int index = p.layout.indexAt(row, col);
            if (index < 0) return null;
            return new Hit(p.index, index, p.display.nearestOrigin(index));
        }
        return null;
    }

    @Override
    public Anchor anchorAt(Point2D pt) {
        Hit hit = hitAt(pt);
        if (hit == null || hit.origin() < 0) return null;
        Token token = exchange.pane(hit.pane()).tokenAt(hit.origin());
        return token == null ? null : new Anchor.Text(hit.pane(), token.start(), token.end());
    }

    @Override
    public Anchor anchorBetween(Point2D from, Point2D to) {
        TextRange range = rangeBetween(from, to);
        Hit hit = hitAt(from);
        return range == null ? null : new Anchor.Text(hit.pane(), range.start(), range.end());
    }

    public TextRange rangeBetween(Point2D from, Point2D to) {
        Hit a = hitAt(from), b = hitAt(to);
        if (a == null || b == null || a.pane() != b.pane()) return null;
        int lo = Math.min(a.origin(), b.origin());
        int hi = Math.max(a.origin(), b.origin());
        return lo < 0 ? null : new TextRange(lo, hi + 1);
    }

    public List<TextRange> redactions(int pane) {
        return pane < panes.size() ? panes.get(pane).redactions() : List.of();
    }

    public List<TextRange> autoRedactions(int pane) {
        return pane < panes.size() ? panes.get(pane).autoRedactions : List.of();
    }
}
