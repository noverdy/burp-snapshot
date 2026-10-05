package burpss.render;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.util.Arrays;
import java.util.List;

public final class Fonts {

    public static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final List<String> INSTALLED =
            Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
    private static String mono = pick("JetBrains Mono", "SF Mono", "Menlo", "Consolas", "DejaVu Sans Mono", Font.MONOSPACED);
    private static String sans = pick("Inter", "SF Pro Text", "Helvetica Neue", "Segoe UI", "Roboto", Font.SANS_SERIF);

    private Fonts() {
    }

    public static void use(Font editor, Font display) {
        if (editor != null) mono = editor.getFamily();
        if (display != null) sans = display.getFamily();
    }

    public static Font mono(float size) {
        return new Font(mono, Font.PLAIN, 1).deriveFont(size);
    }

    public static Font sans(float size, boolean bold) {
        return new Font(sans, bold ? Font.BOLD : Font.PLAIN, 1).deriveFont(size);
    }

    public static double width(Font font, String text) {
        return font.getStringBounds(text, FRC).getWidth();
    }

    public static double ascent(Font font) {
        return font.getLineMetrics("Hg", FRC).getAscent();
    }

    public static double height(Font font) {
        return font.getLineMetrics("Hg", FRC).getHeight();
    }

    private static String pick(String... families) {
        for (String f : families) {
            if (INSTALLED.contains(f)) {
                return f;
            }
        }
        return families[families.length - 1];
    }
}
