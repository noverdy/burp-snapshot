package burpss.core;

public record Token(Kind kind, String name, int start, int end, int valueStart, int valueEnd) {

    public enum Kind {
        METHOD, PATH, VERSION, STATUS_LINE, HEADER, COOKIE, QUERY_PARAM, BODY_PARAM, JSON_MEMBER
    }

    public boolean contains(int offset) {
        return offset >= start && offset < end;
    }

    public int length() {
        return end - start;
    }

    public boolean hasValue() {
        return valueEnd > valueStart;
    }
}
