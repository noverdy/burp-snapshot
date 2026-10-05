package burpss.core;

import java.util.ArrayList;
import java.util.List;

public final class PayloadDiff {

    private static final int MIN_ANCHOR = 3;
    private static final int MAX_SCAN = 400;

    private PayloadDiff() {
    }

    public static List<String> labels(List<String> requests) {
        List<String> cleaned = requests.stream().map(r -> r.replaceAll("(?im)^content-length:.*\\r?\\n", "")).toList();
        List<List<String>> segments = new ArrayList<>();
        cleaned.forEach(r -> segments.add(new ArrayList<>()));
        if (cleaned.size() > 1) collect(cleaned, segments);
        return segments.stream().map(s -> String.join(" | ", s)).toList();
    }

    private static void collect(List<String> texts, List<List<String>> out) {
        int prefix = commonPrefix(texts);
        int suffix = commonSuffix(texts, prefix);
        List<String> middles = texts.stream().map(t -> t.substring(prefix, t.length() - suffix)).toList();
        if (middles.stream().allMatch(String::isEmpty)) return;
        String anchor = commonAnchor(middles);
        if (anchor == null) {
            for (int i = 0; i < middles.size(); i++) out.get(i).add(middles.get(i));
            return;
        }
        List<String> left = new ArrayList<>(), right = new ArrayList<>();
        for (String m : middles) {
            int at = m.indexOf(anchor);
            left.add(m.substring(0, at));
            right.add(m.substring(at + anchor.length()));
        }
        collect(left, out);
        collect(right, out);
    }

    private static int commonPrefix(List<String> texts) {
        int n = texts.stream().mapToInt(String::length).min().orElse(0);
        for (int i = 0; i < n; i++) {
            char c = texts.get(0).charAt(i);
            for (String t : texts) if (t.charAt(i) != c) return i;
        }
        return n;
    }

    private static int commonSuffix(List<String> texts, int prefix) {
        int n = texts.stream().mapToInt(String::length).min().orElse(0) - prefix;
        for (int i = 0; i < n; i++) {
            String first = texts.get(0);
            char c = first.charAt(first.length() - 1 - i);
            for (String t : texts) if (t.charAt(t.length() - 1 - i) != c) return i;
        }
        return Math.max(0, n);
    }

    private static String commonAnchor(List<String> middles) {
        String shortest = middles.stream().min((a, b) -> a.length() - b.length()).orElse("");
        if (shortest.length() > MAX_SCAN) return null;
        for (int len = shortest.length(); len >= MIN_ANCHOR; len--) {
            for (int start = 0; start + len <= shortest.length(); start++) {
                String candidate = shortest.substring(start, start + len);
                if (middles.stream().allMatch(m -> m.contains(candidate))) return candidate;
            }
        }
        return null;
    }
}
