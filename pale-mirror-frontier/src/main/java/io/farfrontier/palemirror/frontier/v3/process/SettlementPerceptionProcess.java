package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The sole bounded bridge from local world conditions into settlement strategic knowledge. */
public final class SettlementPerceptionProcess {
    private static final Comparator<InfectionCell> CELL_ORDER = Comparator.comparingInt(InfectionCell::x).thenComparingInt(InfectionCell::z);
    private SettlementPerceptionProcess() { }

    public static Refresh refreshLocalInfection(FrontierWorldState state, Settlement settlement, long now) {
        Map<InfectionCell, FixedRatio> actual = new LinkedHashMap<>();
        state.infection().entrySet().stream().filter(entry -> locallyObservable(state.bootstrap().ruleset(), settlement, entry.getKey()))
                .sorted(Map.Entry.comparingByKey(CELL_ORDER)).forEach(entry -> actual.put(entry.getKey(), entry.getValue()));
        Map<InfectionCell, SettlementInfectionKnowledge.KnownInfection> known = state.strategicPlans().infectionKnowledge().known(settlement.id());
        List<InfectionCell> cells = new ArrayList<>(known.keySet());
        actual.keySet().forEach(cell -> { if (!known.containsKey(cell)) cells.add(cell); });
        cells.sort(CELL_ORDER);
        SettlementInfectionKnowledge next = state.strategicPlans().infectionKnowledge(); List<ProposedEvent> events = new ArrayList<>();
        for (InfectionCell cell : cells) {
            FixedRatio intensity = actual.getOrDefault(cell, new FixedRatio(FixedScalar.ZERO));
            SettlementInfectionKnowledge.KnownInfection prior = known.get(cell);
            if (prior != null && prior.intensity().equals(intensity)) continue;
            next = next.observe(settlement.id(), cell, intensity, now);
            events.add(new ProposedEvent(settlement.id(), new SettlementInfectionObserved(settlement.id(), cell, intensity, now)));
        }
        return new Refresh(next, List.copyOf(events));
    }

    private static boolean locallyObservable(FrontierRuleset ruleset, Settlement settlement, InfectionCell cell) {
        int radius = ruleset.spatial().settlementInfectionRadius();
        return settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY)
                .anyMatch(facility -> squaredDistance(facility.anchor(), cell.originAtY(facility.anchor().y())) <= (long) radius * radius);
    }

    public static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject, SettlementInfectionObserved observed) {
        if (!subject.equals(observed.settlementId())) throw new IllegalArgumentException("settlement infection observation has a foreign owner");
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), observed.settlementId());
        if (!locallyObservable(state.bootstrap().ruleset(), settlement, observed.cell())) throw new IllegalArgumentException("settlement observed infection outside local perception");
        return state.withStrategicPlans(state.strategicPlans().withInfectionKnowledge(
                state.strategicPlans().infectionKnowledge().observe(observed.settlementId(), observed.cell(), observed.intensity(), observed.observedAt())));
    }

    private static long squaredDistance(BlockPosition left, BlockPosition right) {
        long x = (long) left.x() - right.x(), z = (long) left.z() - right.z(); return x * x + z * z;
    }

    public record Refresh(SettlementInfectionKnowledge knowledge, List<ProposedEvent> events) { }
}
