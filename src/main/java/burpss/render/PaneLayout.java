package burpss.render;

import java.util.ArrayList;
import java.util.List;

public final class PaneLayout {

    public record Line(int start, int end, String synthetic, int number) {
        public int length() {
            return synthetic != null ? synthetic.length() : end - start;
        }
    }

    private final List<Line> lines = new ArrayList<>();
    private int columns;

    public static final int MAX_BODY_LINES = 100;

    public static PaneLayout build(DisplayText d, int wrapColumns, int maxBodyLines, int bodyOffset) {
        PaneLayout layout = new PaneLayout();
        int limit = limit(maxBodyLines);
        int wrap = Math.max(20, wrapColumns);
        int total = bodyRow(d, wrap, d.length());
        int skip = Math.max(0, Math.min(bodyOffset, total - limit));
        int bodyRows = 0;
        int bodyLines = 0;
        int overflow = 0;
        int n = d.length();
        int lineStart = 0;
        int number = 0;
        while (lineStart < n) {
            int nl = lineStart;
            while (nl < n && d.charAt(nl) != '\n') nl++;
            boolean trailingEmpty = nl == lineStart && nl == n - 1 && lineStart >= d.bodyStart();
            if (!trailingEmpty) {
                number++;
                int chunk = lineStart;
                do {
                    int chunkEnd = Math.min(nl, chunk + wrap);
                    boolean inBody = chunk >= d.bodyStart() && lineStart >= d.bodyStart();
                    if (inBody && bodyRows++ < skip) {
                        if (bodyRows == skip) layout.add(new Line(chunkEnd, chunkEnd, "… " + skip + " line" + (skip == 1 ? "" : "s") + " above", 0));
                    } else if (inBody && bodyLines >= limit) {
                        overflow++;
                    } else {
                        layout.add(new Line(chunk, chunkEnd, null, chunk == lineStart ? number : 0));
                        if (inBody) bodyLines++;
                    }
                    chunk = chunkEnd;
                } while (chunk < nl);
            }
            lineStart = nl + 1;
        }
        if (overflow > 0) {
            layout.add(new Line(n, n, "… " + overflow + " more line" + (overflow == 1 ? "" : "s"), 0));
        }
        return layout;
    }

    static int limit(int maxBodyLines) {
        return maxBodyLines > 0 ? Math.min(maxBodyLines, MAX_BODY_LINES) : MAX_BODY_LINES;
    }

    static int bodyRow(DisplayText d, int wrapColumns, int displayIndex) {
        int wrap = Math.max(20, wrapColumns);
        int n = d.length();
        int rows = 0;
        int lineStart = d.bodyStart();
        while (lineStart < n) {
            int nl = lineStart;
            while (nl < n && d.charAt(nl) != '\n') nl++;
            if (nl == lineStart && nl == n - 1) break;
            int chunk = lineStart;
            do {
                int chunkEnd = Math.min(nl, chunk + wrap);
                if (displayIndex < chunkEnd || (displayIndex == chunkEnd && chunkEnd == nl)) return rows;
                rows++;
                chunk = chunkEnd;
            } while (chunk < nl);
            lineStart = nl + 1;
        }
        return rows;
    }

    private void add(Line line) {
        lines.add(line);
        columns = Math.max(columns, line.length());
    }

    public List<Line> lines() { return lines; }
    public int columns() { return columns; }
    public int lastNumber() { return lines.stream().mapToInt(Line::number).max().orElse(1); }

    public int lineOf(int displayIndex) {
        int lo = 0, hi = lines.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            Line l = lines.get(mid);
            boolean nextStartsHere = mid + 1 < lines.size() && lines.get(mid + 1).start() == displayIndex;
            if (l.synthetic() != null) {
                if (displayIndex > l.start() || nextStartsHere) lo = mid + 1;
                else hi = mid - 1;
            } else if (displayIndex < l.start()) hi = mid - 1;
            else if (displayIndex > l.end() || (displayIndex == l.end() && nextStartsHere)) lo = mid + 1;
            else return mid;
        }
        return -1;
    }

    public int indexAt(int row, int col) {
        if (row < 0 || row >= lines.size()) return -1;
        Line l = lines.get(row);
        if (l.synthetic() != null) return -1;
        return l.start() + Math.max(0, Math.min(col, l.end() - l.start()));
    }
}
