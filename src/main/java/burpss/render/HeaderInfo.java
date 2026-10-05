package burpss.render;

public record HeaderInfo(String title, String method, String url, String host, int status, String reason, long timeMs) {
}
