package burpss.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AutoRedactor {

    private AutoRedactor() {
    }

    public static List<TextRange> find(HttpText message, Settings settings) {
        List<String> headerNames = Names.split(settings.redactHeaders);
        List<String> keywords = Names.split(settings.redactParams);
        Set<TextRange> out = new LinkedHashSet<>();
        for (Token t : message.tokens()) {
            switch (t.kind()) {
                case HEADER -> {
                    if (!Names.globMatches(headerNames, t.name())) {
                        break;
                    }
                    String lower = t.name().toLowerCase(Locale.ROOT);
                    if (lower.equals("cookie") || lower.equals("set-cookie")) {
                        for (Token c : message.tokens()) {
                            if (c.kind() == Token.Kind.COOKIE && c.start() >= t.start() && c.end() <= t.end()) {
                                addValue(out, c);
                            }
                        }
                    } else {
                        addValue(out, t);
                    }
                }
                case QUERY_PARAM, BODY_PARAM, COOKIE -> {
                    if (Names.containsAny(keywords, t.name())) {
                        addValue(out, t);
                    }
                }
                case JSON_MEMBER -> {
                    char first = t.hasValue() ? message.text().charAt(t.valueStart()) : ' ';
                    if (Names.containsAny(keywords, t.name()) && first != '{' && first != '[') {
                        addValue(out, t);
                    }
                }
                default -> { }
            }
        }
        return new ArrayList<>(out);
    }

    private static void addValue(Set<TextRange> out, Token t) {
        if (t.hasValue()) {
            out.add(new TextRange(t.valueStart(), t.valueEnd()));
        }
    }
}
