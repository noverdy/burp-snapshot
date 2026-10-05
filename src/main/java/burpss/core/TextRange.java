package burpss.core;

public record TextRange(int start, int end) {

    public boolean contains(int offset) {
        return offset >= start && offset < end;
    }

    public String key() {
        return start + ":" + end;
    }
}
