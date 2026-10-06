package burpss.render;

import burpss.core.HttpText;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Set;

final class MarkupFormat {

    private static final Set<String> VOID = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
            "param", "source", "track", "wbr");
    private static final Set<String> VERBATIM = Set.of("pre", "textarea");
    private static final Set<String> SELF_CLOSING = Set.of("p", "li", "dt", "dd", "tr", "td", "th", "option");
    private static final int INLINE_TEXT = 80;
    static final int MAX_INDENT = 24;

    private final DisplayText out;
    private final String s;
    private final Deque<String> open = new ArrayDeque<>();

    private MarkupFormat(DisplayText out, String s) {
        this.out = out;
        this.s = s;
    }

    static void format(DisplayText out, String s, int from, int to) {
        new MarkupFormat(out, s).run(from, to);
    }

    private void run(int from, int to) {
        int i = from;
        while (i < to) {
            if (s.startsWith("<!--", i)) {
                i = line(i, closeAfter("-->", i + 4, to), to);
            } else if (s.startsWith("<![CDATA[", i)) {
                i = line(i, closeAfter("]]>", i + 9, to), to);
            } else if (tagStart(i, to)) {
                i = tag(i, to);
            } else {
                int end = i + 1;
                while (end < to && !tagStart(end, to)) end++;
                text(i, end);
                i = end;
            }
        }
    }

    private int tag(int i, int to) {
        int end = tagEnd(i, to);
        char kind = s.charAt(i + 1);
        String name = name(kind == '/' ? i + 2 : i + 1, end);
        if (kind == '/') {
            if (open.contains(name)) {
                while (!open.pop().equals(name)) {
                }
            }
            return line(i, end, to);
        }
        if (SELF_CLOSING.contains(name) && name.equals(open.peek())) open.pop();
        if (kind == '!' || kind == '?' || s.charAt(end - 2) == '/' || VOID.contains(name)) return line(i, end, to);
        boolean raw = VERBATIM.contains(name) || name.equals("script") || name.equals("style");
        int close = raw ? HttpText.indexOfIgnoreCase(s, "</" + name, end) : -1;
        if (close >= to) close = -1;
        if (VERBATIM.contains(name)) {
            int closeEnd = close < 0 ? to : tagEnd(close, to);
            line(i, end, to);
            out.copy(end, closeEnd);
            return closeEnd;
        }
        if (name.equals("script") || name.equals("style")) {
            int contentEnd = close < 0 ? to : close;
            line(i, end, to);
            open.push(name);
            if (!s.substring(end, contentEnd).isBlank()) {
                newline();
                if (name.equals("style") || scriptCode(i, end)) CodeFormat.format(out, s, end, contentEnd, open.size(), name.equals("script"));
                else text(end, contentEnd);
            }
            return contentEnd;
        }
        int next = s.indexOf('<', end);
        if (next >= 0 && next < to && next - end <= INLINE_TEXT && s.regionMatches(true, next, "</" + name, 0, name.length() + 2)
                && s.substring(end, next).indexOf('\n') < 0) {
            int closeEnd = tagEnd(next, to);
            close = next;
            startLine();
            collapse(i, end);
            collapse(trimStart(end, close), trimEnd(end, close));
            collapse(close, closeEnd);
            return closeEnd;
        }
        line(i, end, to);
        open.push(name);
        return end;
    }

    private boolean scriptCode(int start, int end) {
        String open = s.substring(start, end).toLowerCase(Locale.ROOT);
        int type = open.indexOf("type=");
        if (type < 0) return true;
        String value = open.substring(type);
        return value.contains("javascript") || value.contains("module") || value.contains("ecmascript");
    }

    private boolean tagStart(int i, int to) {
        if (s.charAt(i) != '<' || i + 1 >= to) return false;
        char next = s.charAt(i + 1);
        return Character.isLetter(next) || next == '/' || next == '!' || next == '?';
    }

    private int tagEnd(int i, int to) {
        int j = i + 1;
        char quote = 0;
        while (j < to) {
            char c = s.charAt(j++);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return j;
            }
        }
        return to;
    }

    private String name(int i, int to) {
        int j = i;
        while (j < to && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '-' || s.charAt(j) == ':' || s.charAt(j) == '_')) j++;
        return s.substring(i, j).toLowerCase(Locale.ROOT);
    }

    private int closeAfter(String marker, int from, int to) {
        int at = s.indexOf(marker, from);
        return at < 0 || at + marker.length() > to ? to : at + marker.length();
    }

    private int line(int from, int to, int limit) {
        startLine();
        collapse(from, Math.min(to, limit));
        return Math.min(to, limit);
    }

    private void text(int from, int to) {
        int a = trimStart(from, to), b = trimEnd(from, to);
        if (a >= b) return;
        startLine();
        collapse(a, b);
    }

    private void collapse(int from, int to) {
        for (int k = from; k < to; k++) {
            char c = s.charAt(k);
            if (!Character.isWhitespace(c)) {
                out.copy(k, k + 1);
            } else if (out.length() > 0 && out.charAt(out.length() - 1) != ' ' && out.charAt(out.length() - 1) != '\n') {
                out.add(' ', c == ' ' ? k : DisplayText.INSERTED);
            }
        }
    }

    private int trimStart(int from, int to) {
        while (from < to && Character.isWhitespace(s.charAt(from))) from++;
        return from;
    }

    private int trimEnd(int from, int to) {
        while (to > from && Character.isWhitespace(s.charAt(to - 1))) to--;
        return to;
    }

    private void startLine() {
        newline();
        for (int k = 0; k < Math.min(open.size(), MAX_INDENT) * 2; k++) out.add(' ', DisplayText.INSERTED);
    }

    private void newline() {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.add('\n', DisplayText.INSERTED);
    }
}
