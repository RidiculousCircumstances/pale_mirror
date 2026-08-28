package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure validation and state reduction for one exact physical decontamination result. */
final class DecontaminationStateSupport {
    private DecontaminationStateSupport() { }

    static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        Settlement settlement = DecontaminationProcess.owner(state, intent.causeSubjectId());
        if (intent.subjectIds().size() != 2 || !intent.subjectIds().contains(intent.causeSubjectId())) throw new IllegalArgumentException("decontamination intent subjects are invalid");
        SubjectId itemId = intent.subjectIds().stream().filter(id -> !id.equals(intent.causeSubjectId())).findFirst().orElseThrow();
        ExactItemStack material = state.inventory().items().get(itemId);
        if (material == null || !material.itemKind().equals(DecontaminationPolicy.REAGENT) || !(material.custody() instanceof InventoryCustody.ContainerSlot slot)) {
            throw new IllegalArgumentException("decontamination intent lacks its exact reagent");
        }
        ContainerRecord container = state.inventory().containers().get(slot.containerId()); ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        if (container == null || !container.ownerId().equals(settlement.id()) || surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) {
            throw new IllegalArgumentException("decontamination reagent is not in an active local container");
        }
        if (!state.infection().containsKey(cell(intent))) throw new IllegalArgumentException("decontamination target is no longer infected");
    }

    static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, DecontaminationObservation observation,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        validateIntent(state, intent);
        InfectionCell cell = cell(intent); FixedRatio current = state.infection().get(cell);
        if (!intent.subjectIds().contains(observation.itemId()) || !observation.cell().equals(cell) || observation.priorRaw() != current.value().raw()) {
            throw new IllegalArgumentException("decontamination receipt does not match its exact target");
        }
        long remaining = Math.max(0L, Math.subtractExact(observation.priorRaw(), DecontaminationPolicy.REDUCTION_RAW));
        if (observation.remainingRaw() != remaining) throw new IllegalArgumentException("decontamination receipt has an invalid intensity reduction");
        intents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observation.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations()); observations.put(observation.id(), observation);
        Map<InfectionCell, FixedRatio> infection = new LinkedHashMap<>(state.infection());
        if (remaining == 0L) infection.remove(cell); else infection.put(cell, new FixedRatio(new FixedScalar(remaining)));
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), infection, state.inventory().consumeOne(observation.itemId()),
                state.productionJobs(), state.contracts(), state.operations(), intents, observations, state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(), state.strategicPlans());
    }

    static void validateReceipt(FrontierBootstrap bootstrap, Map<InfectionCell, FixedRatio> infection, PhysicalIntent intent,
                                DecontaminationObservation observation) {
        if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.DECONTAMINATION
                || intent.postcondition() != io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition.DECONTAMINATION_OBSERVED) {
            throw new IllegalArgumentException("decontamination observation has a foreign physical intent");
        }
        DecontaminationProcess.owner(bootstrap, intent.causeSubjectId());
        InfectionCell cell = cell(intent); long expected = Math.max(0L, Math.subtractExact(observation.priorRaw(), DecontaminationPolicy.REDUCTION_RAW));
        FixedRatio actual = infection.get(cell);
        if (!observation.cell().equals(cell) || observation.remainingRaw() != expected || !intent.subjectIds().contains(observation.itemId())
                || expected == 0L != (actual == null) || actual != null && actual.value().raw() != expected) throw new IllegalArgumentException("decontamination observation is invalid");
    }

    static InfectionCell cell(PhysicalIntent intent) {
        long scale = FixedScalar.SCALE;
        if (intent.origin().x().raw() % scale != 0L || intent.origin().y().raw() != 0L || intent.origin().z().raw() % scale != 0L) {
            throw new IllegalArgumentException("decontamination origin must be an infection-cell origin");
        }
        BlockPosition position = new BlockPosition(Math.toIntExact(intent.origin().x().raw() / scale), 0, Math.toIntExact(intent.origin().z().raw() / scale));
        InfectionCell cell = InfectionCell.at(position);
        if (!cell.originAtY(0).equals(position)) throw new IllegalArgumentException("decontamination origin is not cell-aligned");
        return cell;
    }
}
