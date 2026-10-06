package burpss;

import burpss.core.Anchor;
import burpss.core.Exchange;
import burpss.core.HttpText;
import burpss.core.Mark;
import burpss.core.Settings;
import burpss.core.Token;
import burpss.render.ExchangeContent;
import burpss.render.HeaderInfo;
import burpss.render.Scene;
import burpss.render.Theme;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public final class Samples {

    public static final String REQUEST = """
            POST /api/v2/users/1337/profile?debug=true&lang=en HTTP/2\r
            Host: app.example.com\r
            Cookie: session=8f14e45fceea167a5a36dedd4bea2543; theme=dark; _ga=GA1.2.1234\r
            Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMzM3Iiwicm9sZSI6InVzZXIifQ.c2lnbmF0dXJl\r
            Sec-Ch-Ua: "Chromium";v="130", "Not?A_Brand";v="99"\r
            Sec-Ch-Ua-Mobile: ?0\r
            Sec-Fetch-Site: same-origin\r
            Accept-Language: en-US,en;q=0.9\r
            Content-Type: application/json\r
            Content-Length: 86\r
            \r
            {"displayName":"attacker","role":"admin","password":"Winter2026!","tags":["a","b"]}""";

    public static final String RESPONSE = """
            HTTP/2 200 OK\r
            Content-Type: application/json; charset=utf-8\r
            Set-Cookie: session=1d7f0a3c9b2e44a8b6f05e1c2d3a4b5c; Path=/; HttpOnly; Secure\r
            X-Request-Id: 4b7c2e1a-9f0d-4c3b-a2e1-77f0c3d9e8a1\r
            \r
            {"id":1337,"displayName":"attacker","email":"victim@example.com","role":"admin","apiToken":"sk_live_51Hx9QeLkd8sP0aZ2vQ7","verified":true,"address":null}""";

    public static void main(String[] args) throws IOException {
        File out = new File(args[0]);
        out.mkdirs();
        Settings s = new Settings();
        render(s, new File(out, "light-callout.png"));
        s.theme = Settings.ThemeName.DARK;
        s.frame = Settings.Frame.SHOWCASE;
        s.showTime = true;
        render(s, new File(out, "dark-showcase.png"));
        s.theme = Settings.ThemeName.LIGHT;
        s.frame = Settings.Frame.REPORT;
        s.notes = Settings.Notes.LEGEND;
        s.layout = Settings.Layout.STACKED;
        s.redactStyle = Settings.RedactStyle.SOLID;
        s.watermarkPlacement = Settings.WatermarkPlacement.BOTTOM_RIGHT;
        render(s, new File(out, "light-legend-stacked.png"));
        renderTable(new File(out, "results-table.png"));
    }

    static void renderTable(File file) throws IOException {
        String[] users = {"admin", "root", "test", "guest", "administrator"};
        String[] passwords = {"admin", "toor", "test123", "guest", "P@ssw0rd!"};
        java.util.List<String> requests = new java.util.ArrayList<>();
        for (int i = 0; i < users.length; i++) {
            String body = "{\"username\":\"" + users[i] + "\",\"password\":\"" + passwords[i] + "\"}";
            requests.add("POST /login HTTP/1.1\r\nHost: app.example.com\r\nContent-Length: " + body.length() + "\r\n\r\n" + body);
        }
        java.util.List<String> payloads = burpss.core.PayloadDiff.labels(requests);
        java.util.List<burpss.core.ResultRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < users.length; i++) {
            boolean hit = i == users.length - 1;
            rows.add(new burpss.core.ResultRow(i + 1, payloads.get(i), hit ? 302 : 401, hit ? 1288 : 512, 80 + i * 13,
                    hit ? "Location: /dashboard" : "Invalid credentials"));
        }
        Settings settings = new Settings();
        settings.theme = Settings.ThemeName.DARK;
        settings.containsText = "dashboard";
        burpss.core.EditState state = new burpss.core.EditState();
        state.title = "Credential stuffing: valid admin password found";
        Mark mark = new Mark(new Anchor.Rows(5, 5), 3);
        mark.note = "Only this credential pair redirects to the dashboard";
        state.marks.add(mark);
        burpss.render.TableContent content = new burpss.render.TableContent(rows, state, settings, Theme.of(settings));
        HeaderInfo header = new HeaderInfo(state.title, state.caption, "POST", "https://app.example.com/login", "app.example.com", 0, "", -1);
        ImageIO.write(new Scene(content, header, state.marks, settings, null).toImage(2), "png", file);
    }

    static void render(Settings settings, File file) throws IOException {
        HttpText req = HttpText.parse(REQUEST, true);
        HttpText res = HttpText.parse(RESPONSE, false);
        Exchange ex = new Exchange(req, res, "POST", "https://app.example.com/api/v2/users/1337/profile?debug=true&lang=en",
                "app.example.com", 200, "OK", 142);
        ex.state.title = "IDOR + mass assignment: role escalated to admin";
        mark(ex, 0, req, "role", "Client-controlled role accepted by the API", 0);
        mark(ex, 1, res, "role", "Server confirms the escalated role", 0);
        mark(ex, 1, res, "email", "Victim's PII returned for another user's ID", 1);
        Mark path = new Mark(tokenAnchor(0, req, Token.Kind.PATH, "path"), 4);
        ex.state.marks.add(path);
        ExchangeContent content = new ExchangeContent(ex, settings, Theme.of(settings));
        HeaderInfo header = new HeaderInfo(ex.state.title, ex.state.caption, ex.method, ex.url, ex.host, ex.status, ex.reason, ex.timeMs);
        Scene scene = new Scene(content, header, ex.state.marks, settings, null);
        ImageIO.write(scene.toImage(2), "png", file);
    }

    static void mark(Exchange ex, int pane, HttpText text, String name, String note, int color) {
        Mark m = new Mark(tokenAnchor(pane, text, Token.Kind.JSON_MEMBER, name), color);
        m.note = note;
        ex.state.marks.add(m);
    }

    public static Anchor tokenAnchor(int pane, HttpText text, Token.Kind kind, String name) {
        Token t = text.tokens().stream().filter(k -> k.kind() == kind && k.name().equals(name)).findFirst().orElseThrow();
        return new Anchor.Text(pane, t.start(), t.end());
    }
}
