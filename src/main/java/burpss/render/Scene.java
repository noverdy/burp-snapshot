package burpss.render;

import burpss.core.Mark;
import burpss.core.Settings;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class Scene {

    private static final double HEADER_H = 44, TITLE_H = 36, CARD_PAD = 16, CALLOUT_W = 340, MIN_CARD_W = 520;

    private final Content content;
    private final HeaderInfo header;
    private final List<Mark> marks;
    private final Settings settings;
    private final Theme theme;
    private final Image logo;
    private final Font uiFont = Fonts.sans(13, false), uiBold = Fonts.sans(13, true), titleFont = Fonts.sans(15, true);
    private final Font mono = Fonts.mono(13);

    private final Rectangle2D card;
    private final Rectangle2D contentArea;
    private final double headerH, titleH, legendH, captionH;
    private final List<String> captionLines;
    private final List<Rectangle2D> markBoxes = new ArrayList<>();
    private final List<List<Rectangle2D>> markSegments = new ArrayList<>();
    private final List<Rectangle2D> callouts = new ArrayList<>();
    private final List<List<String>> calloutLines = new ArrayList<>();
    private final Rectangle2D imageBounds;

    public Scene(Content content, HeaderInfo header, List<Mark> marks, Settings settings, Image logo) {
        this.content = content;
        this.header = header;
        this.marks = marks;
        this.settings = settings;
        this.theme = Theme.of(settings);
        this.logo = logo;

        titleH = header.title().isBlank() ? 0 : TITLE_H;
        headerH = settings.showHeaderBar ? HEADER_H : 0;
        content.layout(MIN_CARD_W);
        double cardW = content.width();
        legendH = legendHeight(cardW);
        captionLines = settings.showCaption && !header.caption().isBlank()
                ? wrap(header.caption(), uiFont, cardW - 2 * CARD_PAD) : List.of();
        captionH = captionLines.isEmpty() ? 0 : 2 * CARD_PAD - 4 + captionLines.size() * lineHeight(uiFont);
        double margin = settings.frame == Settings.Frame.SHOWCASE ? 56 : 0;
        card = new Rectangle2D.Double(margin, margin, cardW, titleH + headerH + content.height() + legendH + captionH);
        contentArea = new Rectangle2D.Double(card.getX(), card.getY() + titleH + headerH, content.width(), content.height());

        Rectangle2D bounds = (Rectangle2D) card.clone();
        for (Mark m : marks) {
            Rectangle2D local = content.bounds(m.anchor);
            markBoxes.add(local == null ? null : pad(local));
            markSegments.add(content.segments(m.anchor).stream().map(this::pad).toList());
        }
        for (int i = 0; i < marks.size(); i++) {
            Mark m = marks.get(i);
            Rectangle2D box = markBoxes.get(i);
            List<String> lines = box != null && showCallout(m) ? wrap(m.note, uiFont, CALLOUT_W - 44) : List.of();
            calloutLines.add(lines);
            Rectangle2D callout = null;
            if (!lines.isEmpty()) {
                double w = 44 + lines.stream().mapToDouble(l -> Fonts.width(uiFont, l)).max().orElse(0);
                double h = 16 + lines.size() * lineHeight(uiFont);
                callout = m.calloutOffset == null
                        ? autoPlace(i, w, h)
                        : new Rectangle2D.Double(box.getX() + m.calloutOffset.getX(), box.getY() + m.calloutOffset.getY(), w, h);
                bounds.add(new Rectangle2D.Double(callout.getX() - 12, callout.getY() - 12, w + 24, h + 24));
            }
            callouts.add(callout);
        }
        bounds.add(new Rectangle2D.Double(card.getX() - margin, card.getY() - margin,
                card.getWidth() + 2 * margin, card.getHeight() + 2 * margin));
        imageBounds = bounds;
    }

    public double width() { return imageBounds.getWidth(); }
    public double height() { return imageBounds.getHeight(); }
    public Content content() { return content; }

    public BufferedImage toImage(double scale) {
        int w = (int) Math.ceil(width() * scale), h = (int) Math.ceil(height() * scale);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(scale, scale);
        paint(g);
        g.dispose();
        return img;
    }

    public void paint(Graphics2D g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(-imageBounds.getX(), -imageBounds.getY());
        paintBackdrop(g);
        paintCard(g);
        paintMarks(g);
        paintWatermark(g);
        g.dispose();
    }

    public Point2D toScene(Point2D imagePoint) {
        return new Point2D.Double(imagePoint.getX() + imageBounds.getX(), imagePoint.getY() + imageBounds.getY());
    }

    public Point2D toContent(Point2D imagePoint) {
        Point2D p = toScene(imagePoint);
        return new Point2D.Double(p.getX() - contentArea.getX(), p.getY() - contentArea.getY());
    }

    public Rectangle2D contentRectInImage(Rectangle2D local) {
        return new Rectangle2D.Double(local.getX() + contentArea.getX() - imageBounds.getX(),
                local.getY() + contentArea.getY() - imageBounds.getY(), local.getWidth(), local.getHeight());
    }

    private Rectangle2D pad(Rectangle2D local) {
        return new Rectangle2D.Double(local.getX() + contentArea.getX() - 3, local.getY() + contentArea.getY() - 1,
                local.getWidth() + 6, local.getHeight() + 2);
    }

    private List<Rectangle2D> outline(int index) {
        List<Rectangle2D> segments = markSegments.get(index);
        return segments.size() > 1 ? segments : List.of(markBoxes.get(index));
    }

    public Point2D calloutOffset(int index) {
        Rectangle2D box = markBoxes.get(index), callout = callouts.get(index);
        return new Point2D.Double(callout.getX() - box.getX(), callout.getY() - box.getY());
    }

    private Rectangle2D autoPlace(int index, double w, double h) {
        Rectangle2D box = markBoxes.get(index);
        double[] xs = {box.getMaxX() + 28, box.getX() + 24, box.getX() - w - 28, card.getMaxX() + 24};
        Rectangle2D best = null;
        double bestScore = Double.MAX_VALUE;
        for (int xi = 0; xi < xs.length; xi++) {
            for (int k = -14; k <= 14; k++) {
                Rectangle2D c = new Rectangle2D.Double(xs[xi], box.getCenterY() - h / 2 + k * 12, w, h);
                double score = xi * 40 + Math.abs(k) * 4 + penalty(index, c);
                if (score < bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
        }
        return best;
    }

    private double penalty(int index, Rectangle2D c) {
        double score = 0;
        if (!card.contains(c)) score += card.intersects(c) ? 100_000 : 400;
        if (c.intersects(card.getX(), contentArea.getMaxY(), card.getWidth(), legendH + captionH)) score += 20_000;
        for (int i = 0; i < markBoxes.size(); i++) {
            if (markBoxes.get(i) != null && markBoxes.get(i).intersects(c)) score += 10_000;
        }
        for (Rectangle2D placed : callouts) {
            if (placed != null && placed.intersects(c.getX() - 8, c.getY() - 8, c.getWidth() + 16, c.getHeight() + 16)) score += 50_000;
        }
        Rectangle2D local = new Rectangle2D.Double(c.getX() - contentArea.getX() - 6, c.getY() - contentArea.getY() - 6,
                c.getWidth() + 12, c.getHeight() + 12);
        return score + content.ink(local) * 25;
    }

    public int calloutAt(Point2D imagePoint) {
        Point2D p = toScene(imagePoint);
        for (int i = callouts.size() - 1; i >= 0; i--) {
            if (callouts.get(i) != null && callouts.get(i).contains(p)) return i;
        }
        return -1;
    }

    public int markAt(Point2D imagePoint) {
        Point2D p = toScene(imagePoint);
        int best = -1;
        double bestArea = Double.MAX_VALUE;
        for (int i = 0; i < markBoxes.size(); i++) {
            Rectangle2D b = markBoxes.get(i);
            if (b == null) continue;
            Rectangle2D grown = new Rectangle2D.Double(b.getX() - 4, b.getY() - 4, b.getWidth() + 8, b.getHeight() + 8);
            double area = b.getWidth() * b.getHeight();
            if (grown.contains(p) && area < bestArea) {
                best = i;
                bestArea = area;
            }
        }
        return best;
    }

    private boolean showCallout(Mark m) {
        return settings.notes == Settings.Notes.CALLOUT && m.hasNote();
    }

    private boolean numbered(int index) {
        return marks.get(index).hasNote() && markBoxes.get(index) != null
                && (settings.numberMarks || settings.notes == Settings.Notes.LEGEND);
    }

    private int number(int index) {
        int n = 0;
        for (int i = 0; i <= index; i++) {
            if (marks.get(i).hasNote() && markBoxes.get(i) != null) n++;
        }
        return n;
    }

    private void paintBackdrop(Graphics2D g) {
        if (settings.frame == Settings.Frame.SHOWCASE) {
            g.setPaint(new GradientPaint((float) imageBounds.getX(), (float) imageBounds.getY(), theme.backdropFrom,
                    (float) imageBounds.getMaxX(), (float) imageBounds.getMaxY(), theme.backdropTo));
            g.fill(imageBounds);
            for (int i = 1; i <= 12; i++) {
                g.setColor(new Color(0, 0, 0, 6));
                g.fill(new RoundRectangle2D.Double(card.getX() - i + 2, card.getY() - i + 10, card.getWidth() + 2 * i - 4,
                        card.getHeight() + 2 * i - 4, 12 + 2 * i, 12 + 2 * i));
            }
        } else {
            g.setColor(theme.card);
            g.fill(imageBounds);
        }
    }

    private void paintCard(Graphics2D g) {
        double radius = settings.frame == Settings.Frame.SHOWCASE ? 12 : 6;
        Shape shape = new RoundRectangle2D.Double(card.getX(), card.getY(), card.getWidth(), card.getHeight(), radius * 2, radius * 2);
        g.setColor(theme.card);
        g.fill(shape);

        Graphics2D clip = (Graphics2D) g.create();
        clip.clip(shape);
        double y = card.getY();
        if (titleH > 0) {
            clip.setColor(theme.headerBar);
            clip.fill(new Rectangle2D.Double(card.getX(), y, card.getWidth(), titleH));
            clip.setFont(titleFont);
            clip.setColor(theme.text);
            String title = ellipsize(header.title(), titleFont, card.getWidth() - 2 * CARD_PAD);
            clip.drawString(title, (float) (card.getX() + CARD_PAD), (float) (y + titleH / 2 + Fonts.ascent(titleFont) / 2 - 1));
            y += titleH;
        }
        if (headerH > 0) {
            paintHeaderBar(clip, y);
            y += headerH;
            clip.setColor(theme.border);
            clip.draw(new Line2D.Double(card.getX(), y - 0.5, card.getMaxX(), y - 0.5));
        }
        Graphics2D c = (Graphics2D) clip.create();
        c.translate(contentArea.getX(), contentArea.getY());
        content.paint(c);
        c.dispose();
        if (legendH > 0) paintLegend(clip, contentArea.getMaxY());
        if (captionH > 0) paintCaption(clip, contentArea.getMaxY() + legendH);
        clip.dispose();

        g.setColor(theme.border);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(card.getX() + 0.5, card.getY() + 0.5, card.getWidth() - 1, card.getHeight() - 1,
                radius * 2, radius * 2));
    }

    private void paintHeaderBar(Graphics2D g, double top) {
        g.setColor(theme.headerBar);
        g.fill(new Rectangle2D.Double(card.getX(), top, card.getWidth(), headerH));
        double mid = top + headerH / 2;
        double x = card.getX() + CARD_PAD;
        if (settings.frame == Settings.Frame.SHOWCASE) {
            Color[] dots = {new Color(0xFF5F57), new Color(0xFEBC2E), new Color(0x28C840)};
            for (Color dot : dots) {
                g.setColor(dot);
                g.fill(new Ellipse2D.Double(x, mid - 6, 12, 12));
                x += 20;
            }
            x += 8;
        }
        double right = card.getMaxX() - CARD_PAD;
        if (settings.showTime && header.timeMs() >= 0) {
            String time = header.timeMs() + " ms";
            right -= Fonts.width(uiFont, time);
            g.setFont(uiFont);
            g.setColor(theme.muted);
            g.drawString(time, (float) right, (float) (mid + Fonts.ascent(uiFont) / 2 - 1));
            right -= 12;
        }
        if (header.status() > 0) {
            String status = (header.status() + " " + header.reason()).trim();
            right -= pillWidth(status);
            pill(g, status, right, mid, Theme.statusColor(header.status()));
            right -= 12;
        }
        if (!header.method().isEmpty()) {
            x += pill(g, header.method(), x, mid, theme.accent) + 10;
        }
        g.setFont(mono);
        g.setColor(theme.text);
        String url = ellipsize(header.url(), mono, right - x);
        g.drawString(url, (float) x, (float) (mid + Fonts.ascent(mono) / 2 - 1));
    }

    private double pillWidth(String text) {
        return Fonts.width(uiBold, text) + 16;
    }

    private double pill(Graphics2D g, String text, double x, double mid, Color color) {
        double w = pillWidth(text);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 36));
        g.fill(new RoundRectangle2D.Double(x, mid - 11, w, 22, 22, 22));
        g.setColor(color);
        g.setFont(uiBold);
        g.drawString(text, (float) (x + 8), (float) (mid + Fonts.ascent(uiBold) / 2 - 1));
        return w;
    }

    private List<Integer> legendEntries() {
        List<Integer> out = new ArrayList<>();
        if (settings.notes != Settings.Notes.LEGEND) return out;
        for (int i = 0; i < marks.size(); i++) {
            if (marks.get(i).hasNote() && content.bounds(marks.get(i).anchor) != null) out.add(i);
        }
        return out;
    }

    private double legendHeight(double cardW) {
        List<Integer> entries = legendEntries();
        if (entries.isEmpty()) return 0;
        double h = 2 * CARD_PAD - 6;
        for (int i : entries) {
            h += Math.max(22, wrap(marks.get(i).note, uiFont, cardW - 2 * CARD_PAD - 32).size() * lineHeight(uiFont)) + 6;
        }
        return h;
    }

    private void paintCaption(Graphics2D g, double top) {
        g.setColor(theme.headerBar);
        g.fill(new Rectangle2D.Double(card.getX(), top, card.getWidth(), captionH));
        g.setColor(theme.border);
        g.draw(new Line2D.Double(card.getX(), top + 0.5, card.getMaxX(), top + 0.5));
        g.setFont(uiFont);
        g.setColor(theme.text);
        double y = top + CARD_PAD - 2 + Fonts.ascent(uiFont);
        for (String line : captionLines) {
            g.drawString(line, (float) (card.getX() + CARD_PAD), (float) y);
            y += lineHeight(uiFont);
        }
    }

    private void paintLegend(Graphics2D g, double top) {
        g.setColor(theme.headerBar);
        g.fill(new Rectangle2D.Double(card.getX(), top, card.getWidth(), legendH));
        g.setColor(theme.border);
        g.draw(new Line2D.Double(card.getX(), top + 0.5, card.getMaxX(), top + 0.5));
        double y = top + CARD_PAD - 3;
        for (int i : legendEntries()) {
            Mark m = marks.get(i);
            badge(g, card.getX() + CARD_PAD + 10, y + 11, number(i), m.awtColor());
            List<String> lines = wrap(m.note, uiFont, card.getWidth() - 2 * CARD_PAD - 32);
            g.setFont(uiFont);
            g.setColor(theme.text);
            double ty = y + 11 + Fonts.ascent(uiFont) / 2 - 1;
            for (String line : lines) {
                g.drawString(line, (float) (card.getX() + CARD_PAD + 30), (float) ty);
                ty += lineHeight(uiFont);
            }
            y += Math.max(22, lines.size() * lineHeight(uiFont)) + 6;
        }
    }

    private void paintMarks(Graphics2D g) {
        for (int i = 0; i < marks.size(); i++) {
            Rectangle2D box = markBoxes.get(i);
            if (box == null) continue;
            Color color = marks.get(i).awtColor();
            g.setColor(color);
            g.setStroke(new BasicStroke(2f));
            for (Rectangle2D part : outline(i)) {
                g.draw(new RoundRectangle2D.Double(part.getX(), part.getY(), part.getWidth(), part.getHeight(), 6, 6));
            }
            Rectangle2D callout = callouts.get(i);
            if (callout != null) paintCallout(g, i, box, callout, color);
        }
        for (int i = 0; i < marks.size(); i++) {
            if (!numbered(i)) continue;
            Rectangle2D first = outline(i).get(0);
            badge(g, first.getX() - 3, first.getY() - 3, number(i), marks.get(i).awtColor());
        }
    }

    private void paintCallout(Graphics2D g, int index, Rectangle2D box, Rectangle2D callout, Color color) {
        Point2D target = closest(box, center(callout));
        double nearest = Double.MAX_VALUE;
        for (Rectangle2D part : outline(index)) {
            Point2D p = closest(part, center(callout));
            if (p.distance(center(callout)) < nearest) {
                nearest = p.distance(center(callout));
                target = p;
            }
        }
        Point2D source = closest(callout, target);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(color);
        if (!box.intersects(callout)) {
            g.draw(new Line2D.Double(source, target));
            g.fill(new Ellipse2D.Double(target.getX() - 3, target.getY() - 3, 6, 6));
        }
        RoundRectangle2D shape = new RoundRectangle2D.Double(callout.getX(), callout.getY(), callout.getWidth(), callout.getHeight(), 12, 12);
        g.setColor(theme.shadow);
        g.fill(new RoundRectangle2D.Double(callout.getX() + 1, callout.getY() + 3, callout.getWidth(), callout.getHeight(), 12, 12));
        g.setColor(theme.calloutBg);
        g.fill(shape);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(shape);
        g.fill(new RoundRectangle2D.Double(callout.getX(), callout.getY(), 6, callout.getHeight(), 6, 6));
        double textX = callout.getX() + 16;
        if (numbered(index)) {
            badge(g, callout.getX() + 22, callout.getY() + 8 + lineHeight(uiFont) / 2, number(index), color);
            textX = callout.getX() + 38;
        }
        g.setFont(uiFont);
        g.setColor(theme.text);
        double y = callout.getY() + 8 + Fonts.ascent(uiFont) + (lineHeight(uiFont) - Fonts.height(uiFont)) / 2;
        for (String line : calloutLines.get(index)) {
            g.drawString(line, (float) textX, (float) y);
            y += lineHeight(uiFont);
        }
    }

    private void badge(Graphics2D g, double cx, double cy, int number, Color color) {
        Font f = Fonts.sans(10, true);
        String s = String.valueOf(number);
        double d = Math.max(16, Fonts.width(f, s) + 8);
        g.setColor(theme.card);
        g.fill(new RoundRectangle2D.Double(cx - d / 2 - 1.5, cy - 9.5, d + 3, 19, 19, 19));
        g.setColor(color);
        g.fill(new RoundRectangle2D.Double(cx - d / 2, cy - 8, d, 16, 16, 16));
        g.setColor(Color.WHITE);
        g.setFont(f);
        g.drawString(s, (float) (cx - Fonts.width(f, s) / 2), (float) (cy + Fonts.ascent(f) / 2 - 1));
    }

    private void paintWatermark(Graphics2D g0) {
        if (!settings.watermark || (settings.watermarkText.isBlank() && logo == null)) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.clip(imageBounds);
        Composite alpha = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, settings.watermarkOpacity / 100f);
        g.setComposite(alpha);
        Font font = Fonts.sans(settings.watermarkSize, true);
        String text = settings.watermarkText
                .replace("{date}", LocalDate.now().toString())
                .replace("{host}", header.host());
        double logoH = logo == null ? 0 : settings.watermarkSize * 1.4;
        double logoW = logo == null ? 0 : logoH * logo.getWidth(null) / Math.max(1, logo.getHeight(null));
        double gap = logo != null && !text.isBlank() ? 8 : 0;
        double tileW = logoW + gap + Fonts.width(font, text);
        double tileH = Math.max(logoH, Fonts.height(font));

        if (settings.watermarkPlacement == Settings.WatermarkPlacement.TILED) {
            Rectangle2D area = imageBounds;
            g.rotate(Math.toRadians(settings.watermarkAngle), area.getCenterX(), area.getCenterY());
            double spacing = settings.watermarkSpacing / 100.0;
            double stepX = tileW + settings.watermarkSize * 5 * spacing;
            double stepY = tileH * 5 * spacing;
            double reach = Math.hypot(area.getWidth(), area.getHeight());
            int row = 0;
            for (double y = area.getCenterY() - reach; y < area.getCenterY() + reach; y += stepY, row++) {
                double offset = (row % 2) * stepX / 2;
                for (double x = area.getCenterX() - reach - offset; x < area.getCenterX() + reach; x += stepX) {
                    watermarkTile(g, font, text, x, y, logoW, logoH, gap, tileH);
                }
            }
        } else {
            double pad = 14;
            double x = switch (settings.watermarkPlacement) {
                case BOTTOM_LEFT -> card.getX() + pad;
                case CENTER -> card.getCenterX() - tileW / 2;
                default -> card.getMaxX() - pad - tileW;
            };
            double y = settings.watermarkPlacement == Settings.WatermarkPlacement.CENTER
                    ? card.getCenterY() - tileH / 2
                    : card.getMaxY() - pad - tileH;
            watermarkTile(g, font, text, x, y, logoW, logoH, gap, tileH);
        }
        g.dispose();
    }

    private void watermarkTile(Graphics2D g, Font font, String text, double x, double y,
                               double logoW, double logoH, double gap, double tileH) {
        if (logo != null) {
            g.drawImage(logo, (int) x, (int) (y + (tileH - logoH) / 2), (int) logoW, (int) logoH, null);
        }
        if (!text.isBlank()) {
            g.setFont(font);
            g.setColor(theme.watermark);
            g.drawString(text, (float) (x + logoW + gap), (float) (y + (tileH - Fonts.height(font)) / 2 + Fonts.ascent(font)));
        }
    }

    private static Point2D center(Rectangle2D r) {
        return new Point2D.Double(r.getCenterX(), r.getCenterY());
    }

    private static Point2D closest(Rectangle2D r, Point2D p) {
        return new Point2D.Double(Math.max(r.getMinX(), Math.min(p.getX(), r.getMaxX())),
                Math.max(r.getMinY(), Math.min(p.getY(), r.getMaxY())));
    }

    private static double lineHeight(Font f) {
        return Math.round(Fonts.height(f) * 1.35);
    }

    static String ellipsize(String s, Font f, double max) {
        if (Fonts.width(f, s) <= max) return s;
        String out = s;
        while (!out.isEmpty() && Fonts.width(f, out + "…") > max) out = out.substring(0, out.length() - 1);
        return out + "…";
    }

    static List<String> wrap(String text, Font f, double max) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.strip().split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (Fonts.width(f, candidate) <= max || line.isEmpty()) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
