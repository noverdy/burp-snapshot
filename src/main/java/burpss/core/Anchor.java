package burpss.core;

public sealed interface Anchor permits Anchor.Text, Anchor.Rows {

    record Text(int pane, int start, int end) implements Anchor {
    }

    record Rows(int first, int last) implements Anchor {
    }
}
