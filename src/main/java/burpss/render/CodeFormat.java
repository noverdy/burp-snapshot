package burpss.render;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

final class CodeFormat {

    private record Frame(boolean object, int parens, int brackets) {
    }

    private static final String REGEX_AFTER = "(,=:[!&|?{};+-*%<>~^";
    private static final Set<String> REGEX_WORDS = Set.of("return", "typeof", "case", "in", "of", "delete", "void", "throw", "new", "yield");
    private static final Set<String> CONTINUATIONS = Set.of("else", "catch", "finally", "while");

    private final DisplayText out;
    private final String s;
    private final boolean js;
    private final int base;
    private final Deque<Frame> frames = new ArrayDeque<>();
    private int depth, parens, brackets;
    private boolean space, pendingLine;
    private char last;
    private String lastWord = "";

    private CodeFormat(DisplayText out, String s, int base, boolean js) {
        this.out = out;
        this.s = s;
        this.base = base;
        this.js = js;
    }

    static void format(DisplayText out, String s, int from, int to, int baseDepth, boolean js) {
        new CodeFormat(out, s, baseDepth, js).run(from, to);
    }

    private void run(int from, int to) {
        int i = from;
        while (i < to) {
            char c = s.charAt(i);
            char next = i + 1 < to ? s.charAt(i + 1) : 0;
            if (Character.isWhitespace(c)) {
                int j = i;
                boolean lineBreak = false;
                while (j < to && Character.isWhitespace(s.charAt(j))) lineBreak |= s.charAt(j++) == '\n';
                char following = j < to ? s.charAt(j) : 0;
                boolean operandEnded = Character.isLetterOrDigit(last) || "_$)]\"'`".indexOf(last) >= 0;
                boolean statementStarts = Character.isLetter(following) || following == '_' || following == '$';
                if (js && lineBreak && statementLevel() && operandEnded && statementStarts) newline();
                else space = true;
                i = j;
            } else if (c == '/' && next == '*') {
                int close = s.indexOf("*/", i + 2);
                int end = close < 0 || close + 2 > to ? to : close + 2;
                boolean ownLine = atLineStart();
                span(i, end, false);
                if (ownLine) newline();
                i = end;
            } else if (js && c == '/' && next == '/') {
                int end = i;
                while (end < to && s.charAt(end) != '\n') end++;
                span(i, end, false);
                newline();
                i = end;
            } else if (c == '"' || c == '\'' || (js && c == '`')) {
                int end = i + 1;
                while (end < to && s.charAt(end) != c) end += s.charAt(end) == '\\' ? 2 : 1;
                end = Math.min(to, end + 1);
                span(i, end, true);
                i = end;
            } else if (js && c == '/' && regexAllowed()) {
                int end = regexEnd(i, to);
                span(i, end, true);
                i = end;
            } else {
                i = punctuation(c, i, to);
            }
        }
    }

    private int punctuation(char c, int i, int to) {
        switch (c) {
            case '{' -> {
                int after = skipSpace(i + 1, to);
                if (after < to && s.charAt(after) == '}') {
                    emit('{', i);
                    emit('}', after);
                    return after + 1;
                }
                boolean object = js && objectStart();
                space |= last == ')' || wordChar(last);
                emit(c, i);
                frames.push(new Frame(object, parens, brackets));
                depth++;
                newline();
            }
            case '}' -> {
                depth = Math.max(0, depth - 1);
                if (!frames.isEmpty()) frames.pop();
                newline();
                emit(c, i);
                int after = skipSpace(i + 1, to);
                char following = after < to ? s.charAt(after) : 0;
                if (!js || !(following == ';' || following == ',' || following == ')' || following == '.' || following == ']'
                        || following == '(' || CONTINUATIONS.contains(word(after, to)))) newline();
                else if (Character.isLetter(following)) space = true;
            }
            case ';' -> {
                emit(c, i);
                if (parens > (frames.isEmpty() ? 0 : frames.peek().parens())) space = true;
                else newline();
            }
            case ',' -> {
                emit(c, i);
                if (inObject()) newline();
                else space = true;
            }
            case '(' -> {
                emit(c, i);
                parens++;
            }
            case ')' -> {
                parens = Math.max(0, parens - 1);
                emit(c, i);
            }
            case '[' -> {
                emit(c, i);
                brackets++;
            }
            case ']' -> {
                brackets = Math.max(0, brackets - 1);
                emit(c, i);
            }
            default -> emit(c, i);
        }
        return i + 1;
    }

    private boolean inObject() {
        Frame f = frames.peek();
        return f != null && f.object() && parens == f.parens() && brackets == f.brackets();
    }

    private boolean statementLevel() {
        Frame f = frames.peek();
        return f == null ? parens == 0 && brackets == 0 : !f.object() && parens == f.parens() && brackets == f.brackets();
    }

    private boolean objectStart() {
        return "=(,:[?".indexOf(last) >= 0 || lastWord.equals("return");
    }

    private boolean regexAllowed() {
        return last == 0 || REGEX_AFTER.indexOf(last) >= 0 || REGEX_WORDS.contains(lastWord);
    }

    private int regexEnd(int i, int to) {
        int j = i + 1;
        boolean inClass = false;
        while (j < to) {
            char c = s.charAt(j);
            if (c == '\\') j++;
            else if (c == '[') inClass = true;
            else if (c == ']') inClass = false;
            else if (c == '/' && !inClass) break;
            else if (c == '\n') return j;
            j++;
        }
        j = Math.min(to, j + 1);
        while (j < to && Character.isLetter(s.charAt(j))) j++;
        return j;
    }

    private String word(int i, int to) {
        int j = i;
        while (j < to && Character.isLetter(s.charAt(j))) j++;
        return s.substring(i, j);
    }

    private int skipSpace(int i, int to) {
        while (i < to && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private void span(int from, int to, boolean operand) {
        emit(s.charAt(from), from);
        out.copy(from + 1, to);
        if (operand) {
            last = '"';
            lastWord = "";
        }
    }

    private void emit(char c, int origin) {
        if (pendingLine) out.add('\n', DisplayText.INSERTED);
        if (atLineStart()) {
            for (int k = 0; k < Math.min(base + depth, MarkupFormat.MAX_INDENT) * 2; k++) out.add(' ', DisplayText.INSERTED);
        } else if (space) {
            out.add(' ', DisplayText.INSERTED);
        }
        space = false;
        pendingLine = false;
        out.copy(origin, origin + 1);
        lastWord = wordChar(c) ? (wordChar(last) ? lastWord + c : String.valueOf(c)) : "";
        last = c;
    }

    private static boolean wordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private void newline() {
        if (!atLineStart()) pendingLine = true;
        space = false;
    }

    private boolean atLineStart() {
        return pendingLine || out.length() == 0 || out.charAt(out.length() - 1) == '\n';
    }
}
