package io.farfrontier.palemirror.frontier.v3.model;

/** Read-only current portion declared by the bakery order, independent of stack presentation. */
public final class StationWorkCargo {
    private StationWorkCargo() { }
    public static boolean output(ProductionJob job) {
        return switch (job.stationWork().orElseThrow().phase()) {
            case STATION_UNLOAD, DEPOT_DELIVERY, DELIVERED -> true;
            case TARGET_PICKUP, TARGET_LOAD, DEPOT_PICKUP, STATION_LOAD, PROCESSING -> false;
        };
    }
    public static boolean exact(FrontierWorldState state, ProductionJob job) {
        return job.stationWork().orElseThrow().phase().maintenanceTransfer() || (output(job) ? StationWorkProduct.exact(state, job)
                : job.inputHold() instanceof ProductionInputHold.Materialized);
    }
    public static io.farfrontier.palemirror.frontier.v3.api.SubjectId exactId(ProductionJob job) {
        return job.stationWork().orElseThrow().phase().maintenanceTransfer()
                ? job.stationWork().orElseThrow().maintenanceTarget().orElseThrow().itemId()
                : output(job) ? job.outputItemId() : job.consumedItemId();
    }
    public static String kind(ProductionJob job) {
        if (job.stationWork().orElseThrow().phase().maintenanceTransfer()) return job.stationWork().orElseThrow().maintenanceTarget().orElseThrow().itemKind();
        return output(job) ? job.outputItemKind() : job.stationWork().orElseThrow().inputItemKind();
    }
    public static int quantity(ProductionJob job) { if (job.stationWork().orElseThrow().phase().maintenanceTransfer()) return 1; return output(job) ? job.outputCount() : job.inputCount(); }
}
