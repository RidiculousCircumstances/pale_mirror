package io.farfrontier.palemirror.frontier.v3.model;

/** Read-only current portion declared by the bakery order, independent of stack presentation. */
public final class BakeryCargo {
    private BakeryCargo() { }
    public static boolean output(ProductionJob job) {
        return switch (job.bakeryWork().orElseThrow().phase()) {
            case STATION_UNLOAD, DEPOT_DELIVERY, DELIVERED -> true;
            case DEPOT_PICKUP, STATION_LOAD, PROCESSING -> false;
        };
    }
    public static String kind(ProductionJob job) {
        return output(job) ? job.outputItemKind() : job.bakeryWork().orElseThrow().inputItemKind();
    }
    public static int quantity(ProductionJob job) { return output(job) ? job.outputCount() : job.inputCount(); }
}
