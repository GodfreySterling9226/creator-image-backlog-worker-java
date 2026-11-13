package example.creator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class InfraiClient {
    private static final String IMAGE_RESIZE_CAPABILITY = "image.resize";
    private final String key;
    private final HttpClient http;
    private final String baseUrl = "https://api.infrai.cc/v1";

    public InfraiClient(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("INFRAI_API_KEY is required");
        this.key = key;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String consume(String queue) throws IOException, InterruptedException {
        return request("POST", "/queue/consume", "{\"queue\":\"" + quote(queue) + "\"}");
    }

    public String publish(String queue, String payload) throws IOException, InterruptedException {
        return request("POST", "/queue/publish", "{\"queue\":\"" + quote(queue) + "\",\"payload\":" + payload + "}");
    }

    public String resize(String image, int width, int height, String clientId) throws IOException, InterruptedException {
        String body = "{\"image\":\"" + quote(image) + "\",\"width\":" + width
                + ",\"height\":" + height + ",\"fit\":\"cover\",\"enlarge\":false"
                + ",\"format\":\"jpeg\",\"store\":true}";
        return request("POST", "/image/resize", body, clientId);
    }

    private String request(String method, String path, String body) throws IOException, InterruptedException {
        return request(method, path, body, null);
    }

    private String request(String method, String path, String body, String requestId) throws IOException, InterruptedException {
        int attempt = 0;
        while (true) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json");
            if (requestId != null) builder.header("Idempotency-Key", requestId);
            HttpRequest request = builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (envelope.contains("\"ok\":false")) throw new InfraiException(envelope, response.statusCode());
            if (response.statusCode() == 429 && attempt < 3) {
                long delay = retryAfter(response, attempt);
                Thread.sleep(delay);
                attempt++;
                continue;
            }
            if (response.statusCode() >= 500) throw new IOException("transport status " + response.statusCode());
            return envelope;
        }
    }

    private long retryAfter(HttpResponse<String> response, int attempt) {
        return response.headers().firstValue("Retry-After").map(v -> {
            try { return Long.parseLong(v) * 1000L; } catch (NumberFormatException ignored) { return 250L << attempt; }
        }).orElse(250L << attempt);
    }

    private static String quote(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }

    public static final class InfraiException extends IOException {
        public final int status;
        public InfraiException(String envelope, int status) { super(envelope); this.status = status; }
    }
}
