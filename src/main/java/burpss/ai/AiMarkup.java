package burpss.ai;

import burpss.core.Anchor;
import burpss.core.Exchange;
import burpss.core.HttpText;
import burpss.core.Mark;
import burpss.core.Token;
import burpss.render.ExchangeContent;
import burpss.render.TableContent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AiMarkup {

    public interface Model {
        boolean available();

        String ask(String system, String user) throws Exception;
    }

    public record Result(String title, String caption, List<Mark> marks, int unmatched) {
    }

    private static final int MAX_PANE_CHARS = 15_000;

    private static final String RULES = """
            You help a penetration tester annotate a screenshot for a security report.
            Using the tester's context, pick the few elements (usually 2 to 5) that prove the finding.
            Write a short note for each mark (at most 8 words), a concise report title (at most 10 words),
            and an evidence caption: one or two neutral, past-tense sentences describing what the screenshot shows,
            suitable as a figure caption in a report.
            Text shown as ‹redacted› is a hidden secret. Never guess it, but you may mark the element that holds it.
            Reply with JSON only, no prose and no code fences.
            """;

    public static final String EXCHANGE_SYSTEM = RULES + """
            Schema:
            {"title": string, "caption": string, "marks": [{"pane": "request" | "response", "target": TARGET, "note": string}]}
            TARGET is exactly one of:
              {"header": "<header name>"}            a request or response header
              {"param": "<name>"}                    a query, form body or cookie parameter
              {"json": "<key>"}                      a JSON member, by key name (dotted path allowed)
              {"status": true}                       the response status line
              {"text": "<exact text>"}               any exact text copied from the message, e.g. a path segment like "/projects/1337"
            Prefer header, param, json and status targets. A query string parameter such as merchantId=... is a param target.
            Use text only for parts of the URL path or body that have no key, and keep it as short as possible:
            only the characters that matter, never a whole line or the whole request line.
            """;

    public static final String TABLE_SYSTEM = RULES + """
            The screenshot is a table of attack results, one row per request.
            Schema:
            {"title": string, "caption": string, "marks": [{"rows": [first, last], "note": string}]}
            first and last are row numbers from the # column. Use the same number twice for a single row.
            """;

    private AiMarkup() {
    }

    public static String exchangePrompt(String context, Exchange exchange, ExchangeContent content) {
        StringBuilder out = new StringBuilder("Tester's context: ").append(context.isBlank() ? "(none)" : context.strip())
                .append("\n\n").append(exchange.method).append(' ').append(exchange.url).append('\n');
        for (int pane = 0; pane < content.paneCount(); pane++) {
            out.append("\n=== ").append(pane == 0 ? "REQUEST" : "RESPONSE").append(" (as shown in the screenshot) ===\n")
                    .append(clip(content.visible(pane).text())).append('\n');
        }
        return out.toString();
    }

    public static String tablePrompt(String context, String method, String url, TableContent content) {
        return "Tester's context: " + (context.isBlank() ? "(none)" : context.strip())
                + "\n\nAttack on " + method + " " + url + "\n\n=== RESULTS ===\n"
                + clip(String.join("\n", content.describeRows())) + "\n";
    }

    public static Result exchangeResult(String reply, Exchange exchange, ExchangeContent content, int color) {
        Map<?, ?> root = root(reply);
        List<Mark> marks = new ArrayList<>();
        int unmatched = 0;
        for (Map<?, ?> m : maps(root.get("marks"))) {
            int pane = "response".equalsIgnoreCase(text(m.get("pane"))) ? 1 : 0;
            Map<?, ?> target = m.get("target") instanceof Map<?, ?> t ? t : m;
            Anchor anchor = pane < content.paneCount() ? resolve(exchange.pane(pane), content.visible(pane), pane, target, text(m.get("note"))) : null;
            if (anchor == null || marks.stream().anyMatch(x -> x.anchor.equals(anchor))) {
                unmatched += anchor == null ? 1 : 0;
                continue;
            }
            marks.add(mark(anchor, color, m));
        }
        return new Result(text(root.get("title")), text(root.get("caption")), marks, unmatched);
    }

    public static Result tableResult(String reply, TableContent content, int color) {
        Map<?, ?> root = root(reply);
        List<Integer> visible = content.rowNumbers();
        List<Mark> marks = new ArrayList<>();
        int unmatched = 0;
        for (Map<?, ?> m : maps(root.get("marks"))) {
            List<?> rows = m.get("rows") instanceof List<?> l ? l : List.of(java.util.Objects.requireNonNullElse(m.get("row"), -1));
            int first = number(rows.isEmpty() ? null : rows.get(0)), last = number(rows.isEmpty() ? null : rows.get(rows.size() - 1));
            if (!visible.contains(first) || !visible.contains(last)) {
                unmatched++;
                continue;
            }
            marks.add(mark(new Anchor.Rows(Math.min(first, last), Math.max(first, last)), color, m));
        }
        return new Result(text(root.get("title")), text(root.get("caption")), marks, unmatched);
    }

    private static Anchor resolve(HttpText message, ExchangeContent.Visible visible, int pane, Map<?, ?> target, String note) {
        if (target.get("header") instanceof String name) {
            return token(message, visible, pane, EnumSet.of(Token.Kind.HEADER), name);
        }
        if (target.get("param") instanceof String name) {
            return token(message, visible, pane, EnumSet.of(Token.Kind.QUERY_PARAM, Token.Kind.BODY_PARAM, Token.Kind.COOKIE), name);
        }
        if (target.get("json") instanceof String path) {
            String key = path.replaceAll("\\[\\d*]", "");
            key = key.substring(key.lastIndexOf('.') + 1);
            return token(message, visible, pane, EnumSet.of(Token.Kind.JSON_MEMBER), key);
        }
        if (Boolean.TRUE.equals(target.get("status"))) {
            return token(message, visible, pane, EnumSet.of(Token.Kind.STATUS_LINE), null);
        }
        if (target.get("text") instanceof String text && !text.isBlank()) {
            return narrow(message, text(visible, pane, text.strip()), note);
        }
        return null;
    }

    private static Anchor token(HttpText message, ExchangeContent.Visible visible, int pane, Set<Token.Kind> kinds, String name) {
        for (boolean exact : new boolean[]{true, false}) {
            for (Token t : message.tokens()) {
                if (!kinds.contains(t.kind()) || !shown(visible, t.start(), t.end())) continue;
                if (name == null || (exact ? t.name().equals(name) : t.name().equalsIgnoreCase(name))) {
                    return new Anchor.Text(pane, t.start(), t.end());
                }
            }
        }
        return null;
    }

    private static Anchor narrow(HttpText message, Anchor anchor, String note) {
        if (!(anchor instanceof Anchor.Text span) || note.isBlank()) return anchor;
        String lower = note.toLowerCase(Locale.ROOT);
        Set<Token.Kind> named = EnumSet.of(Token.Kind.QUERY_PARAM, Token.Kind.BODY_PARAM, Token.Kind.COOKIE,
                Token.Kind.JSON_MEMBER, Token.Kind.HEADER);
        Token best = null;
        for (Token t : message.tokens()) {
            boolean inside = t.start() >= span.start() && t.end() <= span.end() && t.length() < span.end() - span.start();
            if (inside && named.contains(t.kind()) && t.name().length() > 1
                    && lower.matches("(?s).*\\b" + java.util.regex.Pattern.quote(t.name().toLowerCase(Locale.ROOT)) + "\\b.*")
                    && (best == null || t.name().length() > best.name().length())) {
                best = t;
            }
        }
        return best == null ? anchor : new Anchor.Text(span.pane(), best.start(), best.end());
    }

    private static Anchor text(ExchangeContent.Visible visible, int pane, String text) {
        int at = visible.text().indexOf(text);
        if (at < 0) at = visible.text().toLowerCase(Locale.ROOT).indexOf(text.toLowerCase(Locale.ROOT));
        if (at < 0) return null;
        int lo = Integer.MAX_VALUE, hi = -1;
        for (int i = at; i < at + text.length(); i++) {
            int o = visible.origin()[i];
            if (o < 0) continue;
            lo = Math.min(lo, o);
            hi = Math.max(hi, o);
        }
        return hi < 0 ? null : new Anchor.Text(pane, lo, hi + 1);
    }

    private static boolean shown(ExchangeContent.Visible visible, int start, int end) {
        for (int o : visible.origin()) if (o >= start && o < end) return true;
        return false;
    }

    private static Mark mark(Anchor anchor, int color, Map<?, ?> source) {
        Mark mark = new Mark(anchor, color);
        mark.note = text(source.get("note"));
        return mark;
    }

    private static Map<?, ?> root(String reply) {
        if (Json.parse(reply) instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException("Burp AI did not reply with a JSON object");
    }

    private static List<Map<?, ?>> maps(Object value) {
        List<Map<?, ?>> out = new ArrayList<>();
        if (value instanceof List<?> list) for (Object o : list) if (o instanceof Map<?, ?> m) out.add(m);
        return out;
    }

    private static String text(Object value) {
        return value instanceof String s ? s.strip() : "";
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : -1;
    }

    private static String clip(String text) {
        return text.length() <= MAX_PANE_CHARS ? text : text.substring(0, MAX_PANE_CHARS) + "\n… (cut for length)";
    }
}
