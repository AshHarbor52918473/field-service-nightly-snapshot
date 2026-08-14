package dev.infrai.fieldservice;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiStorageClient {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern URL = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern MESSAGE = Pattern.compile("\\\"(?:message|hint)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final SnapshotConfig config;

    public InfraiStorageClient(SnapshotConfig config) {
        this.config = config;
    }

    public void createBucket() throws IOException, InterruptedException {
        call("POST", "/v1/storage/bucket/create", "{\"name\":\"" + json(config.bucket()) + "\"}");
    }

    public URI presignPut(String key, String idempotencyKey) throws IOException, InterruptedException {
        String path = "/v1/storage/object/presign/" + segment(config.bucket()) + "/" + pathKey(key);
        String body = "{\"op\":\"put\",\"expires_seconds\":900,\"content_type\":\"application/json\","
                + "\"idempotency_key\":\"" + json(idempotencyKey) + "\"}";
        String envelope = call("POST", path, body);
        Matcher url = URL.matcher(envelope);
        if (!url.find()) throw new IOException("Successful response did not contain a signed URL");
        return URI.create(url.group(1).replace("\\/", "/"));
    }

    public void upload(URI signedUrl, String json) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(signedUrl)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .method("PUT", HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) throw new IOException("Signed upload rejected with HTTP " + response.statusCode());
    }

    private String call(String method, String path, String body) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.apiBase().resolve(path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            Matcher ok = OK.matcher(envelope);
            if (!ok.find()) throw new IOException("Response was not an Infrai envelope");
            if (!Boolean.parseBoolean(ok.group(1))) {
                if (response.statusCode() == 429 && attempt < 3) {
                    Thread.sleep(retryDelayMillis(response, attempt));
                    continue;
                }
                throw envelopeError(envelope, response.statusCode());
            }
            if (response.statusCode() >= 500) throw new IOException("Transport error: HTTP " + response.statusCode());
            return envelope;
        }
        throw new IOException("Retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> { try { return Long.parseLong(value) * 1000L; } catch (NumberFormatException e) { return 500L << attempt; } })
                .orElse(500L << attempt);
    }

    private static IOException envelopeError(String envelope, int status) {
        Matcher code = CODE.matcher(envelope);
        Matcher message = MESSAGE.matcher(envelope);
        return new IOException((code.find() ? code.group(1) : "INFRAI_ERROR") + ": "
                + (message.find() ? message.group(1) : "request rejected") + " (HTTP " + status + ")");
    }

    private static String segment(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String pathKey(String value) { return java.util.Arrays.stream(value.split("/", -1)).map(InfraiStorageClient::segment).reduce((a, b) -> a + "/" + b).orElse(""); }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
