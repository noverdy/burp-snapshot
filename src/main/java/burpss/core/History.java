package burpss.core;

import java.util.ArrayDeque;
import java.util.Deque;

public final class History {

    private static final int LIMIT = 100;

    private final EditState state;
    private final Deque<EditState> undo = new ArrayDeque<>();
    private final Deque<EditState> redo = new ArrayDeque<>();

    public History(EditState state) {
        this.state = state;
    }

    public void checkpoint() {
        undo.push(state.copy());
        if (undo.size() > LIMIT) {
            undo.removeLast();
        }
        redo.clear();
    }

    public boolean undo() {
        if (undo.isEmpty()) {
            return false;
        }
        redo.push(state.copy());
        state.restore(undo.pop());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) {
            return false;
        }
        undo.push(state.copy());
        state.restore(redo.pop());
        return true;
    }
}
