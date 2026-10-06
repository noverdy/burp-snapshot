package burpss.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EditState {

    public final List<Mark> marks = new ArrayList<>();
    public final List<List<TextRange>> manualRedactions = List.of(new ArrayList<>(), new ArrayList<>());
    public final List<Set<String>> suppressedAuto = List.of(new HashSet<>(), new HashSet<>());
    public final List<Set<Integer>> toggledHeaders = List.of(new HashSet<>(), new HashSet<>());
    public final Map<Integer, String> payloadOverrides = new HashMap<>();
    public final Set<Integer> hiddenRows = new HashSet<>();
    public String title = "";
    public String caption = "";
    public String aiContext = "";

    public EditState copy() {
        EditState s = new EditState();
        for (Mark m : marks) {
            s.marks.add(m.copy());
        }
        for (int p = 0; p < 2; p++) {
            s.manualRedactions.get(p).addAll(manualRedactions.get(p));
            s.suppressedAuto.get(p).addAll(suppressedAuto.get(p));
            s.toggledHeaders.get(p).addAll(toggledHeaders.get(p));
        }
        s.payloadOverrides.putAll(payloadOverrides);
        s.hiddenRows.addAll(hiddenRows);
        s.title = title;
        s.caption = caption;
        s.aiContext = aiContext;
        return s;
    }

    public void restore(EditState other) {
        EditState o = other.copy();
        marks.clear();
        marks.addAll(o.marks);
        for (int p = 0; p < 2; p++) {
            manualRedactions.get(p).clear();
            manualRedactions.get(p).addAll(o.manualRedactions.get(p));
            suppressedAuto.get(p).clear();
            suppressedAuto.get(p).addAll(o.suppressedAuto.get(p));
            toggledHeaders.get(p).clear();
            toggledHeaders.get(p).addAll(o.toggledHeaders.get(p));
        }
        payloadOverrides.clear();
        payloadOverrides.putAll(o.payloadOverrides);
        hiddenRows.clear();
        hiddenRows.addAll(o.hiddenRows);
        title = o.title;
        caption = o.caption;
        aiContext = o.aiContext;
    }
}
