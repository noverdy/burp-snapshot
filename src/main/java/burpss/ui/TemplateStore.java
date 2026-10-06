package burpss.ui;

import burpss.core.Template;

import java.util.Comparator;
import java.util.List;

public interface TemplateStore {

    List<Template> templates();

    void saveTemplate(Template template);

    void deleteTemplate(String id);

    default List<Template> recent() {
        return templates().stream()
                .sorted(Comparator.comparingLong((Template t) -> t.lastUsed).reversed().thenComparing(t -> t.name.toLowerCase()))
                .toList();
    }
}
