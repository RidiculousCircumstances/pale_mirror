package io.farfrontier.palemirror.internal.adapter;

/** A typed physical fact. Unobserved means PM did not load chunks merely to inspect this route. */
public record LogisticsRouteObservation(boolean observed, boolean originTrainPresent, int originCapacity,
                                        String originVehicleId, boolean destinationTrainPresent, int destinationCapacity,
                                        String destinationVehicleId, String diagnostic) {
    public LogisticsRouteObservation {
        if (originCapacity < 0 || destinationCapacity < 0) throw new IllegalArgumentException("Route capacity must not be negative");
        originVehicleId = originVehicleId == null ? "" : originVehicleId;
        destinationVehicleId = destinationVehicleId == null ? "" : destinationVehicleId;
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public static LogisticsRouteObservation unobserved(String diagnostic) {
        return new LogisticsRouteObservation(false, false, 0, "", false, 0, "", diagnostic);
    }

    public static LogisticsRouteObservation invalid(String diagnostic) {
        return new LogisticsRouteObservation(true, false, 0, "", false, 0, "", diagnostic);
    }
}
