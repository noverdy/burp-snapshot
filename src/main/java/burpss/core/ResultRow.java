package burpss.core;

public record ResultRow(int number, String payload, int status, int length, long timeMs, String response) {
}
