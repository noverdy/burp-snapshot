package burpss.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        if (start < 0 || end < start) throw new IllegalArgumentException("No JSON object in the reply");
        Json json = new Json(text.substring(start, end + 1));
        return json.value();
    }

    private Object value() {
        skip();
        if (i >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return true; }
        if (s.startsWith("false", i)) { i += 5; return false; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw new IllegalArgumentException("Unexpected '" + c + "' in JSON");
        return Double.parseDouble(s.substring(start, i));
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        i++;
        skip();
        if (peek('}')) return map;
        do {
            skip();
            String key = string();
            skip();
            expect(':');
            map.put(key, value());
            skip();
        } while (next(','));
        expect('}');
        return map;
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        i++;
        skip();
        if (peek(']')) return list;
        do {
            list.add(value());
            skip();
        } while (next(','));
        expect(']');
        return list;
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (i < s.length() && s.charAt(i) != '"') {
            char c = s.charAt(i++);
            if (c != '\\' || i >= s.length()) {
                out.append(c);
                continue;
            }
            char e = s.charAt(i++);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> out.append(e);
            }
        }
        expect('"');
        return out.toString();
    }

    private void skip() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private boolean peek(char c) {
        if (i < s.length() && s.charAt(i) == c) {
            i++;
            return true;
        }
        return false;
    }

    private boolean next(char c) {
        skip();
        return peek(c);
    }

    private void expect(char c) {
        if (!peek(c)) throw new IllegalArgumentException("Expected '" + c + "' in JSON");
    }
}
