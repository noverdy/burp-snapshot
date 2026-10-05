package burpss.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Names {

    private Names() {
    }

    public static List<String> split(String list) {
        List<String> out = new ArrayList<>();
        for (String part : list.split("[,\\n]")) {
            String p = part.trim().toLowerCase(Locale.ROOT);
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    public static boolean globMatches(List<String> patterns, String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String p : patterns) {
            if (p.startsWith("*") && p.endsWith("*") && p.length() > 1) {
                if (n.contains(p.substring(1, p.length() - 1))) {
                    return true;
                }
            } else if (p.endsWith("*")) {
                if (n.startsWith(p.substring(0, p.length() - 1))) {
                    return true;
                }
            } else if (p.startsWith("*")) {
                if (n.endsWith(p.substring(1))) {
                    return true;
                }
            } else if (n.equals(p)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsAny(List<String> keywords, String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String k : keywords) {
            if (n.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
