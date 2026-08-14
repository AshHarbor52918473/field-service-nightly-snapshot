package dev.infrai.fieldservice;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class SnapshotPolicy {
    public SnapshotDocument prepare(LocalDate businessDate, List<WorkOrderSnapshot> orders) {
        List<WorkOrderSnapshot> selected = orders.stream()
                .filter(order -> order.dispatchStatus().equals("COMPLETED"))
                .filter(WorkOrderSnapshot::technicianFollowUpRequired)
                .sorted(Comparator.comparing(WorkOrderSnapshot::workOrderId))
                .toList();
        return new SnapshotDocument(businessDate, selected);
    }

    public record SnapshotDocument(LocalDate businessDate, List<WorkOrderSnapshot> workOrders) {
        public String objectKey() {
            return "nightly/" + businessDate + "/follow-up-work-orders.json";
        }

        public String toJson() {
            String orders = workOrders.stream().map(SnapshotDocument::orderJson).reduce((a, b) -> a + "," + b).orElse("");
            return "{\"business_date\":\"" + businessDate + "\",\"work_orders\":[" + orders + "]}";
        }

        private static String orderJson(WorkOrderSnapshot order) {
            String photos = order.photoObjectKeys().stream()
                    .map(SnapshotDocument::quote).reduce((a, b) -> a + "," + b).orElse("");
            return "{\"work_order_id\":" + quote(order.workOrderId())
                    + ",\"dispatch_status\":" + quote(order.dispatchStatus())
                    + ",\"technician_follow_up_required\":" + order.technicianFollowUpRequired()
                    + ",\"photo_object_keys\":[" + photos + "]}";
        }

        private static String quote(String value) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
