package burpss.render;

import burpss.core.HttpText;
import burpss.core.Names;
import burpss.core.Settings;
import burpss.core.Style;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class DisplayText {

    static final int INSERTED = -1;
    static final int SYNTHETIC = -2;

    private final HttpText source;
    private final StringBuilder text = new StringBuilder();
    private int[] origin = new int[256];
    private int bodyStart = -1;

    private DisplayText(HttpText source) {
        this.source = source;
    }

    public static DisplayText build(HttpText source, Settings settings, Set<Integer> toggledHeaders) {
        DisplayText d = new DisplayText(source);
        d.appendHead(Names.split(settings.hideHeaders), toggledHeaders);
        d.appendBody(settings.prettyJson);
        return d;
    }

    public static boolean isHidden(HttpText.HeaderLine header, List<String> hidePatterns, Set<Integer> toggled) {
        return Names.globMatches(hidePatterns, header.name()) ^ toggled.contains(header.lineStart());
    }

    public HttpText source() { return source; }
    public int length() { return text.length(); }
    public char charAt(int i) { return text.charAt(i); }
    public int origin(int i) { return origin[i]; }
    public int bodyStart() { return bodyStart < 0 ? text.length() : bodyStart; }

    public Style style(int i) {
        int o = origin[i];
        if (o >= 0) return source.styleAt(o);
        return o == SYNTHETIC ? Style.MUTED : Style.PLAIN;
    }

    public int nearestOrigin(int displayIndex) {
        for (int i = Math.min(displayIndex, text.length() - 1); i >= 0; i--) {
            if (origin[i] >= 0) return origin[i];
        }
        return -1;
    }

    private void appendHead(List<String> hidePatterns, Set<Integer> toggled) {
        String s = source.text();
        int end = Math.min(source.bodyStart(), s.length());
        int lineStart = 0;
        boolean first = true;
        while (lineStart < end) {
            int nl = s.indexOf('\n', lineStart);
            int next = nl < 0 || nl >= end ? end : nl + 1;
            int lineEnd = nl < 0 || nl >= end ? end : nl;
            if (lineEnd > lineStart && s.charAt(lineEnd - 1) == '\r') lineEnd--;
            if (lineEnd == lineStart && !first) break;
            HttpText.HeaderLine header = first ? null : source.headerAt(lineStart);
            if (header == null || !isHidden(header, hidePatterns, toggled)) {
                copy(lineStart, lineEnd);
                add('\n', INSERTED);
            }
            first = false;
            lineStart = next;
        }
    }

    private void appendBody(boolean pretty) {
        if (source.bodyLength() <= 0) return;
        add('\n', INSERTED);
        bodyStart = text.length();
        int start = source.bodyStart();
        int end = source.text().length();
        switch (source.bodyKind()) {
            case BINARY -> synthetic("[binary body · " + source.bodyLength() + " chars]");
            case JSON -> {
                if (pretty) prettyJson(start, end);
                else copy(start, end);
            }
            default -> copy(start, end);
        }
    }

    private void copy(int from, int to) {
        String s = source.text();
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c == '\r') continue;
            if (c == '\t') {
                for (int k = 0; k < 4; k++) add(' ', i);
            } else if (c < 0x20 && c != '\n') {
                add('·', i);
            } else {
                add(c, i);
            }
        }
    }

    private void prettyJson(int from, int to) {
        String s = source.text();
        int depth = 0;
        boolean inString = false;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (inString) {
                add(c, i);
                if (c == '\\' && i + 1 < to) {
                    add(s.charAt(++i), i);
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    add(c, i);
                }
                case '{', '[' -> {
                    add(c, i);
                    int next = skipWhitespace(s, i + 1, to);
                    if (next < to && s.charAt(next) == (c == '{' ? '}' : ']')) {
                        add(s.charAt(next), next);
                        i = next;
                    } else {
                        newline(++depth);
                    }
                }
                case '}', ']' -> {
                    newline(depth = Math.max(0, depth - 1));
                    add(c, i);
                }
                case ',' -> {
                    add(c, i);
                    newline(depth);
                }
                case ':' -> {
                    add(c, i);
                    add(' ', INSERTED);
                }
                case ' ', '\t', '\r', '\n' -> { }
                default -> add(c, i);
            }
        }
    }

    private static int skipWhitespace(String s, int i, int to) {
        while (i < to && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private void newline(int depth) {
        add('\n', INSERTED);
        for (int k = 0; k < depth * 2; k++) add(' ', INSERTED);
    }

    private void synthetic(String s) {
        for (char c : s.toCharArray()) add(c, SYNTHETIC);
    }

    private void add(char c, int from) {
        if (text.length() == origin.length) origin = Arrays.copyOf(origin, origin.length * 2);
        origin[text.length()] = from;
        text.append(c);
    }
}
