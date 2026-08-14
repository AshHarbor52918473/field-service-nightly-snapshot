package dev.infrai.fieldservice;

import java.time.LocalDate;
import java.util.List;

public final class SnapshotPolicyTest {
    public static void main(String[] args) {
        var input = List.of(
                new WorkOrderSnapshot("WO-9", "COMPLETED", false, List.of("photos/WO-9/done.jpg")),
                new WorkOrderSnapshot("WO-2", "COMPLETED", true, List.of("photos/WO-2/arrival.jpg")),
                new WorkOrderSnapshot("WO-1", "DISPATCHED", true, List.of()));
        var result = new SnapshotPolicy().prepare(LocalDate.parse("2026-08-13"), input);

        require(result.workOrders().size() == 1, "one completed order needs follow-up");
        require(result.workOrders().get(0).workOrderId().equals("WO-2"), "WO-2 must be selected");
        require(result.objectKey().equals("nightly/2026-08-13/follow-up-work-orders.json"), "key is date-scoped");
        System.out.println("PASS: selected WO-2 for nightly/2026-08-13/follow-up-work-orders.json");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
