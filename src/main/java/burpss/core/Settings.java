package burpss.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class Settings {

    public enum ThemeName { LIGHT, DARK }
    public enum Layout { SIDE_BY_SIDE, STACKED }
    public enum Frame { REPORT, SHOWCASE }
    public enum Notes { CALLOUT, LEGEND }
    public enum RedactStyle { BLUR, SOLID, DOTS }
    public enum WatermarkPlacement { TILED, BOTTOM_RIGHT, BOTTOM_LEFT, CENTER }

    public interface Store {
        String get(String key);
        void set(String key, String value);
    }

    public ThemeName theme = ThemeName.LIGHT;
    public Layout layout = Layout.SIDE_BY_SIDE;
    public Frame frame = Frame.REPORT;
    public int fontSize = 13;
    public boolean lineNumbers = true;

    public boolean showHeaderBar = true;
    public boolean showTime = false;

    public Notes notes = Notes.CALLOUT;
    public boolean numberMarks = true;

    public boolean autoRedact = true;
    public RedactStyle redactStyle = RedactStyle.BLUR;
    public int revealChars = 4;
    public String redactHeaders = "Authorization, Proxy-Authorization, Cookie, Set-Cookie, X-Api-Key, X-Auth-Token, X-CSRF-Token, X-XSRF-Token";
    public String redactParams = "password, passwd, secret, token, api_key, apikey, session";

    public boolean prettyJson = true;
    public String hideHeaders = "Sec-Ch-Ua*, Sec-Fetch-*, Accept-Language, Accept-Encoding, Priority, Upgrade-Insecure-Requests";
    public int maxBodyLines = 40;
    public int wrapColumns = 100;

    public boolean watermark = true;
    public String watermarkText = "CONFIDENTIAL";
    public String watermarkLogo = "";
    public WatermarkPlacement watermarkPlacement = WatermarkPlacement.TILED;
    public int watermarkOpacity = 12;
    public int watermarkSize = 20;
    public int watermarkSpacing = 100;
    public int watermarkAngle = -25;

    public int exportScale = 2;
    public String zoom = "Fit";
    public int markColor = 0;
    public String openSections = "";
    public String lastSaveDir = "";

    public String containsText = "";

    private static final String PREFIX = "burpss.";

    public void load(Store store) {
        for (Field f : persistentFields()) {
            String raw = store.get(PREFIX + f.getName());
            if (raw == null) {
                continue;
            }
            try {
                Class<?> type = f.getType();
                if (type == String.class) {
                    f.set(this, raw);
                } else if (type == int.class) {
                    f.setInt(this, Integer.parseInt(raw));
                } else if (type == boolean.class) {
                    f.setBoolean(this, Boolean.parseBoolean(raw));
                } else if (type.isEnum()) {
                    f.set(this, enumValue(type, raw));
                }
            } catch (IllegalArgumentException | IllegalAccessException ignored) {
            }
        }
    }

    public void save(Store store) {
        for (Field f : persistentFields()) {
            try {
                store.set(PREFIX + f.getName(), String.valueOf(f.get(this)));
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String raw) {
        return Enum.valueOf((Class<? extends Enum>) type, raw);
    }

    private static Field[] persistentFields() {
        return java.util.Arrays.stream(Settings.class.getFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .toArray(Field[]::new);
    }
}
