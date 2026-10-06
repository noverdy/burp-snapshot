package burpss.core;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class Shortcuts {

    public static final String QUICK_COPY = "Ctrl+Shift+C", OPEN = "Ctrl+Shift+X", TITLE = "{method} {path}";

    private static final Set<String> BURP_DEFAULTS = Set.of(
            "Ctrl+A", "Ctrl+B", "Ctrl+C", "Ctrl+D", "Ctrl+E", "Ctrl+F", "Ctrl+G", "Ctrl+H", "Ctrl+I", "Ctrl+K",
            "Ctrl+O", "Ctrl+R", "Ctrl+S", "Ctrl+T", "Ctrl+U", "Ctrl+V", "Ctrl+X", "Ctrl+Y", "Ctrl+Z",
            "Ctrl+Shift+B", "Ctrl+Shift+D", "Ctrl+Shift+G", "Ctrl+Shift+H", "Ctrl+Shift+I", "Ctrl+Shift+L",
            "Ctrl+Shift+O", "Ctrl+Shift+P", "Ctrl+Shift+R", "Ctrl+Shift+T", "Ctrl+Shift+U", "Ctrl+Alt+C",
            "Ctrl+Backspace", "Ctrl+Delete", "Ctrl+Enter", "Ctrl+Comma", "Ctrl+Period", "Ctrl+Equals", "Ctrl+Minus",
            "Ctrl+Up", "Ctrl+Down", "Ctrl+Left", "Ctrl+Right", "Ctrl+Home", "Ctrl+End",
            "Ctrl+Shift+Up", "Ctrl+Shift+Down", "Ctrl+Shift+Left", "Ctrl+Shift+Right", "Ctrl+Shift+Home", "Ctrl+Shift+End");

    private static final String PREFIX = "burpss.shortcuts.";

    public String quickCopy = QUICK_COPY;
    public String open = OPEN;
    public String titleTemplate = TITLE;

    public static Shortcuts load(Settings.Store store) {
        Shortcuts s = new Shortcuts();
        s.quickCopy = valueOr(store.get(PREFIX + "quickCopy"), QUICK_COPY);
        s.open = valueOr(store.get(PREFIX + "open"), OPEN);
        s.titleTemplate = valueOr(store.get(PREFIX + "titleTemplate"), TITLE);
        return s;
    }

    public void save(Settings.Store store) {
        store.set(PREFIX + "quickCopy", quickCopy);
        store.set(PREFIX + "open", open);
        store.set(PREFIX + "titleTemplate", titleTemplate);
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }

    public static boolean usedByBurp(String hotkey) {
        return !hotkey.isBlank() && BURP_DEFAULTS.stream().anyMatch(d -> normalize(d).equals(normalize(hotkey)));
    }

    private static String normalize(String hotkey) {
        return Arrays.stream(hotkey.split("\\+")).map(String::strip).sorted().collect(Collectors.joining("+"));
    }

    public String title(String method, String path, String host, int status) {
        return titleTemplate
                .replace("{method}", method)
                .replace("{path}", path)
                .replace("{host}", host)
                .replace("{status}", status > 0 ? String.valueOf(status) : "")
                .replace("{date}", LocalDate.now().toString())
                .strip();
    }
}
