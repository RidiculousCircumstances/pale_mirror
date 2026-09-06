package io.farfrontier.palemirror.internal.adapter;

public record RailConnectionObservation(String connectionId, RailConnectionStatus status, net.minecraft.core.BlockPos currentPosition,
                                        String planHash,
                                        int plannedLength, int remainingDistance, String nativeReference,
                                        RailConstructionPolicy constructionPolicy,
                                        int completedSegments, int totalSegments,
                                        String diagnostic) {
    public static RailConnectionObservation unavailable(String id, String diagnostic) {
        return new RailConnectionObservation(id, RailConnectionStatus.UNAVAILABLE, null, "", 0, 0, "",
                RailConstructionPolicy.LOADED_CHUNKS_ONLY, 0, 0, diagnostic);
    }
}
