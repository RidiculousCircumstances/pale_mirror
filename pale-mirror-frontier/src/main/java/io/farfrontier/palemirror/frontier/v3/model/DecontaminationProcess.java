package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Plans one exact reagent-backed, physically observable infection-cell treatment at a time. */
final class DecontaminationProcess {
    private static final SubjectId SYSTEM = new SubjectId("system:decontamination");
    private static final long RESPONSE_RADIUS_SQUARED = 25_600L;
    private DecontaminationProcess() { }

    static ScheduledAction scan(int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:decontamination-" + ordinal), new SimInstant(dueAt), 0,
                SYSTEM, "frontier.decontamination.scan", 1);
    }

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(scan(ordinal + 1, action.dueAt().ticks() + 200L)));
        if (state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.DECONTAMINATION
                && (intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING))) return List.of(next);
        Optional<Candidate> candidate = candidate(state);
        if (candidate.isEmpty()) return List.of(next);
        Candidate value = candidate.orElseThrow(); InfectionCell cell = value.cell();
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:decontamination-" + ordinal), PhysicalIntentKind.DECONTAMINATION,
                PhysicalIntentStatus.PREPARED, value.facility().id(), List.of(value.facility().id(), value.material().id()),
                new FixedPosition(FixedScalar.whole(cell.originAtY(0).x()), FixedScalar.whole(0), FixedScalar.whole(cell.originAtY(0).z())), 0,
                PhysicalPostcondition.DECONTAMINATION_OBSERVED);
        return List.of(new ProposedEvent(value.settlement().id(), new PhysicalIntentPrepared(intent)), next);
    }

    static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.DECONTAMINATION) throw new IllegalArgumentException("decontamination intent kind is invalid");
        Settlement settlement = owner(state, intent.causeSubjectId());
        if (!subject.equals(settlement.id())) throw new IllegalArgumentException("decontamination intent has a foreign settlement owner");
        DecontaminationStateSupport.validateIntent(state, intent);
        return state.preparePhysicalIntent(intent);
    }

    private static Optional<Candidate> candidate(FrontierWorldState state) {
        return state.bootstrap().settlements().stream().sorted(Comparator.comparing(Settlement::id)).flatMap(settlement -> {
            Optional<SettlementStructure> facility = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY)
                    .min(Comparator.comparing(SettlementStructure::id));
            Optional<ExactItemStack> material = state.inventory().items().values().stream().filter(item -> item.itemKind().equals(DecontaminationPolicy.REAGENT))
                    .filter(item -> ownedActiveMaterial(state, settlement, item)).sorted(Comparator.comparing(ExactItemStack::id)).findFirst();
            return facility.isPresent() && material.isPresent() ? state.infection().keySet().stream().sorted(Comparator.comparingInt(InfectionCell::x)
                    .thenComparingInt(InfectionCell::z)).filter(cell -> nearby(facility.orElseThrow(), cell)).map(cell -> new Candidate(settlement, facility.orElseThrow(), material.orElseThrow(), cell)) : java.util.stream.Stream.empty();
        }).findFirst();
    }

    private static boolean ownedActiveMaterial(FrontierWorldState state, Settlement settlement, ExactItemStack item) {
        if (!(item.custody() instanceof InventoryCustody.ContainerSlot slot)) return false;
        ContainerRecord container = state.inventory().containers().get(slot.containerId());
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        return container != null && container.ownerId().equals(settlement.id()) && surface != null && surface.status() == ContainerSurfaceStatus.ACTIVE;
    }

    private static boolean nearby(SettlementStructure facility, InfectionCell cell) {
        BlockPosition position = cell.originAtY(facility.anchor().y()); long dx = position.x() - facility.anchor().x(), dz = position.z() - facility.anchor().z();
        return dx * dx + dz * dz <= RESPONSE_RADIUS_SQUARED;
    }

    static Settlement owner(FrontierWorldState state, SubjectId facilityId) {
        return owner(state.bootstrap(), facilityId);
    }

    static Settlement owner(FrontierBootstrap bootstrap, SubjectId facilityId) {
        for (Settlement settlement : bootstrap.settlements()) for (SettlementStructure structure : settlement.structures()) {
            if (structure.id().equals(facilityId) && structure.kind() == StructureKind.INFIRMARY) return settlement;
        }
        throw new IllegalArgumentException("decontamination facility is not a settlement infirmary: " + facilityId.value());
    }

    record Candidate(Settlement settlement, SettlementStructure facility, ExactItemStack material, InfectionCell cell) { }
}
