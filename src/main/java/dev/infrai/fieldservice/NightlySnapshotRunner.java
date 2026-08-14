package dev.infrai.fieldservice;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

public final class NightlySnapshotRunner {
    public static void main(String[] args) throws Exception {
        SnapshotConfig config = SnapshotConfig.fromEnvironment();
        LocalDate date = args.length == 0 ? LocalDate.now(ZoneOffset.UTC).minusDays(1) : LocalDate.parse(args[0]);
        List<WorkOrderSnapshot> orders = List.of(
                new WorkOrderSnapshot("WO-1042", "COMPLETED", true, List.of("photos/WO-1042/arrival.jpg")),
                new WorkOrderSnapshot("WO-1043", "DISPATCHED", true, List.of()),
                new WorkOrderSnapshot("WO-1044", "COMPLETED", false, List.of("photos/WO-1044/result.jpg")));

        SnapshotPolicy.SnapshotDocument snapshot = new SnapshotPolicy().prepare(date, orders);
        InfraiStorageClient storage = new InfraiStorageClient(config);
        storage.createBucket();
        URI uploadUrl = storage.presignPut(snapshot.objectKey(), "field-service-" + date);
        storage.upload(uploadUrl, snapshot.toJson());

        System.out.printf("stored %d follow-up work order(s) at %s/%s; retention=%d days%n",
                snapshot.workOrders().size(), config.bucket(), snapshot.objectKey(), config.retentionDays());
    }
}
