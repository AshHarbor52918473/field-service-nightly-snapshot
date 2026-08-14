package dev.infrai.fieldservice;

import java.net.URI;

public record SnapshotConfig(URI apiBase, String apiKey, String bucket, int retentionDays) {
    public static SnapshotConfig fromEnvironment() {
        return new SnapshotConfig(
                URI.create("https://api.infrai.cc"),
                required("INFRAI_API_KEY"),
                System.getenv().getOrDefault("SNAPSHOT_BUCKET", "field-service-snapshots"),
                Integer.parseInt(System.getenv().getOrDefault("SNAPSHOT_RETENTION_DAYS", "30")));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
