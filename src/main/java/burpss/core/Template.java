package burpss.core;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Template {

    public record Target(int pane, Token.Kind kind, String name, boolean value, int occurrence, String text) {
    }

    public record MarkSpec(Target target, int color, String note, Point2D calloutOffset) {
    }

    public String id = UUID.randomUUID().toString();
    public String name = "";
    public long lastUsed;
    public final List<MarkSpec> marks = new ArrayList<>();
    public final List<Target> redactions = new ArrayList<>();
    public final List<Target> unredactions = new ArrayList<>();
    public final List<List<String>> toggledHeaders = List.of(new ArrayList<>(), new ArrayList<>());
    public boolean hasMarks, hasRedactions, hasHeaders;
    public String title, caption, settings;
    public final int[] bodyOffset = new int[2];

    public String summary() {
        List<String> parts = new ArrayList<>();
        if (hasMarks) parts.add(marks.size() + (marks.size() == 1 ? " mark" : " marks"));
        if (hasRedactions) parts.add(redactions.size() + (redactions.size() == 1 ? " redaction" : " redactions"));
        if (title != null) parts.add("title");
        if (caption != null) parts.add("caption");
        if (hasHeaders) parts.add("headers");
        if (settings != null) parts.add("settings");
        return parts.isEmpty() ? "empty" : String.join(" · ", parts);
    }

    public static String generalize(String text, String method, String path, String host, int status) {
        String out = text;
        if (path.length() > 1) out = out.replace(path, "{path}");
        if (!host.isEmpty()) out = out.replace(host, "{host}");
        out = out.replaceAll("\\b" + Pattern.quote(method) + "\\b", Matcher.quoteReplacement("{method}"));
        if (status > 0) out = out.replaceAll("\\b" + status + "\\b", "{status}");
        return out;
    }

    public static String expand(String text, String method, String path, String host, int status) {
        return text.replace("{method}", method).replace("{path}", path).replace("{host}", host)
                .replace("{status}", status > 0 ? String.valueOf(status) : "");
    }

    public String encode() {
        Properties p = new Properties();
        p.setProperty("id", id);
        p.setProperty("name", name);
        p.setProperty("lastUsed", String.valueOf(lastUsed));
        p.setProperty("hasMarks", String.valueOf(hasMarks));
        p.setProperty("hasRedactions", String.valueOf(hasRedactions));
        p.setProperty("hasHeaders", String.valueOf(hasHeaders));
        if (title != null) p.setProperty("title", title);
        if (caption != null) p.setProperty("caption", caption);
        if (settings != null) {
            p.setProperty("settings", settings);
            p.setProperty("offset.0", String.valueOf(bodyOffset[0]));
            p.setProperty("offset.1", String.valueOf(bodyOffset[1]));
        }
        for (int i = 0; i < marks.size(); i++) {
            MarkSpec m = marks.get(i);
            String key = "mark." + i + ".";
            putTarget(p, key, m.target());
            p.setProperty(key + "color", String.valueOf(m.color()));
            p.setProperty(key + "note", m.note());
            if (m.calloutOffset() != null) {
                p.setProperty(key + "dx", String.valueOf(m.calloutOffset().getX()));
                p.setProperty(key + "dy", String.valueOf(m.calloutOffset().getY()));
            }
        }
        putTargets(p, "redact.", redactions);
        putTargets(p, "unredact.", unredactions);
        for (int pane = 0; pane < 2; pane++) {
            List<String> names = toggledHeaders.get(pane);
            for (int i = 0; i < names.size(); i++) p.setProperty("header." + pane + "." + i, names.get(i));
        }
        StringWriter out = new StringWriter();
        try {
            p.store(out, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    public static Template decode(String encoded) {
        Properties p = new Properties();
        try {
            p.load(new StringReader(encoded));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Template t = new Template();
        t.id = p.getProperty("id", t.id);
        t.name = p.getProperty("name", "");
        t.lastUsed = number(p.getProperty("lastUsed"));
        t.hasMarks = Boolean.parseBoolean(p.getProperty("hasMarks"));
        t.hasRedactions = Boolean.parseBoolean(p.getProperty("hasRedactions"));
        t.hasHeaders = Boolean.parseBoolean(p.getProperty("hasHeaders"));
        t.title = p.getProperty("title");
        t.caption = p.getProperty("caption");
        t.settings = p.getProperty("settings");
        t.bodyOffset[0] = (int) number(p.getProperty("offset.0"));
        t.bodyOffset[1] = (int) number(p.getProperty("offset.1"));
        for (int i = 0; p.containsKey("mark." + i + ".pane"); i++) {
            String key = "mark." + i + ".";
            Point2D offset = p.containsKey(key + "dx")
                    ? new Point2D.Double(Double.parseDouble(p.getProperty(key + "dx")), Double.parseDouble(p.getProperty(key + "dy")))
                    : null;
            t.marks.add(new MarkSpec(target(p, key), (int) number(p.getProperty(key + "color")), p.getProperty(key + "note", ""), offset));
        }
        t.redactions.addAll(targets(p, "redact."));
        t.unredactions.addAll(targets(p, "unredact."));
        for (int pane = 0; pane < 2; pane++) {
            for (int i = 0; p.containsKey("header." + pane + "." + i); i++) {
                t.toggledHeaders.get(pane).add(p.getProperty("header." + pane + "." + i));
            }
        }
        return t;
    }

    private static void putTargets(Properties p, String prefix, List<Target> targets) {
        for (int i = 0; i < targets.size(); i++) putTarget(p, prefix + i + ".", targets.get(i));
    }

    private static List<Target> targets(Properties p, String prefix) {
        List<Target> out = new ArrayList<>();
        for (int i = 0; p.containsKey(prefix + i + ".pane"); i++) out.add(target(p, prefix + i + "."));
        return out;
    }

    private static void putTarget(Properties p, String key, Target t) {
        p.setProperty(key + "pane", String.valueOf(t.pane()));
        p.setProperty(key + "occurrence", String.valueOf(t.occurrence()));
        if (t.value()) p.setProperty(key + "value", "true");
        if (t.kind() != null) p.setProperty(key + "kind", t.kind().name());
        if (t.name() != null) p.setProperty(key + "name", t.name());
        if (t.text() != null) p.setProperty(key + "text", t.text());
    }

    private static Target target(Properties p, String key) {
        String kind = p.getProperty(key + "kind");
        return new Target((int) number(p.getProperty(key + "pane")), kind == null ? null : Token.Kind.valueOf(kind),
                p.getProperty(key + "name"), Boolean.parseBoolean(p.getProperty(key + "value")),
                (int) number(p.getProperty(key + "occurrence")), p.getProperty(key + "text"));
    }

    private static long number(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
