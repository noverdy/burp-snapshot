package burpss.render;

import burpss.core.Settings;
import burpss.core.Style;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

public final class Theme {

    public Color card, headerBar, border, divider, text, muted, paneTitle, gutter, gutterText,
            calloutBg, shadow, backdropFrom, backdropTo, redactSolid, watermark, accent;
    private final Map<Style, Color> syntax = new EnumMap<>(Style.class);

    public static final Theme LIGHT = light();
    public static final Theme DARK = dark();

    private Theme() {
    }

    private static Theme light() {
        Theme t = new Theme();
        t.card = rgb(0xFFFFFF);
        t.headerBar = rgb(0xFBFBFB);
        t.border = rgb(0xDFDFDF);
        t.divider = rgb(0xDDDDDD);
        t.text = rgb(0x141414);
        t.muted = rgb(0x787878);
        t.paneTitle = rgb(0x000000);
        t.gutter = rgb(0xFFFFFF);
        t.gutterText = rgb(0x787878);
        t.calloutBg = rgb(0xFFFFFF);
        t.backdropFrom = rgb(0xFFB090);
        t.backdropTo = rgb(0xFF6633);
        t.redactSolid = rgb(0x2B2B2B);
        t.watermark = rgb(0x4B4B4B);
        t.accent = rgb(0xFF6633);
        t.editor(0x141414, 0x787878, 0x141414, 0x000075, 0x202020, 0x0000C0, 0xA01010, 0x202020,
                0x000075, 0x008000, 0x0000FF, 0x000075, 0xB000C0, 0x000075);
        return t;
    }

    private static Theme dark() {
        Theme t = new Theme();
        t.card = rgb(0x2B2B2B);
        t.headerBar = rgb(0x323334);
        t.border = rgb(0x525255);
        t.divider = rgb(0x555555);
        t.text = rgb(0xCECECE);
        t.muted = rgb(0xA0A0A0);
        t.paneTitle = rgb(0xFFFFFF);
        t.gutter = rgb(0x2B2B2B);
        t.gutterText = rgb(0xA0A0A0);
        t.calloutBg = rgb(0x323334);
        t.backdropFrom = rgb(0x2B2B2B);
        t.backdropTo = rgb(0xAD5B28);
        t.redactSolid = rgb(0x6A6A6C);
        t.watermark = rgb(0xC5C7C8);
        t.accent = rgb(0xFF6633);
        t.editor(0xCECECE, 0xA0A0A0, 0xD1E8F9, 0xD1E8F9, 0xBABABA, 0xBBCDFF, 0xA5C35B, 0xD1E8F9,
                0xE9C063, 0x93C763, 0x79C1F4, 0xFF9E57, 0xE9C063, 0xE9C063);
        return t;
    }

    private void editor(int text, int muted, int firstLine, int headerName, int headerValue, int paramName,
                        int paramValue, int separator, int key, int string, int number, int literal, int tag, int attr) {
        shadow = new Color(0, 0, 0, 60);
        syntax(Style.PLAIN, text);
        syntax(Style.MUTED, muted);
        syntax(Style.METHOD, firstLine);
        syntax(Style.PATH, firstLine);
        syntax(Style.STATUS, firstLine);
        syntax(Style.HEADER_NAME, headerName);
        syntax(Style.HEADER_VALUE, headerValue);
        syntax(Style.PARAM_NAME, paramName);
        syntax(Style.PARAM_VALUE, paramValue);
        syntax(Style.PUNCT, separator);
        syntax(Style.JSON_KEY, key);
        syntax(Style.STRING, string);
        syntax(Style.NUMBER, number);
        syntax(Style.KEYWORD, literal);
        syntax(Style.TAG, tag);
        syntax(Style.ATTR, attr);
    }

    public static Theme of(Settings settings) {
        return settings.theme == Settings.ThemeName.DARK ? DARK : LIGHT;
    }

    public Color color(Style style) {
        return syntax.get(style);
    }

    public static Color statusColor(int status) {
        if (status >= 500) return rgb(0xE5484D);
        if (status >= 400) return rgb(0xF76B15);
        if (status >= 300) return rgb(0x0090FF);
        if (status >= 200) return rgb(0x30A46C);
        return rgb(0x8B949E);
    }

    private void syntax(Style style, int color) {
        syntax.put(style, rgb(color));
    }

    private static Color rgb(int value) {
        return new Color(value);
    }
}
