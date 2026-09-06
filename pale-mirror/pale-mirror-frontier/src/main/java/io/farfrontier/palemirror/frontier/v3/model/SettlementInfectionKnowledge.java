package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded facts about local infection that a settlement has actually observed. */
public final class SettlementInfectionKnowledge {
    static final int MAX_CELLS_PER_SETTLEMENT = 64;
    private final Map<SubjectId, Map<InfectionCell, KnownInfection>> bySettlement;

    public SettlementInfectionKnowledge(Map<SubjectId, Map<InfectionCell, KnownInfection>> bySettlement) {
        Objects.requireNonNull(bySettlement, "settlement knowledge");
        Map<SubjectId, Map<InfectionCell, KnownInfection>> copy = new LinkedHashMap<>();
        for (Map.Entry<SubjectId, Map<InfectionCell, KnownInfection>> entry : bySettlement.entrySet()) {
            SubjectId settlement = Objects.requireNonNull(entry.getKey(), "knowledge settlement");
            Map<InfectionCell, KnownInfection> cells = new LinkedHashMap<>();
            for (Map.Entry<InfectionCell, KnownInfection> cell : entry.getValue().entrySet()) {
                if (!cell.getKey().equals(cell.getValue().cell())) throw new IllegalArgumentException("known infection key must match cell");
                cells.put(cell.getKey(), cell.getValue());
            }
            if (cells.size() > MAX_CELLS_PER_SETTLEMENT) throw new IllegalArgumentException("settlement infection knowledge retention limit exceeded");
            if (!cells.isEmpty()) copy.put(settlement, Map.copyOf(cells));
        }
        this.bySettlement = Map.copyOf(copy);
    }

    public static SettlementInfectionKnowledge empty() { return new SettlementInfectionKnowledge(Map.of()); }

    public Map<SubjectId, Map<InfectionCell, KnownInfection>> entries() { return bySettlement; }
    public Map<InfectionCell, KnownInfection> known(SubjectId settlementId) { return bySettlement.getOrDefault(settlementId, Map.of()); }

    public SettlementInfectionKnowledge observe(SubjectId settlementId, InfectionCell cell, FixedRatio intensity, long observedAt) {
        Objects.requireNonNull(settlementId, "knowledge settlement"); Objects.requireNonNull(cell, "knowledge cell"); Objects.requireNonNull(intensity, "knowledge intensity");
        if (observedAt < 0L) throw new IllegalArgumentException("knowledge observation tick must be non-negative");
        Map<SubjectId, Map<InfectionCell, KnownInfection>> next = new LinkedHashMap<>(bySettlement);
        Map<InfectionCell, KnownInfection> cells = new LinkedHashMap<>(known(settlementId));
        if (intensity.value().equals(FixedScalar.ZERO)) cells.remove(cell);
        else cells.put(cell, new KnownInfection(cell, intensity, observedAt));
        if (cells.isEmpty()) next.remove(settlementId); else next.put(settlementId, Map.copyOf(cells));
        return new SettlementInfectionKnowledge(next);
    }

    void validate(FrontierBootstrap bootstrap) {
        bySettlement.forEach((settlement, cells) -> {
            Settlement owner = FrontierWorldStateSupport.settlement(bootstrap, settlement);
            for (KnownInfection known : cells.values()) {
                if (!locallyObservable(owner, known.cell())) {
                    throw new IllegalArgumentException("settlement retained non-local infection knowledge");
                }
            }
        });
    }

    @Override public boolean equals(Object other) { return other instanceof SettlementInfectionKnowledge value && bySettlement.equals(value.bySettlement); }
    @Override public int hashCode() { return bySettlement.hashCode(); }

    private static boolean locallyObservable(Settlement settlement, InfectionCell cell) {
        return settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY)
                .anyMatch(facility -> squaredDistance(facility.anchor(), cell.originAtY(facility.anchor().y())) <= 25_600L);
    }

    private static long squaredDistance(BlockPosition left, BlockPosition right) {
        long x = (long) left.x() - right.x(), z = (long) left.z() - right.z(); return x * x + z * z;
    }

    public record KnownInfection(InfectionCell cell, FixedRatio intensity, long observedAt) {
        public KnownInfection {
            Objects.requireNonNull(cell, "known infection cell"); Objects.requireNonNull(intensity, "known infection intensity");
            if (intensity.value().equals(FixedScalar.ZERO) || observedAt < 0L) throw new IllegalArgumentException("known infection must be nonzero and dated");
        }
    }
}
