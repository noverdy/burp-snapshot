package burpss.core;

public final class Exchange {

    public final HttpText request;
    public final HttpText response;
    public final String method;
    public final String url;
    public final String host;
    public final int status;
    public final String reason;
    public final long timeMs;
    public final EditState state = new EditState();
    public final History history = new History(state);

    public Exchange(HttpText request, HttpText response, String method, String url, String host,
                    int status, String reason, long timeMs) {
        this.request = request;
        this.response = response;
        this.method = method;
        this.url = url;
        this.host = host;
        this.status = status;
        this.reason = reason;
        this.timeMs = timeMs;
    }

    public HttpText pane(int index) {
        return index == 0 ? request : response;
    }

    public String label() {
        String s = method + " " + url;
        return status > 0 ? s + "  →  " + status : s;
    }
}
