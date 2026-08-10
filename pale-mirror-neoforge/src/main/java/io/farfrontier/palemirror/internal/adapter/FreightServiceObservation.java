package io.farfrontier.palemirror.internal.adapter;

public record FreightServiceObservation(String serviceId, FreightServiceStatus status, String nativeReference,
                                        String currentStation, String scheduleFingerprint, String diagnostic) {
    public static FreightServiceObservation unavailable(String id, String diagnostic) {
        return new FreightServiceObservation(id, FreightServiceStatus.UNAVAILABLE, "", "", "", diagnostic);
    }
}
