package burpss.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class HttpText {

    public enum BodyKind { NONE, JSON, SSE, FORM, MARKUP, TEXT, BINARY }

    public record HeaderLine(String name, int lineStart, int lineEnd, int valueStart, int valueEnd) {
        public boolean contains(int offset) {
            return offset >= lineStart && offset < lineEnd;
        }
    }

    private final String text;
    private final boolean request;
    private final Style[] styles;
    private final List<Token> tokens = new ArrayList<>();
    private final List<TextRange> jsonScalars = new ArrayList<>();
    private final List<TextRange> sseJson = new ArrayList<>();
    private final List<HeaderLine> headers = new ArrayList<>();
    private int bodyStart;
    private BodyKind bodyKind = BodyKind.NONE;
    private String contentType = "";

    private HttpText(String text, boolean request) {
        this.text = text;
        this.request = request;
        this.styles = new Style[text.length()];
        Arrays.fill(styles, Style.PLAIN);
    }

    public static HttpText parse(String text, boolean request) {
        HttpText t = new HttpText(text, request);
        t.analyze();
        return t;
    }

    public String text() { return text; }
    public Style styleAt(int offset) { return styles[offset]; }
    public List<Token> tokens() { return Collections.unmodifiableList(tokens); }
    public List<TextRange> jsonScalars() { return Collections.unmodifiableList(jsonScalars); }
    public List<TextRange> sseJson() { return Collections.unmodifiableList(sseJson); }
    public List<HeaderLine> headers() { return Collections.unmodifiableList(headers); }
    public int bodyStart() { return bodyStart; }
    public BodyKind bodyKind() { return bodyKind; }
    public int bodyLength() { return text.length() - bodyStart; }

    public Token tokenAt(int offset) {
        Token best = null;
        for (Token t : tokens) {
            if (t.contains(offset) && (best == null || t.length() < best.length())) {
                best = t;
            }
        }
        return best;
    }

    public HeaderLine headerAt(int offset) {
        for (HeaderLine h : headers) {
            if (h.contains(offset)) {
                return h;
            }
        }
        return null;
    }

    private void analyze() {
        int n = text.length();
        int lineStart = 0;
        int lineIndex = 0;
        bodyStart = n;
        while (lineStart < n) {
            int nl = text.indexOf('\n', lineStart);
            int next = nl < 0 ? n : nl + 1;
            int lineEnd = nl < 0 ? n : nl;
            if (lineEnd > lineStart && text.charAt(lineEnd - 1) == '\r') {
                lineEnd--;
            }
            if (lineIndex == 0) {
                if (request) {
                    parseRequestLine(lineStart, lineEnd);
                } else {
                    parseStatusLine(lineStart, lineEnd);
                }
            } else if (lineEnd == lineStart) {
                bodyStart = next;
                break;
            } else {
                parseHeader(lineStart, lineEnd);
            }
            lineIndex++;
            lineStart = next;
        }
        classifyBody();
        switch (bodyKind) {
            case JSON -> parseJson(bodyStart, n);
            case SSE -> parseSse(bodyStart, n);
            case FORM -> parseParams(bodyStart, trimEnd(bodyStart, n), Token.Kind.BODY_PARAM);
            case MARKUP -> parseMarkup(bodyStart, n);
            default -> { }
        }
    }

    private void parseRequestLine(int start, int end) {
        int sp1 = text.indexOf(' ', start);
        if (sp1 < 0 || sp1 >= end) {
            return;
        }
        style(start, sp1, Style.METHOD);
        tokens.add(new Token(Token.Kind.METHOD, text.substring(start, sp1), start, sp1, start, sp1));
        int sp2 = text.lastIndexOf(' ', end - 1);
        int targetEnd = sp2 > sp1 ? sp2 : end;
        if (sp2 > sp1) {
            tokens.add(new Token(Token.Kind.VERSION, "version", sp2 + 1, end, sp2 + 1, end));
        }
        int targetStart = sp1 + 1;
        int q = text.indexOf('?', targetStart);
        int pathEnd = q >= 0 && q < targetEnd ? q : targetEnd;
        style(targetStart, pathEnd, Style.PATH);
        tokens.add(new Token(Token.Kind.PATH, "path", targetStart, pathEnd, targetStart, pathEnd));
        if (pathEnd < targetEnd) {
            style(pathEnd, pathEnd + 1, Style.PUNCT);
            parseParams(pathEnd + 1, targetEnd, Token.Kind.QUERY_PARAM);
        }
    }

    private void parseStatusLine(int start, int end) {
        int sp1 = text.indexOf(' ', start);
        if (sp1 < 0 || sp1 >= end) {
            style(start, end, Style.STATUS);
            return;
        }
        style(sp1 + 1, end, Style.STATUS);
        tokens.add(new Token(Token.Kind.STATUS_LINE, "status", start, end, sp1 + 1, end));
    }

    private void parseHeader(int start, int end) {
        int colon = text.indexOf(':', start);
        if (colon < 0 || colon >= end) {
            style(start, end, Style.HEADER_VALUE);
            return;
        }
        String name = text.substring(start, colon).trim();
        int valueStart = colon + 1;
        while (valueStart < end && text.charAt(valueStart) == ' ') {
            valueStart++;
        }
        style(start, colon, Style.HEADER_NAME);
        style(colon, colon + 1, Style.PUNCT);
        style(valueStart, end, Style.HEADER_VALUE);
        headers.add(new HeaderLine(name, start, end, valueStart, end));

        String lower = name.toLowerCase(Locale.ROOT);
        int secretStart = valueStart;
        if (lower.equals("authorization") || lower.equals("proxy-authorization")) {
            int sp = text.indexOf(' ', valueStart);
            if (sp > valueStart && sp < end && isWord(valueStart, sp)) {
                secretStart = sp + 1;
            }
        }
        tokens.add(new Token(Token.Kind.HEADER, name, start, end, secretStart, end));

        if (lower.equals("content-type")) {
            contentType = text.substring(valueStart, end).toLowerCase(Locale.ROOT);
        } else if (lower.equals("cookie")) {
            parseCookies(valueStart, end, false);
        } else if (lower.equals("set-cookie")) {
            parseCookies(valueStart, end, true);
        }
    }

    private void parseCookies(int start, int end, boolean firstOnly) {
        int pos = start;
        while (pos < end) {
            int semi = text.indexOf(';', pos);
            int partEnd = semi < 0 || semi > end ? end : semi;
            int s = pos;
            while (s < partEnd && text.charAt(s) == ' ') {
                s++;
            }
            int eq = text.indexOf('=', s);
            if (eq > s && eq < partEnd) {
                style(s, eq, Style.PARAM_NAME);
                style(eq + 1, partEnd, Style.PARAM_VALUE);
                tokens.add(new Token(Token.Kind.COOKIE, text.substring(s, eq), s, partEnd, eq + 1, partEnd));
            }
            if (firstOnly) {
                return;
            }
            pos = partEnd + 1;
        }
    }

    private void parseParams(int start, int end, Token.Kind kind) {
        int pos = start;
        while (pos < end) {
            int amp = text.indexOf('&', pos);
            int partEnd = amp < 0 || amp > end ? end : amp;
            if (partEnd > pos) {
                int eq = text.indexOf('=', pos);
                if (eq >= pos && eq < partEnd) {
                    style(pos, eq, Style.PARAM_NAME);
                    style(eq, eq + 1, Style.PUNCT);
                    style(eq + 1, partEnd, Style.PARAM_VALUE);
                    tokens.add(new Token(kind, text.substring(pos, eq), pos, partEnd, eq + 1, partEnd));
                } else {
                    style(pos, partEnd, Style.PARAM_NAME);
                    tokens.add(new Token(kind, text.substring(pos, partEnd), pos, partEnd, partEnd, partEnd));
                }
            }
            if (partEnd < end) {
                style(partEnd, partEnd + 1, Style.PUNCT);
            }
            pos = partEnd + 1;
        }
    }

    private void classifyBody() {
        int n = text.length();
        if (bodyStart >= n) {
            bodyKind = BodyKind.NONE;
            return;
        }
        int sampleEnd = Math.min(n, bodyStart + 4000);
        int bad = 0;
        for (int i = bodyStart; i < sampleEnd; i++) {
            char c = text.charAt(i);
            if ((c < 0x20 && c != '\n' && c != '\r' && c != '\t') || c == '�' || (c >= 0x7F && c < 0xA0)) {
                bad++;
            }
        }
        if (bad * 20 > (sampleEnd - bodyStart)) {
            bodyKind = BodyKind.BINARY;
            return;
        }
        String head = text.substring(bodyStart, sampleEnd).strip();
        if (contentType.contains("event-stream") || head.matches("(?s)(data|event|id|retry):.*")) {
            bodyKind = BodyKind.SSE;
        } else if (contentType.contains("json") || head.startsWith("{") || head.startsWith("[")) {
            bodyKind = BodyKind.JSON;
        } else if (contentType.contains("x-www-form-urlencoded")
                || (contentType.isEmpty() && head.matches("[^=&\\s]+=[^&\\s]*(&[^=&\\s]+(=[^&\\s]*)?)*"))) {
            bodyKind = BodyKind.FORM;
        } else if (contentType.contains("html") || contentType.contains("xml") || head.startsWith("<")) {
            bodyKind = BodyKind.MARKUP;
        } else {
            bodyKind = BodyKind.TEXT;
        }
    }

    private static final int STR = 0, NUM = 1, LIT = 2, OPEN = 3, CLOSE = 4, COLON = 5, COMMA = 6, OTHER = 7;

    private void parseJson(int start, int end) {
        List<int[]> lex = new ArrayList<>();
        int i = start;
        while (i < end) {
            char c = text.charAt(i);
            if (c == '"') {
                int j = i + 1;
                while (j < end && text.charAt(j) != '"') {
                    j += text.charAt(j) == '\\' ? 2 : 1;
                }
                j = Math.min(j + 1, end);
                lex.add(new int[]{STR, i, j});
                i = j;
            } else if (c == '-' || Character.isDigit(c)) {
                int j = i + 1;
                while (j < end && "0123456789eE+-.".indexOf(text.charAt(j)) >= 0) {
                    j++;
                }
                lex.add(new int[]{NUM, i, j});
                i = j;
            } else if (Character.isLetter(c)) {
                int j = i + 1;
                while (j < end && Character.isLetter(text.charAt(j))) {
                    j++;
                }
                lex.add(new int[]{LIT, i, j});
                i = j;
            } else if (c == '{' || c == '[') {
                lex.add(new int[]{OPEN, i, i + 1});
                i++;
            } else if (c == '}' || c == ']') {
                lex.add(new int[]{CLOSE, i, i + 1});
                i++;
            } else if (c == ':') {
                lex.add(new int[]{COLON, i, i + 1});
                i++;
            } else if (c == ',') {
                lex.add(new int[]{COMMA, i, i + 1});
                i++;
            } else if (Character.isWhitespace(c)) {
                i++;
            } else {
                lex.add(new int[]{OTHER, i, i + 1});
                i++;
            }
        }

        int[] match = new int[lex.size()];
        Arrays.fill(match, -1);
        List<Integer> stack = new ArrayList<>();
        for (int k = 0; k < lex.size(); k++) {
            int type = lex.get(k)[0];
            if (type == OPEN) {
                stack.add(k);
            } else if (type == CLOSE && !stack.isEmpty()) {
                match[stack.remove(stack.size() - 1)] = k;
            }
        }

        for (int k = 0; k < lex.size(); k++) {
            int[] l = lex.get(k);
            boolean isKey = l[0] == STR && k + 1 < lex.size() && lex.get(k + 1)[0] == COLON;
            if (l[0] == STR && !isKey && l[2] - l[1] > 2) jsonScalars.add(new TextRange(l[1] + 1, l[2] - 1));
            if (l[0] == NUM) jsonScalars.add(new TextRange(l[1], l[2]));
            switch (l[0]) {
                case STR -> style(l[1], l[2], isKey ? Style.JSON_KEY : Style.STRING);
                case NUM -> style(l[1], l[2], Style.NUMBER);
                case LIT -> style(l[1], l[2], isLiteral(l[1], l[2]) ? Style.KEYWORD : Style.PLAIN);
                case OPEN, CLOSE, COLON, COMMA -> style(l[1], l[2], Style.PUNCT);
                default -> { }
            }
            if (!isKey || k + 2 >= lex.size()) {
                continue;
            }
            String name = unquote(l[1], l[2]);
            int[] v = lex.get(k + 2);
            if (v[0] == STR) {
                tokens.add(new Token(Token.Kind.JSON_MEMBER, name, l[1], v[2], v[1] + 1, Math.max(v[1] + 1, v[2] - 1)));
            } else if (v[0] == NUM || v[0] == LIT) {
                tokens.add(new Token(Token.Kind.JSON_MEMBER, name, l[1], v[2], v[1], v[2]));
            } else if (v[0] == OPEN && match[k + 2] >= 0) {
                int closeEnd = lex.get(match[k + 2])[2];
                tokens.add(new Token(Token.Kind.JSON_MEMBER, name, l[1], closeEnd, v[1], closeEnd));
            }
        }
    }

    private boolean isLiteral(int s, int e) {
        String w = text.substring(s, e);
        return w.equals("true") || w.equals("false") || w.equals("null");
    }

    private String unquote(int s, int e) {
        if (e - s >= 2) {
            return text.substring(s + 1, e - 1);
        }
        return text.substring(s, e);
    }

    private void parseSse(int start, int end) {
        int lineStart = start;
        while (lineStart < end) {
            int nl = text.indexOf('\n', lineStart);
            int lineEnd = nl < 0 || nl > end ? end : nl;
            int contentEnd = lineEnd > lineStart && text.charAt(lineEnd - 1) == '\r' ? lineEnd - 1 : lineEnd;
            int colon = text.indexOf(':', lineStart);
            if (colon == lineStart) {
                style(lineStart, contentEnd, Style.MUTED);
            } else if (colon > lineStart && colon < contentEnd) {
                style(lineStart, colon, Style.HEADER_NAME);
                style(colon, colon + 1, Style.PUNCT);
                int value = colon + 1;
                while (value < contentEnd && text.charAt(value) == ' ') value++;
                if (looksLikeJson(value, contentEnd) && text.substring(lineStart, colon).equals("data")) {
                    sseJson.add(new TextRange(value, contentEnd));
                    parseJson(value, contentEnd);
                } else {
                    style(value, contentEnd, Style.HEADER_VALUE);
                }
            }
            lineStart = lineEnd + 1;
        }
    }

    private boolean looksLikeJson(int start, int end) {
        String value = text.substring(start, end).strip();
        if (value.length() < 2) return false;
        char open = value.charAt(0), close = value.charAt(value.length() - 1);
        char next = value.substring(1).strip().charAt(0);
        return open == '{' ? close == '}' && (next == '"' || next == '}')
                : open == '[' && close == ']' && "{[\"-0123456789tfn]".indexOf(next) >= 0;
    }

    private void parseMarkup(int start, int end) {
        int i = start;
        while (i < end) {
            if (text.startsWith("<!--", i)) {
                int close = text.indexOf("-->", i + 4);
                int j = close < 0 ? end : Math.min(end, close + 3);
                style(i, j, Style.MUTED);
                i = j;
            } else if (text.charAt(i) == '<') {
                i = parseTag(i, end);
            } else {
                i++;
            }
        }
    }

    private int parseTag(int start, int end) {
        int i = start + 1;
        style(start, i, Style.PUNCT);
        while (i < end && (text.charAt(i) == '/' || text.charAt(i) == '!' || text.charAt(i) == '?')) {
            style(i, i + 1, Style.PUNCT);
            i++;
        }
        int nameStart = i;
        while (i < end && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '>' && text.charAt(i) != '/') {
            i++;
        }
        style(nameStart, i, Style.TAG);
        while (i < end && text.charAt(i) != '>') {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                int close = text.indexOf(c, i + 1);
                int j = close < 0 ? end : Math.min(end, close + 1);
                style(i, j, Style.STRING);
                i = j;
            } else if (c == '=' || c == '/') {
                style(i, i + 1, Style.PUNCT);
                i++;
            } else if (Character.isWhitespace(c)) {
                i++;
            } else {
                int s = i;
                while (i < end && !Character.isWhitespace(text.charAt(i)) && "=>/".indexOf(text.charAt(i)) < 0) {
                    i++;
                }
                style(s, i, Style.ATTR);
            }
        }
        if (i < end) {
            style(i, i + 1, Style.PUNCT);
            i++;
        }
        return i;
    }

    private void style(int s, int e, Style style) {
        for (int i = Math.max(0, s); i < Math.min(e, styles.length); i++) {
            styles[i] = style;
        }
    }

    private boolean isWord(int s, int e) {
        for (int i = s; i < e; i++) {
            if (!Character.isLetter(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private int trimEnd(int s, int e) {
        while (e > s && Character.isWhitespace(text.charAt(e - 1))) {
            e--;
        }
        return e;
    }
}
