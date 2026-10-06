package burpss.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class Templates {

    public record Applied(int found, List<String> missing) {
    }

    private static final Set<Token.Kind> SINGLE = EnumSet.of(Token.Kind.METHOD, Token.Kind.PATH, Token.Kind.VERSION, Token.Kind.STATUS_LINE);

    private Templates() {
    }

    public static Template capture(Exchange exchange) {
        Template t = new Template();
        EditState state = exchange.state;
        for (Mark mark : state.marks) {
            if (mark.anchor instanceof Anchor.Text a && exchange.pane(a.pane()) != null) {
                t.marks.add(new Template.MarkSpec(target(exchange.pane(a.pane()), a.pane(), a.start(), a.end()), mark.color, mark.note,
                        mark.calloutOffset == null ? null : (java.awt.geom.Point2D) mark.calloutOffset.clone()));
            }
        }
        for (int pane = 0; pane < 2; pane++) {
            HttpText message = exchange.pane(pane);
            if (message == null) continue;
            for (TextRange r : state.manualRedactions.get(pane)) t.redactions.add(target(message, pane, r.start(), r.end()));
            for (String key : state.suppressedAuto.get(pane)) {
                TextRange r = parseKey(key);
                if (r != null) t.unredactions.add(target(message, pane, r.start(), r.end()));
            }
            for (HttpText.HeaderLine h : message.headers()) {
                if (state.toggledHeaders.get(pane).contains(h.lineStart())) t.toggledHeaders.get(pane).add(h.name());
            }
        }
        t.hasMarks = !t.marks.isEmpty();
        t.hasRedactions = !t.redactions.isEmpty() || !t.unredactions.isEmpty();
        t.hasHeaders = !t.toggledHeaders.get(0).isEmpty() || !t.toggledHeaders.get(1).isEmpty();
        t.title = state.title;
        t.caption = state.caption;
        t.bodyOffset[0] = state.bodyOffset[0];
        t.bodyOffset[1] = state.bodyOffset[1];
        return t;
    }

    public static Applied apply(Template t, Exchange exchange, String path) {
        EditState state = exchange.state;
        int found = 0;
        List<String> missing = new ArrayList<>();
        if (t.hasMarks) {
            state.marks.clear();
            for (Template.MarkSpec spec : t.marks) {
                TextRange r = resolve(exchange, spec.target());
                if (r == null) {
                    missing.add(describe(spec.target()));
                    continue;
                }
                Mark mark = new Mark(new Anchor.Text(spec.target().pane(), r.start(), r.end()), spec.color());
                mark.note = spec.note();
                mark.calloutOffset = spec.calloutOffset() == null ? null : (java.awt.geom.Point2D) spec.calloutOffset().clone();
                state.marks.add(mark);
                found++;
            }
        }
        if (t.hasRedactions) {
            for (int pane = 0; pane < 2; pane++) {
                state.manualRedactions.get(pane).clear();
                state.suppressedAuto.get(pane).clear();
            }
            for (Template.Target target : t.redactions) {
                TextRange r = resolve(exchange, target);
                if (r == null) missing.add(describe(target));
                else {
                    state.manualRedactions.get(target.pane()).add(r);
                    found++;
                }
            }
            for (Template.Target target : t.unredactions) {
                TextRange r = resolve(exchange, target);
                if (r != null) state.suppressedAuto.get(target.pane()).add(r.key());
            }
        }
        if (t.hasHeaders) {
            for (int pane = 0; pane < 2; pane++) {
                state.toggledHeaders.get(pane).clear();
                HttpText message = exchange.pane(pane);
                if (message == null) continue;
                for (HttpText.HeaderLine h : message.headers()) {
                    String name = h.name();
                    if (t.toggledHeaders.get(pane).stream().anyMatch(name::equalsIgnoreCase)) state.toggledHeaders.get(pane).add(h.lineStart());
                }
            }
        }
        if (t.settings != null) {
            state.bodyOffset[0] = t.bodyOffset[0];
            state.bodyOffset[1] = t.bodyOffset[1];
        }
        if (t.title != null) state.title = Template.expand(t.title, exchange.method, path, exchange.host, exchange.status);
        if (t.caption != null) state.caption = Template.expand(t.caption, exchange.method, path, exchange.host, exchange.status);
        return new Applied(found, missing);
    }

    static Template.Target target(HttpText message, int pane, int start, int end) {
        for (Token t : message.tokens()) {
            if (t.start() == start && t.end() == end) return tokenTarget(message, pane, t, false);
        }
        for (Token t : message.tokens()) {
            if (t.hasValue() && t.valueStart() == start && t.valueEnd() == end) return tokenTarget(message, pane, t, true);
        }
        String text = message.text().substring(Math.max(0, start), Math.min(end, message.text().length()));
        int occurrence = 0;
        for (int at = message.text().indexOf(text); at >= 0 && at < start; at = message.text().indexOf(text, at + 1)) occurrence++;
        return new Template.Target(pane, null, null, false, occurrence, text);
    }

    static TextRange resolve(Exchange exchange, Template.Target target) {
        HttpText message = target.pane() < 2 ? exchange.pane(target.pane()) : null;
        if (message == null) return null;
        if (target.kind() != null) {
            List<Token> matches = message.tokens().stream().filter(t -> sameToken(t, target.kind(), target.name())).toList();
            if (matches.isEmpty()) return null;
            Token t = matches.get(target.occurrence() < matches.size() ? target.occurrence() : 0);
            if (!target.value()) return new TextRange(t.start(), t.end());
            return t.hasValue() ? new TextRange(t.valueStart(), t.valueEnd()) : null;
        }
        String text = target.text();
        if (text == null || text.isEmpty()) return null;
        String all = message.text();
        int at = all.indexOf(text);
        int first = at;
        for (int n = 0; n < target.occurrence() && at >= 0; n++) at = all.indexOf(text, at + 1);
        if (at < 0) at = first;
        return at < 0 ? null : new TextRange(at, at + text.length());
    }

    private static Template.Target tokenTarget(HttpText message, int pane, Token token, boolean value) {
        String name = SINGLE.contains(token.kind()) ? null : token.name();
        int occurrence = 0;
        for (Token t : message.tokens()) {
            if (t == token) break;
            if (sameToken(t, token.kind(), name)) occurrence++;
        }
        return new Template.Target(pane, token.kind(), name, value, occurrence, null);
    }

    private static boolean sameToken(Token t, Token.Kind kind, String name) {
        if (t.kind() != kind) return false;
        if (name == null) return true;
        return kind == Token.Kind.HEADER ? t.name().equalsIgnoreCase(name) : t.name().equals(name);
    }

    private static String describe(Template.Target t) {
        if (t.kind() == null) {
            String text = t.text() == null ? "" : t.text().strip();
            return "“" + (text.length() > 24 ? text.substring(0, 23) + "…" : text) + "”";
        }
        return switch (t.kind()) {
            case METHOD -> "method";
            case PATH -> "path";
            case VERSION -> "version";
            case STATUS_LINE -> "status line";
            case HEADER -> "header " + t.name();
            case COOKIE -> "cookie " + t.name();
            case QUERY_PARAM, BODY_PARAM -> "param " + t.name();
            case JSON_MEMBER -> "JSON " + t.name();
        };
    }

    private static TextRange parseKey(String key) {
        int colon = key.indexOf(':');
        try {
            return colon < 0 ? null : new TextRange(Integer.parseInt(key.substring(0, colon)), Integer.parseInt(key.substring(colon + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
