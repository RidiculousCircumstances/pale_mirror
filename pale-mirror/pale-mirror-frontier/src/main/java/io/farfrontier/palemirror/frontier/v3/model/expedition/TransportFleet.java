package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableInputView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Sole finite-asset/reservation register; never manufactures a replacement on loss. */
public record TransportFleet(Map<SubjectId, TransportAsset> assets) {
    public static final int MAX_ASSETS = 128;
    private static final ImmutableInputView<Map<SubjectId, List<TransportAsset>>> HOMES = new ImmutableInputView<>();
    public TransportFleet {
        assets = Map.copyOf(assets);
        if (assets.size() > MAX_ASSETS || assets.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().actorId())))
            throw new IllegalArgumentException("invalid finite transport fleet");
        if (assets.values().stream().map(TransportAsset::containerId).distinct().count() != assets.size())
            throw new IllegalArgumentException("transport assets must have distinct containers");
    }
    public static TransportFleet empty() { return new TransportFleet(Map.of()); }
    /** Declared membership only. Availability still belongs to exact current
     * custody, body, mission and execution admission, never this read directory. */
    public List<TransportAsset> assetsAt(SubjectId home) {
        Objects.requireNonNull(home);
        return HOMES.get(List.of(assets), () -> {
            var grouped = new LinkedHashMap<SubjectId, List<TransportAsset>>();
            assets.values().forEach(asset -> grouped.computeIfAbsent(asset.homeSettlementId(), ignored -> new java.util.ArrayList<>()).add(asset));
            grouped.replaceAll((id, values) -> List.copyOf(values));
            return Map.copyOf(grouped);
        }).getOrDefault(home, List.of());
    }
    public TransportAsset require(SubjectId actorId) {
        var asset = assets.get(actorId);
        if (asset == null) throw new IllegalArgumentException("unknown transport asset: " + actorId);
        return asset;
    }
    public TransportFleet reserve(SubjectId actorId, SubjectId missionId) { return replace(require(actorId).reserve(missionId)); }
    public TransportFleet release(SubjectId actorId, SubjectId missionId) { return replace(require(actorId).release(missionId)); }
    private TransportFleet replace(TransportAsset replacement) {
        Objects.requireNonNull(replacement);
        var next = new LinkedHashMap<>(assets); next.put(replacement.actorId(), replacement); return new TransportFleet(next);
    }
}
