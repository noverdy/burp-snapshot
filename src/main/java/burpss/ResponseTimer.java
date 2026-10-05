package burpss;

import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ResponseTimer implements HttpHandler {

    private static final int CAPACITY = 5000;

    private final Map<Integer, Long> started = new ConcurrentHashMap<>();
    private final Map<Long, Long> elapsedMs = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
            return size() > CAPACITY;
        }
    });

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent request) {
        if (started.size() > CAPACITY) started.clear();
        started.put(request.messageId(), System.nanoTime());
        return RequestToBeSentAction.continueWith(request, request.annotations());
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived response) {
        Long start = started.remove(response.messageId());
        if (start != null) elapsedMs.put(fingerprint(response), (System.nanoTime() - start) / 1_000_000);
        return ResponseReceivedAction.continueWith(response, response.annotations());
    }

    long elapsedMs(HttpResponse response) {
        return response == null ? -1 : elapsedMs.getOrDefault(fingerprint(response), -1L);
    }

    private static long fingerprint(HttpResponse response) {
        byte[] bytes = response.toByteArray().getBytes();
        return ((long) bytes.length << 32) ^ Arrays.hashCode(bytes);
    }
}
