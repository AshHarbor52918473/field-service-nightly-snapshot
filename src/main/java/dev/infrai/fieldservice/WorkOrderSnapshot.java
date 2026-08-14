package dev.infrai.fieldservice;

import java.util.List;

public record WorkOrderSnapshot(
        String workOrderId,
        String dispatchStatus,
        boolean technicianFollowUpRequired,
        List<String> photoObjectKeys) {
}
